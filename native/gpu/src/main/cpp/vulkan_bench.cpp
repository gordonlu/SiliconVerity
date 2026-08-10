#include <jni.h>
#include <vulkan/vulkan.h>
#include <android/performance_hint.h>
#include <ctime>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <cstdio>
#include <dlfcn.h>
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

static uint64_t monotonic_nanos();

// ---- ADPF (API 36 notifyWorkloadIncrease, dlsym 运行时解析) ----
typedef int (*NotifyWorkloadIncreaseFn)(APerformanceHintSession*, bool, bool, const char*);
typedef AWorkDuration* (*WorkDurationCreateFn)();
typedef void (*WorkDurationReleaseFn)(AWorkDuration*);
typedef void (*WorkDurationSetFn)(AWorkDuration*, int64_t);
typedef int (*ReportWorkDuration2Fn)(APerformanceHintSession*, AWorkDuration*);

/**
 * Hint session 必须与当前 native benchmark 线程同寿命。Kotlin 使用
 * Dispatchers.Default，全局复用 session 会使后续测试绑到已经不执行的 TID。
 */
class PerformanceHintScope {
public:
    explicit PerformanceHintScope(int64_t targetNs) {
        APerformanceHintManager* mgr = APerformanceHint_getManager();
        if (!mgr) return;
        int32_t tid = (int32_t)syscall(SYS_gettid);
        session_ = APerformanceHint_createSession(mgr, &tid, 1, targetNs);
        if (!session_) return;

        libandroid_ = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
        if (libandroid_) {
            notifyIncrease_ = reinterpret_cast<NotifyWorkloadIncreaseFn>(
                dlsym(libandroid_, "APerformanceHint_notifyWorkloadIncrease"));
            workDurationCreate_ = reinterpret_cast<WorkDurationCreateFn>(
                dlsym(libandroid_, "AWorkDuration_create"));
            workDurationRelease_ = reinterpret_cast<WorkDurationReleaseFn>(
                dlsym(libandroid_, "AWorkDuration_release"));
            setWorkStart_ = reinterpret_cast<WorkDurationSetFn>(
                dlsym(libandroid_, "AWorkDuration_setWorkPeriodStartTimestampNanos"));
            setActualTotal_ = reinterpret_cast<WorkDurationSetFn>(
                dlsym(libandroid_, "AWorkDuration_setActualTotalDurationNanos"));
            setActualCpu_ = reinterpret_cast<WorkDurationSetFn>(
                dlsym(libandroid_, "AWorkDuration_setActualCpuDurationNanos"));
            setActualGpu_ = reinterpret_cast<WorkDurationSetFn>(
                dlsym(libandroid_, "AWorkDuration_setActualGpuDurationNanos"));
            reportDuration2_ = reinterpret_cast<ReportWorkDuration2Fn>(
                dlsym(libandroid_, "APerformanceHint_reportActualWorkDuration2"));
        }
    }

    ~PerformanceHintScope() {
        if (session_) APerformanceHint_closeSession(session_);
        if (libandroid_) dlclose(libandroid_);
    }

    PerformanceHintScope(const PerformanceHintScope&) = delete;
    PerformanceHintScope& operator=(const PerformanceHintScope&) = delete;

    void announceIncrease() {
        if (session_ && notifyIncrease_) {
            notifyResult_ = notifyIncrease_(session_, false, true, "sv_gpu_compute_prime");
        }
    }

    void updateTarget(int64_t targetNs) {
        if (session_ && targetNs > 0) {
            updateResult_ = APerformanceHint_updateTargetWorkDuration(session_, targetNs);
        }
    }

    void report(uint64_t actualNs, uint64_t gpuNs = 0, uint64_t cpuNs = 0) {
        if (!session_ || actualNs == 0) return;
        int rc;
        if (workDurationCreate_ && workDurationRelease_ && setWorkStart_ &&
            setActualTotal_ && setActualCpu_ && setActualGpu_ && reportDuration2_) {
            AWorkDuration* work = workDurationCreate_();
            const uint64_t safeGpuNs = std::min(gpuNs > 0 ? gpuNs : actualNs, actualNs);
            const uint64_t safeCpuNs = std::min(cpuNs, actualNs);
            const uint64_t nowNs = monotonic_nanos();
            setWorkStart_(work, (int64_t)(nowNs > actualNs ? nowNs - actualNs : 1));
            setActualTotal_(work, (int64_t)actualNs);
            setActualCpu_(work, (int64_t)safeCpuNs);
            setActualGpu_(work, (int64_t)safeGpuNs);
            rc = reportDuration2_(session_, work);
            workDurationRelease_(work);
            if (rc == 0) report2Count_++;
        } else {
            rc = APerformanceHint_reportActualWorkDuration(session_, (int64_t)actualNs);
        }
        if (rc == 0) reportCount_++;
        else reportError_ = rc;
    }

    bool active() const { return session_ != nullptr; }
    int notifyResult() const { return notifyResult_; }
    int updateResult() const { return updateResult_; }
    int reportCount() const { return reportCount_; }
    int report2Count() const { return report2Count_; }
    int reportError() const { return reportError_; }

private:
    void* libandroid_ = nullptr;
    NotifyWorkloadIncreaseFn notifyIncrease_ = nullptr;
    WorkDurationCreateFn workDurationCreate_ = nullptr;
    WorkDurationReleaseFn workDurationRelease_ = nullptr;
    WorkDurationSetFn setWorkStart_ = nullptr;
    WorkDurationSetFn setActualTotal_ = nullptr;
    WorkDurationSetFn setActualCpu_ = nullptr;
    WorkDurationSetFn setActualGpu_ = nullptr;
    ReportWorkDuration2Fn reportDuration2_ = nullptr;
    APerformanceHintSession* session_ = nullptr;
    int notifyResult_ = -1;
    int updateResult_ = -1;
    int reportCount_ = 0;
    int report2Count_ = 0;
    int reportError_ = 0;
};

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
    VkResult submitResult = VK_SUCCESS;
    VkResult waitResult = VK_SUCCESS;
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
    // fence 超时后 command buffer 仍可能被驱动使用。此时不能 reset/free/destroy
    // 任何关联 Vulkan 对象；让进程回收它们比触发未定义行为更安全。
    bool abandoned = false;
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
            if (qfp[i].queueFlags & VK_QUEUE_COMPUTE_BIT) { found = (int)i; break; }
        }
        if (found < 0) { err = "no compute queue"; return false; }
        qf = (uint32_t)found;
        tsValidBits = qfp[qf].timestampValidBits;
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
        if (abandoned) return;
        if (cmdPool) vkDestroyCommandPool(device, cmdPool, nullptr);
        if (device) vkDestroyDevice(device, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
    }

    bool gpuTimestampUsable() const { return tsValidBits > 0; }

    RoundTimings runRound(VkPipeline pipeline, VkPipelineLayout layout, VkDescriptorSet descSet,
                          const void* pcData, uint32_t pcSize, uint32_t groups,
                          VkQueryPool queryPool, VkFence fence, bool useGpuTs,
                          uint32_t dispatchRepeats = 1,
                          uint64_t fenceTimeoutNs = 2000000000ULL) {
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
            // 多个中等 dispatch 连续填满 GPU。同一 storage buffer 跨 dispatch 读写，
            // 显式 barrier 避免 RAW/WAW hazard；时间戳包围整个 compute quantum。
            for (uint32_t repeat = 0; repeat < dispatchRepeats; repeat++) {
                vkCmdDispatch(cmd, groups, 1, 1);
                if (repeat + 1 < dispatchRepeats) {
                    VkMemoryBarrier barrier{};
                    barrier.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
                    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
                    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
                    vkCmdPipelineBarrier(
                        cmd,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        0,
                        1, &barrier,
                        0, nullptr,
                        0, nullptr);
                }
            }
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
        t.submitResult = sr;
        uint64_t sub0 = monotonic_nanos();
        t.queueSubmitNs = sub0 - wall0;

        uint64_t wait0 = monotonic_nanos();
        VkResult wr = VK_ERROR_UNKNOWN;
        if (sr == VK_SUCCESS) {
            wr = vkWaitForFences(device, 1, &fence, VK_TRUE, fenceTimeoutNs);
        }
        t.waitResult = wr;
        uint64_t wall1 = monotonic_nanos();
        if (sr == VK_SUCCESS && wr == VK_SUCCESS) {
            t.submitToFenceNs = wall1 - wall0;
            vkResetFences(device, 1, &fence);
        } else if (sr == VK_SUCCESS) {
            // VK_TIMEOUT 并不取消已提交的 GPU 工作。reset fence、free command
            // buffer 或销毁其资源都会违反 Vulkan 生命周期规则。
            abandoned = true;
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
        if (!abandoned) vkFreeCommandBuffers(device, cmdPool, 1, &cmd);
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
    std::vector<uint64_t> sampleNs;
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
    // 4 MiB working set / 4096 workgroups: 较旧版 256 workgroups 更容易填满高端 GPU。
    const uint32_t vec4Count = 262144;
    const uint32_t groups = vec4Count / WG;
    const VkDeviceSize bufBytes = (VkDeviceSize)vec4Count * 4 * sizeof(float);
    const uint32_t fmaPerIter = independent ? 16 : 4;
    // 真机验证表明 4/8 dispatch 的 ~20ms batch 仍会在 submit 空隙掉回低档；
    // 64 dispatch 组成约 200ms 连续 queue-busy 区间，不需要图形 Surface 也能施加稳定压力。
    const uint32_t primeDispatches = 64;
    const uint32_t probeIters = 128;
    const uint32_t minIters = 16;
    const uint32_t maxIters = 10000000;
    const uint64_t primeQuantumTargetNs = 200000000ull;
    const uint64_t primeDurationTargetNs = 3000000000ull;

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

    auto abortPendingGpuWork = [&](const char* phase) {
        r.invalidReason = std::string("gpu fence timeout during compute ") + phase;
        r.retestNeeded = true;
        r.checksumValid = false;
        return r;
    };

    bool useGpuTs = h.gpuTimestampUsable();

    // ==== PROBE: 仅此一次根据当前速度缩放工作量 ====
    pcData.iterations = probeIters;
    RoundTimings probe = h.runRound(
        g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
        g.queryPool, g.fence, useGpuTs, primeDispatches);
    if (h.abandoned) return abortPendingGpuWork("probe");
    uint64_t probeNs = probe.submitToFenceNs;
    uint32_t primeIters = probeIters;
    if (probeNs > 0) {
        double scale = (double)primeQuantumTargetNs / (double)probeNs;
        scale = std::clamp(scale, 0.10, 64.0);
        primeIters = (uint32_t)std::llround((double)probeIters * scale);
        primeIters = std::clamp(primeIters, minIters, maxIters);
    }
    pcData.iterations = primeIters;

    // ==== PRIME: 固定工作量、连续 compute batch、持续 3s ====
    // 调频后周期应自然缩短；不再动态改 iterations 把 P-state 变化隐藏掉。
    PerformanceHintScope hint((int64_t)(primeQuantumTargetNs / 2));
    hint.announceIncrease();
    hint.updateTarget((int64_t)(primeQuantumTargetNs / 2));

    std::vector<uint64_t> primeNs;
    std::vector<double> primeGflops;
    const double primeFlop = (double)vec4Count * primeIters * fmaPerIter * 2.0 * primeDispatches;
    uint64_t primeStartNs = monotonic_nanos();
    uint32_t primeAttempts = 0;
    while ((monotonic_nanos() - primeStartNs < primeDurationTargetNs || primeNs.size() < 15) &&
           primeAttempts++ < 80) {
        RoundTimings t = h.runRound(
            g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
            g.queryPool, g.fence, useGpuTs, primeDispatches);
        if (h.abandoned) return abortPendingGpuWork("prime");
        if (t.submitToFenceNs == 0) continue;
        primeNs.push_back(t.submitToFenceNs);
        primeGflops.push_back(primeFlop / (double)t.submitToFenceNs);
        hint.report(t.submitToFenceNs,
                    t.gpuExecNs > 0 ? t.gpuExecNs : t.completionWaitNs,
                    t.commandRecordingNs + t.queueSubmitNs);
    }
    auto sliceMedian = [](const std::vector<double>& values, size_t begin, size_t end) {
        if (begin >= end || end > values.size()) return 0.0;
        return medianD(std::vector<double>(values.begin() + begin, values.begin() + end));
    };
    const size_t platformWindow = std::min<size_t>(20, primeGflops.size());
    double primeFirstGflops = platformWindow > 0
        ? sliceMedian(primeGflops, 0, platformWindow) : 0.0;
    double primeFinalGflops = platformWindow > 0
        ? sliceMedian(primeGflops, primeGflops.size() - platformWindow, primeGflops.size()) : 0.0;
    uint64_t primeFinalNs = platformWindow > 0
        ? medianU64(std::vector<uint64_t>(primeNs.end() - platformWindow, primeNs.end())) : 0;
    double primePeakGflops = 0.0;
    for (size_t begin = 0; begin < primeGflops.size(); begin += 10) {
        size_t end = std::min(begin + 10, primeGflops.size());
        if (end - begin >= 5) {
            primePeakGflops = std::max(primePeakGflops, sliceMedian(primeGflops, begin, end));
        }
    }
    std::vector<double> primeTail;
    if (platformWindow > 0) {
        primeTail.assign(primeGflops.end() - platformWindow, primeGflops.end());
    }
    double primeTailCv = robustCv(primeTail);

    // ==== MEASURE: 保持同一 iterations，只增加 dispatch 数组成 ~targetMs 正式轮 ====
    uint64_t targetNs = (uint64_t)std::max(targetMs, 50) * 1000000ull;
    uint32_t measureDispatches = primeDispatches;
    if (primeFinalNs > 0) {
        double scaled = (double)primeDispatches * (double)targetNs / (double)primeFinalNs;
        measureDispatches = (uint32_t)std::llround(scaled);
        measureDispatches = std::clamp(measureDispatches, primeDispatches, 256u);
    }
    const double measureFlop = (double)vec4Count * primeIters * fmaPerIter * 2.0 * measureDispatches;

    // settle 一轮，保持 queue 连续繁忙。
    RoundTimings settle = h.runRound(
        g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
        g.queryPool, g.fence, useGpuTs, measureDispatches);
    if (h.abandoned) return abortPendingGpuWork("settle");
    hint.report(settle.submitToFenceNs,
                settle.gpuExecNs > 0 ? settle.gpuExecNs : settle.completionWaitNs,
                settle.commandRecordingNs + settle.queueSubmitNs);

    std::vector<uint64_t> gpuTimes, recTimes, subTimes, waitTimes, submitFenceTimes;
    std::vector<double> mGflops;
    // BenchmarkEngine 会消费 1 个 warmup，正式阶段最多请求 11 个样本。
    for (int i = 0; i < 12; i++) {
        RoundTimings t = h.runRound(
            g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
            g.queryPool, g.fence, useGpuTs, measureDispatches);
        if (h.abandoned) return abortPendingGpuWork("measure");
        if (t.submitToFenceNs == 0) continue;
        gpuTimes.push_back(t.gpuExecNs);
        recTimes.push_back(t.commandRecordingNs);
        subTimes.push_back(t.queueSubmitNs);
        waitTimes.push_back(t.completionWaitNs);
        submitFenceTimes.push_back(t.submitToFenceNs);
        mGflops.push_back(measureFlop / (double)t.submitToFenceNs);
        hint.report(t.submitToFenceNs,
                    t.gpuExecNs > 0 ? t.gpuExecNs : t.completionWaitNs,
                    t.commandRecordingNs + t.queueSubmitNs);
    }

    // 高低平台混合、测量期才升频、或 prime 末端已掉频，均不输出正式分。
    bool transitionOrBimodal = false;
    if (primeTailCv > 0.05) transitionOrBimodal = true;
    if (primePeakGflops > 0.0 && primeFinalGflops < primePeakGflops * 0.90) {
        transitionOrBimodal = true;
    }
    if (primeFinalGflops > 0.0) {
        for (double gf : mGflops) {
            if (gf > primeFinalGflops * 1.08 || gf < primeFinalGflops * 0.88) {
                transitionOrBimodal = true;
                break;
            }
        }
    }
    if (mGflops.size() >= 6) {
        std::vector<double> sorted = mGflops;
        std::sort(sorted.begin(), sorted.end());
        double lo = (sorted[0] + sorted[1] + sorted[2]) / 3.0;
        double hi = (sorted[sorted.size() - 3] + sorted[sorted.size() - 2] + sorted[sorted.size() - 1]) / 3.0;
        double med = medianD(mGflops);
        if (med > 0.0 && (hi - lo) / med > 0.12) transitionOrBimodal = true;
    }
    if (submitFenceTimes.size() < 12) transitionOrBimodal = true;
    r.retestNeeded = transitionOrBimodal;

    uint64_t measureMedianNs = medianU64(submitFenceTimes);
    r.diag = "groups=" + std::to_string(groups) +
             " primeDispatches=" + std::to_string(primeDispatches) +
             " probeNs=" + std::to_string(probeNs) +
             " primeIters=" + std::to_string(primeIters) +
             " primeRounds=" + std::to_string(primeNs.size()) +
             " primeFirst=" + std::to_string(primeFirstGflops) +
             " primeFinal=" + std::to_string(primeFinalGflops) +
             " primePeak=" + std::to_string(primePeakGflops) +
             " primeTailCv=" + std::to_string(primeTailCv) +
             " measureDispatches=" + std::to_string(measureDispatches) +
             " measureMedianNs=" + std::to_string(measureMedianNs) +
             " adpf=" + std::to_string(hint.active() ? 1 : 0) +
             "," + std::to_string(hint.notifyResult()) +
             "," + std::to_string(hint.updateResult()) +
             "," + std::to_string(hint.reportCount()) +
             "," + std::to_string(hint.report2Count()) +
             "," + std::to_string(hint.reportError());

    // 正式成绩 = host submit-to-fence 中位数; gpuExecNs 保留 timestamp 作诊断
    r.medianNs = medianU64(submitFenceTimes);
    r.commandRecordingNs = medianU64(recTimes);
    r.queueSubmitNs = medianU64(subTimes);
    r.gpuExecNs = medianU64(gpuTimes);
    r.completionWaitNs = medianU64(waitTimes);
    r.sampleNs = submitFenceTimes;

    {
        std::vector<double> d;
        for (auto v : submitFenceTimes) d.push_back((double)v);
        double med = medianD(d);
        std::vector<double> dev;
        for (auto v : d) dev.push_back(std::fabs(v - med));
        double m = medianD(dev);
        r.cv = (med > 0) ? m / med : 0.0;
    }

    // 正式 metric 只依赖 host submit-to-fence 墙钟。GPU timestamp 缺失时仅少一项
    // 诊断数据，不得让本来可测的 Compute workload 失效。
    if (r.medianNs > 0) r.metricValue = measureFlop / (double)r.medianNs;

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
    WorkResult r;
    fillBase(r, h);
    r.metricUnit = "GB/s";
    r.spirvHash = hex64(fnv1a64(buffer_triad_spv, buffer_triad_spv_len));

    // 包含分配、预热和正式采样的绝对预算。正常运行约 4--6 秒；8 秒后
    // 宁可返回可重试结果，也不能继续占用设备数十秒。
    const uint64_t benchmarkStartNs = monotonic_nanos();
    const uint64_t benchmarkDeadlineNs = benchmarkStartNs + 8000000000ULL;
    auto beforeDeadline = [&]() { return monotonic_nanos() < benchmarkDeadlineNs; };
    auto abortPendingGpuWork = [&](const char* reason) {
        r.invalidReason = reason;
        r.retestNeeded = true;
        r.checksumValid = false;
        r.diag = "elapsedNs=" + std::to_string(monotonic_nanos() - benchmarkStartNs);
        return r;
    };

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
    const uint64_t primeDurationTargetNs = 3000000000ull;
    const double bytesPerDispatch = (double)count * 4.0 * 3.0;

    // Buffer shader 的 barrier 成本在驱动间差异很大。先测单 dispatch，再把
    // prime quantum 缩放到约 80ms，避免固定批量超过 fence timeout。
    RoundTimings probe = h.runRound(
        g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
        g.queryPool, g.fence, useGpuTs, 1, 1000000000ULL);
    if (h.abandoned) return abortPendingGpuWork("gpu fence timeout during buffer probe");
    uint32_t primeDispatches = 1;
    if (probe.submitToFenceNs > 0) {
        primeDispatches = (uint32_t)std::llround(80000000.0 / (double)probe.submitToFenceNs);
        primeDispatches = std::clamp(primeDispatches, 1u, 4u);
    }

    PerformanceHintScope hint(80000000);
    hint.announceIncrease();
    hint.updateTarget(80000000);

    std::vector<uint64_t> primeTimes;
    std::vector<double> primeGbps;
    const uint64_t primeStart = monotonic_nanos();
    const uint64_t primeDeadline = std::min<uint64_t>(
        benchmarkDeadlineNs, primeStart + (uint64_t)4000000000ULL);
    uint32_t attempts = 0;
    while ((monotonic_nanos() - primeStart < primeDurationTargetNs || primeTimes.size() < 8) &&
           monotonic_nanos() < primeDeadline && attempts++ < 128) {
        RoundTimings t = h.runRound(
            g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
            g.queryPool, g.fence, useGpuTs, primeDispatches, 1000000000ULL);
        if (h.abandoned) return abortPendingGpuWork("gpu fence timeout during buffer prime");
        if (t.submitToFenceNs == 0) continue;
        primeTimes.push_back(t.submitToFenceNs);
        primeGbps.push_back(bytesPerDispatch * primeDispatches / (double)t.submitToFenceNs);
        hint.report(t.submitToFenceNs,
                    t.gpuExecNs > 0 ? t.gpuExecNs : t.completionWaitNs,
                    t.commandRecordingNs + t.queueSubmitNs);
    }

    const size_t window = std::min<size_t>(20, primeGbps.size());
    std::vector<double> primeTail;
    if (window > 0) primeTail.assign(primeGbps.end() - window, primeGbps.end());
    const double primeFinal = medianD(primeTail);
    const double primeTailCv = robustCv(primeTail);
    double primePeak = 0.0;
    for (size_t begin = 0; begin < primeGbps.size(); begin += 10) {
        const size_t end = std::min(begin + 10, primeGbps.size());
        if (end - begin >= 5) {
            primePeak = std::max(primePeak, medianD(std::vector<double>(primeGbps.begin() + begin, primeGbps.begin() + end)));
        }
    }

    const uint64_t primeMedianNs = window > 0
        ? medianU64(std::vector<uint64_t>(primeTimes.end() - window, primeTimes.end())) : 0;
    const uint64_t targetNs = (uint64_t)std::max(targetMs, 50) * 1000000ull;
    uint32_t batchesPerSample = 1;
    if (primeMedianNs > 0) {
        batchesPerSample = (uint32_t)std::llround((double)targetNs / (double)primeMedianNs);
        batchesPerSample = std::clamp(batchesPerSample, 1u, 64u);
    }
    const double measureBytes = bytesPerDispatch * primeDispatches * batchesPerSample;

    RoundTimings settle = h.runRound(
        g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
        g.queryPool, g.fence, useGpuTs, primeDispatches, 1000000000ULL);
    if (h.abandoned) return abortPendingGpuWork("gpu fence timeout during buffer settle");
    hint.report(settle.submitToFenceNs,
                settle.gpuExecNs > 0 ? settle.gpuExecNs : settle.completionWaitNs,
                settle.commandRecordingNs + settle.queueSubmitNs);

    std::vector<uint64_t> gpuTimes, recTimes, subTimes, waitTimes, submitFenceTimes;
    std::vector<double> measuredGbps;
    // BenchmarkEngine 会消费 1 个 warmup，正式阶段最多请求 11 个样本。
    for (int i = 0; i < 12; i++) {
        if (!beforeDeadline()) break;
        uint64_t gpuNs = 0, recNs = 0, submitNs = 0, waitNs = 0, wallNs = 0;
        bool complete = true;
        for (uint32_t batch = 0; batch < batchesPerSample; ++batch) {
            if (!beforeDeadline()) { complete = false; break; }
            RoundTimings t = h.runRound(
                g.pipe, g.pl, g.ds, &pcData, sizeof(pcData), groups,
                g.queryPool, g.fence, useGpuTs, primeDispatches, 1000000000ULL);
            if (h.abandoned) return abortPendingGpuWork("gpu fence timeout during buffer measure");
            if (t.submitToFenceNs == 0) { complete = false; break; }
            gpuNs += t.gpuExecNs;
            recNs += t.commandRecordingNs;
            submitNs += t.queueSubmitNs;
            waitNs += t.completionWaitNs;
            wallNs += t.submitToFenceNs;
            hint.report(t.submitToFenceNs,
                        t.gpuExecNs > 0 ? t.gpuExecNs : t.completionWaitNs,
                        t.commandRecordingNs + t.queueSubmitNs);
        }
        if (!complete || wallNs == 0) continue;
        gpuTimes.push_back(gpuNs);
        recTimes.push_back(recNs);
        subTimes.push_back(submitNs);
        waitTimes.push_back(waitNs);
        submitFenceTimes.push_back(wallNs);
        measuredGbps.push_back(measureBytes / (double)wallNs);
    }

    bool unstable = submitFenceTimes.size() < 12 || primeTailCv > 0.07;
    if (primePeak > 0.0 && primeFinal < primePeak * 0.88) unstable = true;
    if (primeFinal > 0.0) {
        for (double value : measuredGbps) {
            if (value > primeFinal * 1.10 || value < primeFinal * 0.85) unstable = true;
        }
    }
    r.retestNeeded = unstable;
    if (submitFenceTimes.size() < 12) {
        r.invalidReason = beforeDeadline() ? "incomplete buffer samples" : "buffer time budget exceeded";
    }
    r.medianNs = medianU64(submitFenceTimes);
    r.commandRecordingNs = medianU64(recTimes);
    r.queueSubmitNs = medianU64(subTimes);
    r.gpuExecNs = medianU64(gpuTimes);
    r.completionWaitNs = medianU64(waitTimes);
    r.sampleNs = submitFenceTimes;
    r.cv = robustCv(measuredGbps);
    if (r.medianNs > 0) r.metricValue = measureBytes / (double)r.medianNs;
    r.diag = "groups=" + std::to_string(groups) +
             " primeDispatches=" + std::to_string(primeDispatches) +
             " probeNs=" + std::to_string(probe.submitToFenceNs) +
             " primeRounds=" + std::to_string(primeTimes.size()) +
             " primeFinal=" + std::to_string(primeFinal) +
             " primePeak=" + std::to_string(primePeak) +
             " primeTailCv=" + std::to_string(primeTailCv) +
             " batchesPerSample=" + std::to_string(batchesPerSample) +
             " adpf=" + std::to_string(hint.active() ? 1 : 0) +
             "," + std::to_string(hint.reportCount()) +
             "," + std::to_string(hint.report2Count()) +
             "," + std::to_string(hint.reportError());

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
    s += "sampleNs=";
    for (size_t i = 0; i < r.sampleNs.size(); ++i) {
        if (i > 0) s += ",";
        s += u64s(r.sampleNs[i]);
    }
    s += ";";
    s += "diag="; s += r.diag; s += ";";
    s += "invalidReason="; s += r.invalidReason;
    return s;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativegpu_VulkanBench_nativeRunVulkanBenchmark(JNIEnv* env, jclass, jint workload, jint targetDurationMs) {
    // 每次调用独占 instance/device/queue/command pool。部分移动 GPU 驱动在大量
    // host-visible buffer workload 后复用长期 device 会卡在下一次提交；初始化开销
    // 不进入任何正式计时区间，因此以生命周期隔离换取跨运行可靠性。
    Harness h;
    WorkResult r;
    if (!h.init()) {
        r.supported = false;
        r.invalidReason = h.err;
    } else if (workload == 2) {
        r = runTriad(h, targetDurationMs);
    } else {
        r = runFp32(h, targetDurationMs, workload == 0);
    }
    return env->NewStringUTF(resultToStr(r).c_str());
}
