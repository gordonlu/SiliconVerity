#include <jni.h>
#include <vulkan/vulkan.h>
#include <android/performance_hint.h>
#include <ctime>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <cstdio>
#include <dlfcn.h>
// #include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <sys/syscall.h>
#include <unistd.h>
#include "fp32_spv.h"
#include "fp32_independent_spv.h"
#include "buffer_triad_spv.h"

// #define LOGD(...) __android_log_print(ANDROID_LOG_INFO, "SV_GPU", __VA_ARGS__)

namespace {

// ---- ADPF (API 36 notifyWorkloadIncrease, dlsym 运行时解析) ----
typedef int (*NotifyWorkloadIncreaseFn)(APerformanceHintSession*, bool, bool, const char*);
static NotifyWorkloadIncreaseFn g_notifyWorkloadIncrease = nullptr;
static APerformanceHintSession* g_hintSession = nullptr;

static void announceGpuLoad() {
    if (!g_notifyWorkloadIncrease) {
        void* lib = dlopen("libandroid.so", RTLD_NOW);
        if (!lib) return;
        g_notifyWorkloadIncrease = (NotifyWorkloadIncreaseFn)dlsym(lib, "APerformanceHint_notifyWorkloadIncrease");
        if (!g_notifyWorkloadIncrease) return;
    }
    if (!g_hintSession) {
        APerformanceHintManager* mgr = APerformanceHint_getManager();
        if (!mgr) return;
        static pid_t tid = (pid_t)syscall(SYS_gettid);
        g_hintSession = APerformanceHint_createSession(mgr, &tid, 1, 30000000LL);
    }
    if (g_hintSession) {
        g_notifyWorkloadIncrease(g_hintSession, false, true, "sv_gpu_benchmark");
    }
}

static uint64_t monotonic_nanos() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static uint64_t medianU64(std::vector<uint64_t> v) {
    if (v.empty()) return 0;
    std::sort(v.begin(), v.end());
    size_t n = v.size();
    return (n % 2 == 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
}

static double medianD(std::vector<double> v) {
    if (v.empty()) return 0.0;
    std::sort(v.begin(), v.end());
    size_t n = v.size();
    return (n % 2 == 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
}

static double robustCv(std::vector<double> v) {
    if (v.empty()) return 0.0;
    double med = medianD(v);
    if (med <= 0.0) return 0.0;
    std::vector<double> dev;
    for (auto x : v) dev.push_back(std::fabs(x - med));
    return medianD(dev) / med;
}

static uint64_t fnv1a64(const unsigned char* d, size_t n) {
    uint64_t h = 0xcbf29ce484222325ULL;
    for (size_t i = 0; i < n; i++) { h ^= d[i]; h *= 0x100000001b3ULL; }
    return h;
}

static std::string hex64(uint64_t v) {
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%016llx", (unsigned long long)v);
    return buf;
}

static uint32_t findMemoryType(VkPhysicalDevice phys, uint32_t typeBits, VkMemoryPropertyFlags flags) {
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(phys, &mp);
    for (uint32_t i = 0; i < mp.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & flags) == flags) return i;
    }
    return UINT32_MAX;
}

struct Buffer {
    VkBuffer buf = VK_NULL_HANDLE;
    VkDeviceMemory mem = VK_NULL_HANDLE;
    VkDeviceSize size = 0;
};

static bool createBuffer(VkDevice dev, VkPhysicalDevice phys, VkDeviceSize size,
                         VkBufferUsageFlags usage, Buffer& out) {
    VkBufferCreateInfo bci{};
    bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bci.size = size; bci.usage = usage; bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(dev, &bci, nullptr, &out.buf) != VK_SUCCESS) return false;
    VkMemoryRequirements mr;
    vkGetBufferMemoryRequirements(dev, out.buf, &mr);
    uint32_t mt = findMemoryType(phys, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (mt == UINT32_MAX) { vkDestroyBuffer(dev, out.buf, nullptr); out.buf = VK_NULL_HANDLE; return false; }
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size; mai.memoryTypeIndex = mt;
    if (vkAllocateMemory(dev, &mai, nullptr, &out.mem) != VK_SUCCESS) {
        vkDestroyBuffer(dev, out.buf, nullptr); out.buf = VK_NULL_HANDLE; return false;
    }
    vkBindBufferMemory(dev, out.buf, out.mem, 0);
    out.size = size;
    return true;
}

static void destroyBuffer(VkDevice dev, Buffer& b) {
    if (b.buf) vkDestroyBuffer(dev, b.buf, nullptr);
    if (b.mem) vkFreeMemory(dev, b.mem, nullptr);
    b.buf = VK_NULL_HANDLE; b.mem = VK_NULL_HANDLE;
}

struct RoundTimings {
    uint64_t commandRecordingNs = 0;
    uint64_t queueSubmitNs = 0;
    uint64_t gpuExecNs = 0;          // Vulkan timestamp (diagnostic only)
    uint64_t completionWaitNs = 0;   // 旧: submit 返回后等待时长 (弃用)
    uint64_t submitToFenceNs = 0;    // 正式计时: submit 前 -> fence 完成
};

struct Harness {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice phys = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    VkCommandPool cmdPool = VK_NULL_HANDLE;
    uint32_t qf = 0;
    VkPhysicalDeviceProperties props{};
    uint32_t tsValidBits = 0;
    float tsPeriod = 1.0f;
    bool ok = false;
    std::string err;

    bool init() {
        VkApplicationInfo ai{};
        ai.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        ai.apiVersion = VK_API_VERSION_1_1;
        ai.pApplicationName = "SiliconVerity";
        ai.pEngineName = "sv-gpu";
        ai.engineVersion = 1;
        VkInstanceCreateInfo ici{};
        ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ici.pApplicationInfo = &ai;

        // 调试: 若设备装有 VK_LAYER_KHRONOS_validation 则启用 (无则安全跳过)
        uint32_t layerCount = 0;
        vkEnumerateInstanceLayerProperties(&layerCount, nullptr);
        std::vector<VkLayerProperties> layers(layerCount);
        if (layerCount > 0) vkEnumerateInstanceLayerProperties(&layerCount, layers.data());
        bool hasValidation = false;
        for (auto& l : layers) {
            if (std::strcmp(l.layerName, "VK_LAYER_KHRONOS_validation") == 0) { hasValidation = true; break; }
        }
        const char* enableLayers[] = { "VK_LAYER_KHRONOS_validation" };
        if (hasValidation) {
            ici.enabledLayerCount = 1;
            ici.ppEnabledLayerNames = enableLayers;
        }

        VkResult r = vkCreateInstance(&ici, nullptr, &instance);
        if (r != VK_SUCCESS) { err = "vkCreateInstance=" + std::to_string(r); return false; }
        uint32_t pdc = 0;
        vkEnumeratePhysicalDevices(instance, &pdc, nullptr);
        if (pdc == 0) { err = "no physical devices"; return false; }
        std::vector<VkPhysicalDevice> pds(pdc);
        vkEnumeratePhysicalDevices(instance, &pdc, pds.data());
        phys = pds[0];
        vkGetPhysicalDeviceProperties(phys, &props);
        tsPeriod = props.limits.timestampPeriod;

        uint32_t qfc = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(phys, &qfc, nullptr);
        std::vector<VkQueueFamilyProperties> qfp(qfc);
        vkGetPhysicalDeviceQueueFamilyProperties(phys, &qfc, qfp.data());
        int found = -1;
        for (uint32_t i = 0; i < qfc; i++) {
            if (qfp[i].queueFlags & VK_QUEUE_COMPUTE_BIT) { found = (int)i; tsValidBits = qfp[i].timestampValidBits; break; }
        }
        if (found < 0) { err = "no compute queue"; return false; }
        qf = (uint32_t)found;
        float prio = 1.0f;
        VkDeviceQueueCreateInfo dqc{};
        dqc.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        dqc.queueFamilyIndex = qf; dqc.queueCount = 1; dqc.pQueuePriorities = &prio;
        VkDeviceCreateInfo dci{};
        dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        dci.queueCreateInfoCount = 1; dci.pQueueCreateInfos = &dqc;
        r = vkCreateDevice(phys, &dci, nullptr, &device);
        if (r != VK_SUCCESS) { err = "vkCreateDevice=" + std::to_string(r); return false; }
        vkGetDeviceQueue(device, qf, 0, &queue);

        VkCommandPoolCreateInfo cpci{};
        cpci.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        cpci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        cpci.queueFamilyIndex = qf;
        if (vkCreateCommandPool(device, &cpci, nullptr, &cmdPool) != VK_SUCCESS) { err = "cmdpool"; return false; }
        ok = true;
        return true;
    }

    ~Harness() {
        if (cmdPool) vkDestroyCommandPool(device, cmdPool, nullptr);
        if (device) vkDestroyDevice(device, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
    }

    bool gpuTimestampUsable() const { return tsValidBits > 0; }

    RoundTimings runRound(VkPipeline pipeline, VkPipelineLayout layout, VkDescriptorSet descSet,
                          const void* pcData, uint32_t pcSize, uint32_t groups,
                          VkQueryPool queryPool, VkFence fence, bool useGpuTs) {
        RoundTimings t;
        VkCommandBuffer cmd = VK_NULL_HANDLE;
        VkCommandBufferAllocateInfo ai{};
        ai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        ai.commandPool = cmdPool; ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandBufferCount = 1;
        if (vkAllocateCommandBuffers(device, &ai, &cmd) != VK_SUCCESS || cmd == VK_NULL_HANDLE) {
            return t;
        }
        VkCommandBufferBeginInfo bi{};
        bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

        uint64_t rec0 = monotonic_nanos();
        if (vkBeginCommandBuffer(cmd, &bi) == VK_SUCCESS) {
            vkCmdResetQueryPool(cmd, queryPool, 0, 2);
            if (useGpuTs) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, 0);
            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, 1, &descSet, 0, nullptr);
            if (pcSize > 0) vkCmdPushConstants(cmd, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pcSize, pcData);
            // K=1: 单次 dispatch, 无跨 dispatch 状态/同步问题
            vkCmdDispatch(cmd, groups, 1, 1);
            if (useGpuTs) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, 1);
            vkEndCommandBuffer(cmd);
        }
        t.commandRecordingNs = monotonic_nanos() - rec0;

        VkSubmitInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.commandBufferCount = 1; si.pCommandBuffers = &cmd;
        // 正式计时起点: submit 之前 (GPU 可能在 submit 内即开始执行)
        uint64_t wall0 = monotonic_nanos();
        VkResult sr = vkQueueSubmit(queue, 1, &si, fence);
        uint64_t sub0 = monotonic_nanos();
        t.queueSubmitNs = sub0 - wall0;

        uint64_t wait0 = monotonic_nanos();
        VkResult wr = VK_ERROR_UNKNOWN;
        if (sr == VK_SUCCESS) {
            wr = vkWaitForFences(device, 1, &fence, VK_TRUE, 5ULL * 1000000000ULL);
        }
        uint64_t wall1 = monotonic_nanos();
        vkResetFences(device, 1, &fence);
        if (sr == VK_SUCCESS && wr == VK_SUCCESS) {
            t.submitToFenceNs = wall1 - wall0;
        }
        t.completionWaitNs = (sr == VK_SUCCESS) ? wall1 - wait0 : 0;
        if (wr == VK_SUCCESS && useGpuTs) {
                uint64_t ts[2] = {0, 0};
                vkGetQueryPoolResults(device, queryPool, 0, 2, sizeof(ts), ts, sizeof(uint64_t),
                    VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
                if (ts[0] != 0 && ts[1] != 0) {
                    uint64_t diff = ts[1] - ts[0];
                    if (tsValidBits < 64) diff &= ((1ULL << tsValidBits) - 1);
                    t.gpuExecNs = (uint64_t)((double)diff * (double)tsPeriod);
                }
            }
        vkFreeCommandBuffers(device, cmdPool, 1, &cmd);
        return t;
    }

    bool buildPipeline(const uint32_t* spv, size_t spvLen, const VkDescriptorSetLayoutBinding* binds,
                       uint32_t bindCount, VkPushConstantRange pcRange,
                       VkShaderModule& sm, VkDescriptorSetLayout& dsl, VkPipelineLayout& pl,
                       VkDescriptorPool& pool, VkDescriptorSet& ds, VkPipeline& pipe) {
        VkShaderModuleCreateInfo smi{};
        smi.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        smi.codeSize = spvLen; smi.pCode = spv;
        if (vkCreateShaderModule(device, &smi, nullptr, &sm) != VK_SUCCESS) return false;
        VkDescriptorSetLayoutCreateInfo dli{};
        dli.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        dli.bindingCount = bindCount; dli.pBindings = binds;
        if (vkCreateDescriptorSetLayout(device, &dli, nullptr, &dsl) != VK_SUCCESS) return false;
        VkPipelineLayoutCreateInfo pli{};
        pli.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        pli.setLayoutCount = 1; pli.pSetLayouts = &dsl;
        if (pcRange.stageFlags != 0) { pli.pushConstantRangeCount = 1; pli.pPushConstantRanges = &pcRange; }
        if (vkCreatePipelineLayout(device, &pli, nullptr, &pl) != VK_SUCCESS) return false;
        VkDescriptorPoolSize dps{};
        dps.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER; dps.descriptorCount = bindCount;
        VkDescriptorPoolCreateInfo dpci{};
        dpci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        dpci.maxSets = 1; dpci.poolSizeCount = 1; dpci.pPoolSizes = &dps;
        if (vkCreateDescriptorPool(device, &dpci, nullptr, &pool) != VK_SUCCESS) return false;
        VkDescriptorSetAllocateInfo dai{};
        dai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dai.descriptorPool = pool; dai.descriptorSetCount = 1; dai.pSetLayouts = &dsl;
        if (vkAllocateDescriptorSets(device, &dai, &ds) != VK_SUCCESS) return false;
        VkComputePipelineCreateInfo cpi{};
        cpi.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
        cpi.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        cpi.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        cpi.stage.module = sm; cpi.stage.pName = "main";
        cpi.layout = pl;
        if (vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &cpi, nullptr, &pipe) != VK_SUCCESS) return false;
        return true;
    }

    void writeDescriptors(uint32_t bindCount, const Buffer* bufs, VkDescriptorSet ds) {
        std::vector<VkDescriptorBufferInfo> infos(bindCount);
        std::vector<VkWriteDescriptorSet> writes(bindCount);
        for (uint32_t i = 0; i < bindCount; i++) {
            infos[i].buffer = bufs[i].buf; infos[i].offset = 0; infos[i].range = VK_WHOLE_SIZE;
            writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[i].dstSet = ds; writes[i].dstBinding = i;
            writes[i].descriptorCount = 1; writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            writes[i].pBufferInfo = &infos[i];
        }
        vkUpdateDescriptorSets(device, bindCount, writes.data(), 0, nullptr);
    }
};

struct WorkResult {
    bool supported = false;
    std::string deviceName;
    std::string driverVersion;
    std::string vulkanVersion;
    double metricValue = 0;
    std::string metricUnit;
    uint64_t medianNs = 0;
    double cv = 0;
    bool checksumValid = false;
    std::string invalidReason;
    uint64_t commandRecordingNs = 0;
    uint64_t queueSubmitNs = 0;
    uint64_t gpuExecNs = 0;
    uint64_t completionWaitNs = 0;
    std::string spirvHash;
    std::string arithType;
    std::string arithContract;
    bool retestNeeded = false;
    std::string diag;
};

static std::string vkVersionStr(uint32_t v) {
    return std::to_string(VK_VERSION_MAJOR(v)) + "." + std::to_string(VK_VERSION_MINOR(v)) + "." + std::to_string(VK_VERSION_PATCH(v));
}

static void fillBase(WorkResult& r, Harness& h) {
    r.supported = true;
    r.deviceName = h.props.deviceName;
    r.driverVersion = vkVersionStr(h.props.driverVersion);
    r.vulkanVersion = vkVersionStr(h.props.apiVersion);
}

struct GpuResources {
    VkShaderModule sm = VK_NULL_HANDLE;
    VkDescriptorSetLayout dsl = VK_NULL_HANDLE;
    VkPipelineLayout pl = VK_NULL_HANDLE;
    VkDescriptorPool pool = VK_NULL_HANDLE;
    VkDescriptorSet ds = VK_NULL_HANDLE;
    VkPipeline pipe = VK_NULL_HANDLE;
    VkQueryPool queryPool = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    void destroy(VkDevice dev) {
        if (fence) vkDestroyFence(dev, fence, nullptr);
        if (queryPool) vkDestroyQueryPool(dev, queryPool, nullptr);
        if (pipe) vkDestroyPipeline(dev, pipe, nullptr);
        if (pool) vkDestroyDescriptorPool(dev, pool, nullptr);
        if (pl) vkDestroyPipelineLayout(dev, pl, nullptr);
        if (dsl) vkDestroyDescriptorSetLayout(dev, dsl, nullptr);
        if (sm) vkDestroyShaderModule(dev, sm, nullptr);
    }
};

// 扩展 push constant: iterations + 4 (factor,offset) + vec4Count (dependency 用 A, independent 用 ABCD)
struct Fp32PC {
    uint32_t iterations;
    float factorA, offsetA;
    float factorB, offsetB;
    float factorC, offsetC;
    float factorD, offsetD;
    uint32_t vec4Count;
};

static WorkResult runFp32(Harness& h, int targetMs, bool independent) {
    WorkResult r;
    fillBase(r, h);
    r.metricUnit = "GFLOPS";
    r.arithType = "FP32";
    r.arithContract = "DEVICE_DEFAULT";
    r.spirvHash = hex64(independent ? fnv1a64(fp32_independent_spv, fp32_independent_spv_len)
                                    : fnv1a64(fp32_spv, fp32_spv_len));

    const uint32_t WG = 64;
    const uint32_t vec4Count = 16384;
    const uint32_t groups = vec4Count / WG;
    const VkDeviceSize bufBytes = (VkDeviceSize)vec4Count * 4 * sizeof(float);
    const uint32_t fmaPerIter = independent ? 16 : 4;

    Buffer io{};
    if (!createBuffer(h.device, h.phys, bufBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, io)) {
        r.invalidReason = "buffer alloc"; return r;
    }
    void* mapped = nullptr;
    vkMapMemory(h.device, io.mem, 0, bufBytes, 0, &mapped);
    float* f = (float*)mapped;
    for (uint32_t i = 0; i < vec4Count * 4; i++) f[i] = (float)((i * 2654435761u) % 1009) / 1009.0f;
    vkUnmapMemory(h.device, io.mem);

    VkDescriptorSetLayoutBinding binds[1] = {};
    binds[0].binding = 0; binds[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    binds[0].descriptorCount = 1; binds[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    VkPushConstantRange pc{}; pc.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; pc.offset = 0; pc.size = sizeof(Fp32PC);

    Fp32PC pcData{};
    pcData.vec4Count = vec4Count;
    pcData.factorA = 1.0000001f; pcData.offsetA = 1e-7f;
    if (independent) {
        pcData.factorB = 1.0000003f; pcData.offsetB = 2e-7f;
        pcData.factorC = 1.0000005f; pcData.offsetC = 3e-7f;
        pcData.factorD = 1.0000007f; pcData.offsetD = 4e-7f;
    }

    GpuResources g;
    const uint32_t* spv = independent ? (const uint32_t*)fp32_independent_spv : (const uint32_t*)fp32_spv;
    size_t spvLen = independent ? fp32_independent_spv_len : fp32_spv_len;
    if (!h.buildPipeline(spv, spvLen, binds, 1, pc, g.sm, g.dsl, g.pl, g.pool, g.ds, g.pipe)) {
        r.invalidReason = "pipeline"; g.destroy(h.device); destroyBuffer(h.device, io); return r;
    }
    h.writeDescriptors(1, &io, g.ds);

    VkQueryPoolCreateInfo qpci{}; qpci.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qpci.queryType = VK_QUERY_TYPE_TIMESTAMP; qpci.queryCount = 2;
    if (vkCreateQueryPool(h.device, &qpci, nullptr, &g.queryPool) != VK_SUCCESS) {
        r.invalidReason = "queryPool"; g.destroy(h.device); destroyBuffer(h.device, io); return r;
    }
    VkFenceCreateInfo fci{}; fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    if (vkCreateFence(h.device, &fci, nullptr, &g.fence) != VK_SUCCESS) {
        r.invalidReason = "fence"; g.destroy(h.device); destroyBuffer(h.device, io); return r;
    }

    bool useGpuTs = h.gpuTimestampUsable();

    // GPU workload 宣告 (Android 16 ADPF: 提前告知 GPU 负载将显著增加)
    announceGpuLoad();

    // ==== GPU PRIME (最长 16 轮, 每轮动态校准保持 ~200ms 持续负载) ====
    // probe: 1024 测时, 放大到 ~200ms/轮
    pcData.iterations = 1024;
    RoundTimings probe = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
    uint64_t probeNs = probe.submitToFenceNs;
    uint32_t boostIters = 1024;
    if (probeNs > 0) {
        boostIters = (uint32_t)((double)1024.0 * 200000000.0 / (double)probeNs);
        if (boostIters < 1024) boostIters = 1024;
        if (boostIters > 100000000u) boostIters = 100000000u;
    }
    std::vector<uint64_t> primeNs;
    std::vector<uint64_t> primeWin;
    uint32_t primeIters = boostIters;
    for (int i = 0; i < 16; i++) {
        pcData.iterations = primeIters;
        RoundTimings t = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
        if (t.submitToFenceNs > 0) {
            primeNs.push_back(t.submitToFenceNs);
            primeWin.push_back(t.submitToFenceNs);
            if (primeWin.size() > 3) primeWin.erase(primeWin.begin());
            // 动态校准: 保持每轮 ~200ms 持续负载 (GPU 升频后轮长会缩短)
            primeIters = (uint32_t)((double)primeIters * 200000000.0 / (double)t.submitToFenceNs);
            if (primeIters < 1024) primeIters = 1024;
            if (primeIters > 100000000u) primeIters = 100000000u;
            if (i >= 5 && primeWin.size() == 3) {
                // 时间 CV < 5% -> GPU 平台稳定 (升频完成), 提前进入校准
                std::vector<double> dv;
                for (auto v : primeWin) dv.push_back((double)v);
                if (robustCv(dv) < 0.05) break;
            }
        }
    }
    boostIters = primeIters;
    uint64_t primeMedianNs = primeNs.empty() ? 0 : medianU64(primeNs);

    // ==== CALIBRATION (逐级, host submit-to-fence; 每级放大 clamp 1.5~8x) ====
    // L1: 1024 -> 目标 30ms (若 <20ms 才放大, 每级最多 x8)
    uint32_t iters = 1024;
    pcData.iterations = iters;
    RoundTimings calib0 = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
    uint64_t calibNs = calib0.submitToFenceNs;
    uint64_t calibItersL1 = iters;
    if (calibNs > 0 && calibNs < 20000000ull) {   // < 20ms 才放大
        double scale = 30000000.0 / (double)calibNs;   // 目标 30ms
        scale = std::clamp(scale, 1.5, 8.0);
        iters = (uint32_t)((double)iters * scale);
        if (iters > 100000000u) iters = 100000000u;
        pcData.iterations = iters;
        RoundTimings m = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
        if (m.submitToFenceNs > 0) calibNs = m.submitToFenceNs;
    }
    // L2: 3 轮 median -> ~targetMs (单轮校准会被 GPU 唤醒状态波动污染)
    if (calibNs > 0) {
        uint64_t target = (uint64_t)targetMs * 1000000ull;
        iters = (uint32_t)((double)iters * (double)target / (double)calibNs);
        if (iters < 1024) iters = 1024;
        if (iters > 100000000u) iters = 100000000u;
        pcData.iterations = iters;
        std::vector<uint64_t> l2ns;
        for (int i = 0; i < 3; i++) {
            RoundTimings m = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
            if (m.submitToFenceNs > 0) l2ns.push_back(m.submitToFenceNs);
        }
        if (!l2ns.empty()) {
            uint64_t l2med = medianU64(l2ns);
            iters = (uint32_t)((double)iters * (double)target / (double)l2med);
            if (iters < 1024) iters = 1024;
            if (iters > 100000000u) iters = 100000000u;
        }
    }
    // 验证轮: 确认单轮 ~300ms, 偏差 >50% 再缩放一次 (防过度放大/缩小)
    pcData.iterations = iters;
    RoundTimings verify = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
    if (verify.submitToFenceNs > 0) {
        uint64_t target = (uint64_t)targetMs * 1000000ull;
        if (verify.submitToFenceNs < target / 2 || verify.submitToFenceNs > target * 2) {
            iters = (uint32_t)((double)iters * (double)target / (double)verify.submitToFenceNs);
            if (iters < 1024) iters = 1024;
            if (iters > 100000000u) iters = 100000000u;
            pcData.iterations = iters;
        }
    }
    // LOGD("calib: L1Iters=%llu L1Ns=%llu finalIters=%u", (unsigned long long)calibItersL1, (unsigned long long)calibNs, iters);

    // ==== MEASURE (settle 1 轮 + 7 轮, transition/双峰检测自动重来) ====
    std::vector<uint64_t> gpuTimes, recTimes, subTimes, submitFenceTimes;
    std::vector<double> mGflops;
    bool transitionOrBimodal = false;

    for (int attempt = 0; attempt < 2; attempt++) {
        // settle
        h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
        gpuTimes.clear(); recTimes.clear(); subTimes.clear(); submitFenceTimes.clear(); mGflops.clear();

        for (int i = 0; i < 7; i++) {
            RoundTimings t = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
            gpuTimes.push_back(t.gpuExecNs);
            recTimes.push_back(t.commandRecordingNs);
            subTimes.push_back(t.queueSubmitNs);
            submitFenceTimes.push_back(t.submitToFenceNs);
            if (t.submitToFenceNs > 0) {
                mGflops.push_back((double)vec4Count * iters * fmaPerIter * 2.0 / (double)t.submitToFenceNs);
            }
        }

        // 迟到升频: 样本显著高于 prime 中位数换算的基线 -> P-state transition
        transitionOrBimodal = false;
        double primeBaselineGflops = (primeMedianNs > 0 && boostIters > 0)
            ? (double)vec4Count * boostIters * fmaPerIter * 2.0 / (double)primeMedianNs : 0.0;
        if (primeBaselineGflops > 0.0 && mGflops.size() >= 7) {
            for (auto gf : mGflops) {
                if (gf > primeBaselineGflops * 1.08) { transitionOrBimodal = true; break; }
            }
        }
        // 双峰: 低 3 均值与高 3 均值差 > 12% (如 4.5/4.5/2.3/2.3/4.5/2.3/4.5)
        if (!transitionOrBimodal && mGflops.size() >= 6) {
            std::vector<double> sorted = mGflops;
            std::sort(sorted.begin(), sorted.end());
            double lo = (sorted[0] + sorted[1] + sorted[2]) / 3.0;
            double hi = (sorted[sorted.size() - 3] + sorted[sorted.size() - 2] + sorted[sorted.size() - 1]) / 3.0;
            double med = medianD(mGflops);
            if (med > 0.0 && (hi - lo) / med > 0.12) transitionOrBimodal = true;
        }

        if (!transitionOrBimodal) break;
        // 重新 prime -> 重新校准 -> measure (最多 1 次重来)
        if (attempt == 0) {
            primeNs.clear();
            primeWin.clear();
            for (int i = 0; i < 16; i++) {
                pcData.iterations = boostIters;
                RoundTimings t = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
                if (t.submitToFenceNs > 0) {
                    primeNs.push_back(t.submitToFenceNs);
                    primeWin.push_back(t.submitToFenceNs);
                    if (primeWin.size() > 3) primeWin.erase(primeWin.begin());
                    if (i >= 5 && primeWin.size() == 3) {
                        std::vector<double> dv;
                        for (auto v : primeWin) dv.push_back((double)v);
                        if (robustCv(dv) < 0.05) break;
                    }
                }
            }
            primeMedianNs = primeNs.empty() ? 0 : medianU64(primeNs);
            // 逐级校准 (L1 + L2 + 验证轮)
            iters = 1024;
            pcData.iterations = iters;
            RoundTimings c0 = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
            calibNs = c0.submitToFenceNs;
            if (calibNs > 0 && calibNs < 20000000ull) {
                double scale = 30000000.0 / (double)calibNs;
                scale = std::clamp(scale, 1.5, 8.0);
                iters = (uint32_t)((double)iters * scale);
                if (iters > 100000000u) iters = 100000000u;
                pcData.iterations = iters;
                RoundTimings m = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
                if (m.submitToFenceNs > 0) calibNs = m.submitToFenceNs;
            }
            if (calibNs > 0) {
                uint64_t target = (uint64_t)targetMs * 1000000ull;
                iters = (uint32_t)((double)iters * (double)target / (double)calibNs);
                if (iters < 1024) iters = 1024;
                if (iters > 100000000u) iters = 100000000u;
                pcData.iterations = iters;
                std::vector<uint64_t> l2ns;
                for (int i = 0; i < 3; i++) {
                    RoundTimings m = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
                    if (m.submitToFenceNs > 0) l2ns.push_back(m.submitToFenceNs);
                }
                if (!l2ns.empty()) {
                    uint64_t l2med = medianU64(l2ns);
                    iters = (uint32_t)((double)iters * (double)target / (double)l2med);
                    if (iters < 1024) iters = 1024;
                    if (iters > 100000000u) iters = 100000000u;
                }
            }
            pcData.iterations = iters;
            RoundTimings verify = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
            if (verify.submitToFenceNs > 0) {
                uint64_t target = (uint64_t)targetMs * 1000000ull;
                if (verify.submitToFenceNs < target / 2 || verify.submitToFenceNs > target * 2) {
                    iters = (uint32_t)((double)iters * (double)target / (double)verify.submitToFenceNs);
                    if (iters < 1024) iters = 1024;
                    if (iters > 100000000u) iters = 100000000u;
                    pcData.iterations = iters;
                }
            }
        }
    }
    if (transitionOrBimodal) r.retestNeeded = true;
    uint64_t measureMedianNs = submitFenceTimes.empty() ? 0 : medianU64(submitFenceTimes);
    r.diag = "probeIters=1024 probeNs=" + std::to_string(probeNs) +
             " primeIters=" + std::to_string(boostIters) + " primeRounds=8 primeMedianNs=" + std::to_string(primeMedianNs) +
             " calibIters=" + std::to_string(calibItersL1) + " calibNs=" + std::to_string(calibNs) +
             " finalIters=" + std::to_string(iters) + " targetNs=" + std::to_string((uint64_t)targetMs * 1000000ull) +
             " measureMedianNs=" + std::to_string(measureMedianNs);

    // 正式成绩 = host submit-to-fence 中位数; gpuExecNs 保留 timestamp 作诊断
    r.medianNs = medianU64(submitFenceTimes);
    r.commandRecordingNs = medianU64(recTimes);
    r.queueSubmitNs = medianU64(subTimes);
    r.gpuExecNs = medianU64(gpuTimes);
    r.completionWaitNs = medianU64(submitFenceTimes);

    {
        std::vector<double> d;
        for (auto v : submitFenceTimes) d.push_back((double)v);
        double med = medianD(d);
        std::vector<double> dev;
        for (auto v : d) dev.push_back(std::fabs(v - med));
        double m = medianD(dev);
        r.cv = (med > 0) ? m / med : 0.0;
    }

    if (!useGpuTs) {
        r.invalidReason = "GPU timestamp unsupported";
    } else {
        // metric 用 host 墙钟 (median wait 时长), 不依赖 GPU timestamp
        double flop = (double)vec4Count * iters * fmaPerIter * 2.0;
        if (r.medianNs > 0) r.metricValue = flop / (double)r.medianNs;
    }

    vkMapMemory(h.device, io.mem, 0, bufBytes, 0, &mapped);
    f = (float*)mapped;
    double sum = 0; bool finite = true; bool changed = false;
    for (uint32_t i = 0; i < vec4Count * 4; i++) {
        float v = f[i];
        if (!std::isfinite(v)) { finite = false; break; }
        sum += v;
        if (i < 4 && v != ((float)((i * 2654435761u) % 1009) / 1009.0f)) changed = true;
    }
    vkUnmapMemory(h.device, io.mem);
    r.checksumValid = finite && std::isfinite(sum) && sum != 0.0 && changed;
    if (!r.checksumValid && r.invalidReason.empty()) r.invalidReason = "checksum";

    g.destroy(h.device);
    destroyBuffer(h.device, io);
    return r;
}

static WorkResult runTriad(Harness& h, int targetMs) {
    (void)targetMs;
    WorkResult r;
    fillBase(r, h);
    r.metricUnit = "GB/s";
    r.spirvHash = hex64(fnv1a64(buffer_triad_spv, buffer_triad_spv_len));

    const uint32_t WG = 64;
    const uint32_t count = 16 * 1024 * 1024;
    const uint32_t groups = count / WG;
    const VkDeviceSize bufBytes = (VkDeviceSize)count * sizeof(float);
    const float scalar = 1.5f;

    Buffer A{}, B{}, O{};
    if (!createBuffer(h.device, h.phys, bufBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, A) ||
        !createBuffer(h.device, h.phys, bufBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, B) ||
        !createBuffer(h.device, h.phys, bufBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, O)) {
        r.invalidReason = "buffer alloc";
        destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
        return r;
    }
    void* m;
    vkMapMemory(h.device, A.mem, 0, bufBytes, 0, &m); float* fa = (float*)m;
    vkMapMemory(h.device, B.mem, 0, bufBytes, 0, &m); float* fb = (float*)m;
    for (uint32_t i = 0; i < count; i++) { fa[i] = (float)((i * 7) % 1009) / 1009.0f; fb[i] = (float)((i * 13) % 1009) / 1009.0f; }
    vkUnmapMemory(h.device, A.mem); vkUnmapMemory(h.device, B.mem);

    VkDescriptorSetLayoutBinding binds[3] = {};
    for (uint32_t i = 0; i < 3; i++) { binds[i].binding = i; binds[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER; binds[i].descriptorCount = 1; binds[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; }
    VkPushConstantRange pc{}; pc.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; pc.offset = 0; pc.size = 8;
    struct PC { float scalar; uint32_t count; };
    PC pcData{scalar, count};

    GpuResources g;
    if (!h.buildPipeline((const uint32_t*)buffer_triad_spv, buffer_triad_spv_len, binds, 3, pc, g.sm, g.dsl, g.pl, g.pool, g.ds, g.pipe)) {
        r.invalidReason = "pipeline";
        destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
        return r;
    }
    Buffer bufs[3] = {A, B, O};
    h.writeDescriptors(3, bufs, g.ds);

    VkQueryPoolCreateInfo qpci{}; qpci.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qpci.queryType = VK_QUERY_TYPE_TIMESTAMP; qpci.queryCount = 2;
    if (vkCreateQueryPool(h.device, &qpci, nullptr, &g.queryPool) != VK_SUCCESS) {
        r.invalidReason = "queryPool"; g.destroy(h.device);
        destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
        return r;
    }
    VkFenceCreateInfo fci{}; fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    if (vkCreateFence(h.device, &fci, nullptr, &g.fence) != VK_SUCCESS) {
        r.invalidReason = "fence"; g.destroy(h.device);
        destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
        return r;
    }

    bool useGpuTs = h.gpuTimestampUsable();

    std::vector<uint64_t> gpuTimes, recTimes, subTimes, waitTimes;
    for (int i = 0; i < 3; i++) h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
    for (int i = 0; i < 7; i++) {
        RoundTimings t = h.runRound(g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups, g.queryPool, g.fence, useGpuTs);
        gpuTimes.push_back(t.gpuExecNs);
        recTimes.push_back(t.commandRecordingNs);
        subTimes.push_back(t.queueSubmitNs);
        waitTimes.push_back(t.completionWaitNs);
    }
    r.medianNs = medianU64(gpuTimes);
    r.commandRecordingNs = medianU64(recTimes);
    r.queueSubmitNs = medianU64(subTimes);
    r.gpuExecNs = r.medianNs;
    r.completionWaitNs = medianU64(waitTimes);
    {
        std::vector<double> d;
        for (auto v : gpuTimes) d.push_back((double)v);
        double med = medianD(d);
        std::vector<double> dev;
        for (auto v : d) dev.push_back(std::fabs(v - med));
        double mm = medianD(dev);
        r.cv = (med > 0) ? mm / med : 0.0;
    }

    if (!useGpuTs) {
        r.invalidReason = "GPU timestamp unsupported";
    } else {
        double bytes = (double)count * 4.0 * 3.0; // K=1, single dispatch
        if (r.medianNs > 0) r.metricValue = bytes / (double)r.medianNs;
    }

    vkMapMemory(h.device, A.mem, 0, bufBytes, 0, &m); fa = (float*)m;
    vkMapMemory(h.device, B.mem, 0, bufBytes, 0, &m); fb = (float*)m;
    vkMapMemory(h.device, O.mem, 0, bufBytes, 0, &m); float* fo = (float*)m;
    bool ok = true;
    for (uint32_t i = 0; i < count; i++) {
        float actual = fo[i];
        if (!std::isfinite(actual)) { ok = false; break; }
        float expected = fa[i] + scalar * fb[i];
        if (std::fabs(actual - expected) > 1e-4f) { ok = false; break; }
    }
    vkUnmapMemory(h.device, A.mem); vkUnmapMemory(h.device, B.mem); vkUnmapMemory(h.device, O.mem);
    r.checksumValid = ok;
    if (!r.checksumValid && r.invalidReason.empty()) r.invalidReason = "checksum";

    g.destroy(h.device);
    destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
    return r;
}

static Harness& harness() {
    static Harness h;
    static bool tried = false;
    if (!tried) { tried = true; h.init(); }
    return h;
}

static std::string u64s(uint64_t v) { return std::to_string(v); }

static std::string resultToStr(const WorkResult& r) {
    std::string s;
    s += "supported="; s += (r.supported ? "1" : "0"); s += ";";
    s += "deviceName="; s += r.deviceName; s += ";";
    s += "driverVersion="; s += r.driverVersion; s += ";";
    s += "vulkanVersion="; s += r.vulkanVersion; s += ";";
    s += "metricValue="; s += std::to_string(r.metricValue); s += ";";
    s += "metricUnit="; s += r.metricUnit; s += ";";
    s += "medianNs="; s += u64s(r.medianNs); s += ";";
    s += "cv="; s += std::to_string(r.cv); s += ";";
    s += "checksumValid="; s += (r.checksumValid ? "1" : "0"); s += ";";
    s += "commandRecordingNs="; s += u64s(r.commandRecordingNs); s += ";";
    s += "queueSubmitNs="; s += u64s(r.queueSubmitNs); s += ";";
    s += "gpuExecNs="; s += u64s(r.gpuExecNs); s += ";";
    s += "completionWaitNs="; s += u64s(r.completionWaitNs); s += ";";
    s += "spirvHash="; s += r.spirvHash; s += ";";
    s += "arithType="; s += r.arithType; s += ";";
    s += "arithContract="; s += r.arithContract; s += ";";
    s += "retest="; s += (r.retestNeeded ? "1" : "0"); s += ";";
    s += "diag="; s += r.diag; s += ";";
    s += "invalidReason="; s += r.invalidReason;
    return s;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativegpu_VulkanBench_nativeRunVulkanBenchmark(JNIEnv* env, jclass, jint workload, jint targetDurationMs) {
    Harness& h = harness();
    WorkResult r;
    if (!h.ok) {
        r.supported = false;
        r.invalidReason = h.err;
    } else if (workload == 2) {
        r = runTriad(h, targetDurationMs);
    } else {
        r = runFp32(h, targetDurationMs, workload == 0);
    }
    return env->NewStringUTF(resultToStr(r).c_str());
}
