#include <jni.h>
#include <vulkan/vulkan.h>
#include <ctime>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <string>
#include <vector>
#include <algorithm>
#include "fp32_spv.h"
#include "buffer_triad_spv.h"

namespace {

static uint64_t monotonic_nanos() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

struct Stats {
    double median = 0;
    double mad = 0;
    double cv = 0;
};

static Stats computeStats(std::vector<double> v) {
    Stats s;
    if (v.empty()) return s;
    std::sort(v.begin(), v.end());
    size_t n = v.size();
    s.median = (n % 2 == 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
    std::vector<double> dev;
    dev.reserve(n);
    for (double x : v) dev.push_back(std::fabs(x - s.median));
    std::sort(dev.begin(), dev.end());
    s.mad = (n % 2 == 1) ? dev[n / 2] : (dev[n / 2 - 1] + dev[n / 2]) / 2.0;
    s.cv = (s.median > 0) ? s.mad / s.median : 0.0;
    return s;
}

static uint32_t findMemoryType(VkPhysicalDevice phys, uint32_t typeBits, VkMemoryPropertyFlags flags) {
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(phys, &mp);
    for (uint32_t i = 0; i < mp.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & flags) == flags) {
            return i;
        }
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
    bci.size = size;
    bci.usage = usage;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(dev, &bci, nullptr, &out.buf) != VK_SUCCESS) return false;
    VkMemoryRequirements mr;
    vkGetBufferMemoryRequirements(dev, out.buf, &mr);
    uint32_t mt = findMemoryType(phys, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (mt == UINT32_MAX) { vkDestroyBuffer(dev, out.buf, nullptr); out.buf = VK_NULL_HANDLE; return false; }
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size;
    mai.memoryTypeIndex = mt;
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
        dqc.queueFamilyIndex = qf;
        dqc.queueCount = 1;
        dqc.pQueuePriorities = &prio;
        VkDeviceCreateInfo dci{};
        dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        dci.queueCreateInfoCount = 1;
        dci.pQueueCreateInfos = &dqc;
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

    uint64_t runRound(VkPipeline pipeline, VkPipelineLayout layout, VkDescriptorSet descSet,
                      const void* pcData, uint32_t pcSize, uint32_t groups, uint32_t repeatK,
                      VkQueryPool queryPool, VkFence fence, bool useGpuTs) {
        VkCommandBuffer cmd;
        VkCommandBufferAllocateInfo ai{};
        ai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        ai.commandPool = cmdPool;
        ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        ai.commandBufferCount = 1;
        vkAllocateCommandBuffers(device, &ai, &cmd);
        VkCommandBufferBeginInfo bi{};
        bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        vkBeginCommandBuffer(cmd, &bi);
        vkCmdResetQueryPool(cmd, queryPool, 0, 2);
        if (useGpuTs) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, 0);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, 1, &descSet, 0, nullptr);
        if (pcSize > 0) vkCmdPushConstants(cmd, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pcSize, pcData);
        for (uint32_t i = 0; i < repeatK; i++) vkCmdDispatch(cmd, groups, 1, 1);
        if (useGpuTs) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, 1);
        vkEndCommandBuffer(cmd);
        VkSubmitInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.commandBufferCount = 1;
        si.pCommandBuffers = &cmd;
        uint64_t cpu0 = monotonic_nanos();
        vkQueueSubmit(queue, 1, &si, fence);
        vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX);
        vkResetFences(device, 1, &fence);
        uint64_t cpu1 = monotonic_nanos();
        uint64_t ts[2] = {0, 0};
        if (useGpuTs) {
            vkGetQueryPoolResults(device, queryPool, 0, 2, sizeof(ts), ts, sizeof(uint64_t),
                VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
        }
        vkFreeCommandBuffers(device, cmdPool, 1, &cmd);
        if (useGpuTs && ts[0] != 0 && ts[1] != 0) {
            uint64_t diff = ts[1] - ts[0];
            if (tsValidBits < 64) diff &= ((1ULL << tsValidBits) - 1);
            return (uint64_t)((double)diff * (double)tsPeriod);
        }
        return cpu1 - cpu0;
    }

    bool buildPipeline(const uint32_t* spv, size_t spvLen, const VkDescriptorSetLayoutBinding* binds,
                       uint32_t bindCount, VkPushConstantRange pcRange,
                       VkShaderModule& sm, VkDescriptorSetLayout& dsl, VkPipelineLayout& pl,
                       VkDescriptorPool& pool, VkDescriptorSet& ds, VkPipeline& pipe) {
        VkShaderModuleCreateInfo smi{};
        smi.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        smi.codeSize = spvLen;
        smi.pCode = spv;
        if (vkCreateShaderModule(device, &smi, nullptr, &sm) != VK_SUCCESS) return false;

        VkDescriptorSetLayoutCreateInfo dli{};
        dli.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        dli.bindingCount = bindCount;
        dli.pBindings = binds;
        if (vkCreateDescriptorSetLayout(device, &dli, nullptr, &dsl) != VK_SUCCESS) return false;

        VkPipelineLayoutCreateInfo pli{};
        pli.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        pli.setLayoutCount = 1;
        pli.pSetLayouts = &dsl;
        if (pcRange.stageFlags != 0) { pli.pushConstantRangeCount = 1; pli.pPushConstantRanges = &pcRange; }
        if (vkCreatePipelineLayout(device, &pli, nullptr, &pl) != VK_SUCCESS) return false;

        VkDescriptorPoolSize dps{};
        dps.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        dps.descriptorCount = bindCount;
        VkDescriptorPoolCreateInfo dpci{};
        dpci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        dpci.maxSets = 1;
        dpci.poolSizeCount = 1;
        dpci.pPoolSizes = &dps;
        if (vkCreateDescriptorPool(device, &dpci, nullptr, &pool) != VK_SUCCESS) return false;
        VkDescriptorSetAllocateInfo dai{};
        dai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dai.descriptorPool = pool;
        dai.descriptorSetCount = 1;
        dai.pSetLayouts = &dsl;
        if (vkAllocateDescriptorSets(device, &dai, &ds) != VK_SUCCESS) return false;

        VkComputePipelineCreateInfo cpi{};
        cpi.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
        cpi.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        cpi.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        cpi.stage.module = sm;
        cpi.stage.pName = "main";
        cpi.layout = pl;
        if (vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &cpi, nullptr, &pipe) != VK_SUCCESS) return false;
        return true;
    }

    void writeDescriptors(uint32_t bindCount, const Buffer* bufs, VkDescriptorSet ds) {
        std::vector<VkDescriptorBufferInfo> infos(bindCount);
        std::vector<VkWriteDescriptorSet> writes(bindCount);
        for (uint32_t i = 0; i < bindCount; i++) {
            infos[i].buffer = bufs[i].buf;
            infos[i].offset = 0;
            infos[i].range = VK_WHOLE_SIZE;
            writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[i].dstSet = ds;
            writes[i].dstBinding = i;
            writes[i].descriptorCount = 1;
            writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
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
};

static std::string vkVersionStr(uint32_t v) {
    return std::to_string(VK_VERSION_MAJOR(v)) + "." + std::to_string(VK_VERSION_MINOR(v)) + "." + std::to_string(VK_VERSION_PATCH(v));
}

static WorkResult runFp32(Harness& h, int targetMs) {
    WorkResult r;
    r.supported = true;
    r.deviceName = h.props.deviceName;
    r.driverVersion = vkVersionStr(h.props.driverVersion);
    r.vulkanVersion = vkVersionStr(h.props.apiVersion);
    r.metricUnit = "GFLOPS";

    const uint32_t WG = 64;
    const uint32_t vec4Count = 16384;
    const uint32_t iterations = 1024;
    const uint32_t groups = vec4Count / WG;
    const VkDeviceSize bufBytes = (VkDeviceSize)vec4Count * 4 * sizeof(float);

    Buffer io{};
    if (!createBuffer(h.device, h.phys, bufBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, io)) {
        r.invalidReason = "buffer alloc"; return r;
    }
    // fill input
    void* mapped = nullptr;
    vkMapMemory(h.device, io.mem, 0, bufBytes, 0, &mapped);
    float* f = (float*)mapped;
    for (uint32_t i = 0; i < vec4Count * 4; i++) f[i] = (float)((i * 2654435761u) % 1009) / 1009.0f;
    vkUnmapMemory(h.device, io.mem);

    VkDescriptorSetLayoutBinding binds[1] = {};
    binds[0].binding = 0; binds[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    binds[0].descriptorCount = 1; binds[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    VkPushConstantRange pc{}; pc.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; pc.offset = 0; pc.size = 16;
    struct PC { uint32_t iterations; float factor; float offset; uint32_t vec4Count; };
    PC pcData{iterations, 1.0000001f, 0.0000001f, vec4Count};

    VkShaderModule sm = VK_NULL_HANDLE; VkDescriptorSetLayout dsl = VK_NULL_HANDLE;
    VkPipelineLayout pl = VK_NULL_HANDLE; VkDescriptorPool pool = VK_NULL_HANDLE;
    VkDescriptorSet ds = VK_NULL_HANDLE; VkPipeline pipe = VK_NULL_HANDLE;
    if (!h.buildPipeline((const uint32_t*)fp32_spv, fp32_spv_len, binds, 1, pc, sm, dsl, pl, pool, ds, pipe)) {
        r.invalidReason = "pipeline"; destroyBuffer(h.device, io); return r;
    }
    h.writeDescriptors(1, &io, ds);

    VkQueryPoolCreateInfo qpci{}; qpci.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qpci.queryType = VK_QUERY_TYPE_TIMESTAMP; qpci.queryCount = 2;
    VkQueryPool queryPool = VK_NULL_HANDLE;
    vkCreateQueryPool(h.device, &qpci, nullptr, &queryPool);
    VkFence fence = VK_NULL_HANDLE;
    VkFenceCreateInfo fci{}; fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    vkCreateFence(h.device, &fci, nullptr, &fence);

    bool useGpuTs = h.gpuTimestampUsable();

    // calibration
    uint64_t oneNs = h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, 1, queryPool, fence, useGpuTs);
    uint32_t K = 1;
    if (oneNs > 0) {
        uint64_t target = (uint64_t)targetMs * 1000000ull;
        K = (uint32_t)(target / oneNs);
        if (K < 1) K = 1;
        if (K > 4096) K = 4096;
    }

    // warmup 3, measure 7
    for (int i = 0; i < 3; i++) h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, K, queryPool, fence, useGpuTs);
    std::vector<double> times;
    for (int i = 0; i < 7; i++) {
        uint64_t ns = h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, K, queryPool, fence, useGpuTs);
        times.push_back((double)ns);
    }
    Stats s = computeStats(times);
    r.medianNs = (uint64_t)s.median;
    r.cv = s.cv;
    if (!useGpuTs) r.invalidReason = "GPU timestamp unsupported";

    // total FLOP per round = vec4Count * iterations * 4 (scalar FMA) * 2 (FMA=2 FLOP) * K
    double flopPerRound = (double)vec4Count * iterations * 4.0 * 2.0 * K;
    if (r.medianNs > 0) r.metricValue = flopPerRound / (double)r.medianNs; // GFLOPS (FLOP/ns = GFLOPS)
    else r.metricValue = 0;

    // checksum: no NaN/Inf, finite sum, computation happened (output != input)
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

    if (fence) vkDestroyFence(h.device, fence, nullptr);
    if (queryPool) vkDestroyQueryPool(h.device, queryPool, nullptr);
    if (pipe) vkDestroyPipeline(h.device, pipe, nullptr);
    if (pool) vkDestroyDescriptorPool(h.device, pool, nullptr);
    if (pl) vkDestroyPipelineLayout(h.device, pl, nullptr);
    if (dsl) vkDestroyDescriptorSetLayout(h.device, dsl, nullptr);
    if (sm) vkDestroyShaderModule(h.device, sm, nullptr);
    destroyBuffer(h.device, io);
    return r;
}

static WorkResult runTriad(Harness& h, int targetMs) {
    WorkResult r;
    r.supported = true;
    r.deviceName = h.props.deviceName;
    r.driverVersion = vkVersionStr(h.props.driverVersion);
    r.vulkanVersion = vkVersionStr(h.props.apiVersion);
    r.metricUnit = "GB/s";

    const uint32_t WG = 64;
    const uint32_t count = 16 * 1024 * 1024; // 64 MiB of floats
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
    vkMapMemory(h.device, A.mem, 0, bufBytes, 0, &m);
    float* fa = (float*)m;
    vkMapMemory(h.device, B.mem, 0, bufBytes, 0, &m);
    float* fb = (float*)m;
    for (uint32_t i = 0; i < count; i++) { fa[i] = (float)((i * 7) % 1009) / 1009.0f; fb[i] = (float)((i * 13) % 1009) / 1009.0f; }
    vkUnmapMemory(h.device, A.mem);
    vkUnmapMemory(h.device, B.mem);

    VkDescriptorSetLayoutBinding binds[3] = {};
    for (uint32_t i = 0; i < 3; i++) { binds[i].binding = i; binds[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER; binds[i].descriptorCount = 1; binds[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; }
    VkPushConstantRange pc{}; pc.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT; pc.offset = 0; pc.size = 8;
    struct PC { float scalar; uint32_t count; };
    PC pcData{scalar, count};

    VkShaderModule sm = VK_NULL_HANDLE; VkDescriptorSetLayout dsl = VK_NULL_HANDLE;
    VkPipelineLayout pl = VK_NULL_HANDLE; VkDescriptorPool pool = VK_NULL_HANDLE;
    VkDescriptorSet ds = VK_NULL_HANDLE; VkPipeline pipe = VK_NULL_HANDLE;
    if (!h.buildPipeline((const uint32_t*)buffer_triad_spv, buffer_triad_spv_len, binds, 3, pc, sm, dsl, pl, pool, ds, pipe)) {
        r.invalidReason = "pipeline";
        destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
        return r;
    }
    Buffer bufs[3] = {A, B, O};
    h.writeDescriptors(3, bufs, ds);

    VkQueryPoolCreateInfo qpci{}; qpci.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qpci.queryType = VK_QUERY_TYPE_TIMESTAMP; qpci.queryCount = 2;
    VkQueryPool queryPool = VK_NULL_HANDLE;
    vkCreateQueryPool(h.device, &qpci, nullptr, &queryPool);
    VkFence fence = VK_NULL_HANDLE;
    VkFenceCreateInfo fci{}; fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    vkCreateFence(h.device, &fci, nullptr, &fence);

    bool useGpuTs = h.gpuTimestampUsable();

    uint64_t oneNs = h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, 1, queryPool, fence, useGpuTs);
    uint32_t K = 1;
    if (oneNs > 0) {
        uint64_t target = (uint64_t)targetMs * 1000000ull;
        K = (uint32_t)(target / oneNs); if (K < 1) K = 1; if (K > 64) K = 64;
    }
    for (int i = 0; i < 3; i++) h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, K, queryPool, fence, useGpuTs);
    std::vector<double> times;
    for (int i = 0; i < 7; i++) {
        uint64_t ns = h.runRound(pipe, pl, ds, &pcData, sizeof(pcData), groups, K, queryPool, fence, useGpuTs);
        times.push_back((double)ns);
    }
    Stats s = computeStats(times);
    r.medianNs = (uint64_t)s.median;
    r.cv = s.cv;
    if (!useGpuTs) r.invalidReason = "GPU timestamp unsupported";

    double bytesPerRound = (double)count * 4.0 * 3.0 * K;
    if (r.medianNs > 0) r.metricValue = bytesPerRound / (double)r.medianNs; // GB/s
    else r.metricValue = 0;

    // checksum: O[i] == A[i] + scalar*B[i]
    vkMapMemory(h.device, A.mem, 0, bufBytes, 0, &m); fa = (float*)m;
    vkMapMemory(h.device, B.mem, 0, bufBytes, 0, &m); fb = (float*)m;
    vkMapMemory(h.device, O.mem, 0, bufBytes, 0, &m); float* fo = (float*)m;
    bool ok = true;
    for (uint32_t i = 0; i < count; i++) {
        float expected = fa[i] + scalar * fb[i];
        if (std::fabs(fo[i] - expected) > 1e-4f) { ok = false; break; }
    }
    vkUnmapMemory(h.device, A.mem); vkUnmapMemory(h.device, B.mem); vkUnmapMemory(h.device, O.mem);
    r.checksumValid = ok;
    if (!r.checksumValid && r.invalidReason.empty()) r.invalidReason = "checksum";

    if (fence) vkDestroyFence(h.device, fence, nullptr);
    if (queryPool) vkDestroyQueryPool(h.device, queryPool, nullptr);
    if (pipe) vkDestroyPipeline(h.device, pipe, nullptr);
    if (pool) vkDestroyDescriptorPool(h.device, pool, nullptr);
    if (pl) vkDestroyPipelineLayout(h.device, pl, nullptr);
    if (dsl) vkDestroyDescriptorSetLayout(h.device, dsl, nullptr);
    if (sm) vkDestroyShaderModule(h.device, sm, nullptr);
    destroyBuffer(h.device, A); destroyBuffer(h.device, B); destroyBuffer(h.device, O);
    return r;
}

static Harness& harness() {
    static Harness h;
    static bool tried = false;
    if (!tried) { tried = true; h.init(); }
    return h;
}

static std::string resultToStr(const WorkResult& r) {
    std::string s;
    s += "supported="; s += (r.supported ? "1" : "0"); s += ";";
    s += "deviceName="; s += r.deviceName; s += ";";
    s += "driverVersion="; s += r.driverVersion; s += ";";
    s += "vulkanVersion="; s += r.vulkanVersion; s += ";";
    s += "metricValue="; s += std::to_string(r.metricValue); s += ";";
    s += "metricUnit="; s += r.metricUnit; s += ";";
    s += "medianNs="; s += std::to_string(r.medianNs); s += ";";
    s += "cv="; s += std::to_string(r.cv); s += ";";
    s += "checksumValid="; s += (r.checksumValid ? "1" : "0"); s += ";";
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
        r.metricUnit = "";
    } else {
        r = (workload == 0) ? runFp32(h, targetDurationMs) : runTriad(h, targetDurationMs);
    }
    return env->NewStringUTF(resultToStr(r).c_str());
}
