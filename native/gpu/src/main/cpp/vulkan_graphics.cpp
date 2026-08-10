#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <time.h>

#include "procedural_glow_vert_spv.h"
#include "procedural_glow_frag_spv.h"

namespace {

std::atomic<bool> g_cancelGraphics{false};

uint64_t nowNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1000000000ULL + static_cast<uint64_t>(ts.tv_nsec);
}

uint64_t median(std::vector<uint64_t> values) {
    if (values.empty()) return 0;
    const size_t mid = values.size() / 2;
    std::nth_element(values.begin(), values.begin() + mid, values.end());
    uint64_t result = values[mid];
    if ((values.size() & 1u) == 0u) {
        std::nth_element(values.begin(), values.begin() + mid - 1, values.end());
        result = (result + values[mid - 1]) / 2;
    }
    return result;
}

uint64_t percentile95(std::vector<uint64_t> values) {
    if (values.empty()) return 0;
    std::sort(values.begin(), values.end());
    const size_t index = std::min(values.size() - 1,
        static_cast<size_t>(std::ceil(values.size() * 0.95)) - 1);
    return values[index];
}

double robustCv(const std::vector<uint64_t>& values) {
    if (values.empty()) return 0.0;
    const uint64_t med = median(values);
    if (med == 0) return 0.0;
    std::vector<uint64_t> deviations;
    deviations.reserve(values.size());
    for (uint64_t value : values) {
        deviations.push_back(value > med ? value - med : med - value);
    }
    return static_cast<double>(median(deviations)) / static_cast<double>(med);
}

uint64_t fnv1a(const unsigned char* data, size_t size, uint64_t hash = 1469598103934665603ULL) {
    for (size_t i = 0; i < size; ++i) {
        hash ^= data[i];
        hash *= 1099511628211ULL;
    }
    return hash;
}

std::string hex64(uint64_t value) {
    static const char* digits = "0123456789abcdef";
    std::string out(16, '0');
    for (int i = 15; i >= 0; --i) {
        out[i] = digits[value & 0xfu];
        value >>= 4u;
    }
    return out;
}

struct GraphicsResult {
    bool supported = false;
    bool valid = false;
    std::string deviceName;
    std::string driverVersion;
    std::string vulkanVersion;
    std::string invalidReason;
    std::string presentMode;
    std::string shaderHash;
    uint32_t width = 0;
    uint32_t height = 0;
    uint64_t frames = 0;
    uint64_t elapsedNs = 0;
    uint64_t medianFrameNs = 0;
    uint64_t p95FrameNs = 0;
    uint64_t medianGpuNs = 0;
    uint64_t measuredSceneIterations = 0;
    uint32_t workloadIterations = 1;
    double sceneIterationsPerSecond = 0.0;
    double presentedFps = 0.0;
    double cv = 0.0;
    std::vector<uint64_t> frameNs;
    std::vector<uint64_t> sceneIterationNs;
};

std::string versionString(uint32_t value) {
    return std::to_string(VK_VERSION_MAJOR(value)) + "." +
           std::to_string(VK_VERSION_MINOR(value)) + "." +
           std::to_string(VK_VERSION_PATCH(value));
}

std::string toProtocol(const GraphicsResult& r) {
    std::string s;
    s += "supported=" + std::string(r.supported ? "1" : "0") + ";";
    s += "deviceName=" + r.deviceName + ";";
    s += "driverVersion=" + r.driverVersion + ";";
    s += "vulkanVersion=" + r.vulkanVersion + ";";
    s += "metricValue=" + std::to_string(r.sceneIterationsPerSecond) + ";metricUnit=scene/s;";
    s += "medianNs=" + std::to_string(r.medianFrameNs) + ";";
    s += "cv=" + std::to_string(r.cv) + ";";
    s += "checksumValid=" + std::string(r.valid ? "1" : "0") + ";";
    s += "commandRecordingNs=0;queueSubmitNs=0;";
    s += "gpuExecNs=" + std::to_string(r.medianGpuNs) + ";completionWaitNs=0;";
    s += "spirvHash=" + r.shaderHash + ";arithType=FP32;arithContract=DEVICE_DEFAULT;";
    s += "retest=0;sampleNs=";
    // A sample is normalized GPU time for one standard scene iteration, so the
    // primary score stays comparable when warmup selects a larger batch.
    const size_t stride = std::max<size_t>(1, r.sceneIterationNs.size() / 120);
    bool first = true;
    for (size_t i = 0; i < r.sceneIterationNs.size(); i += stride) {
        if (!first) s += ",";
        first = false;
        s += std::to_string(r.sceneIterationNs[i]);
    }
    s += ";";
    s += "totalFrames=" + std::to_string(r.frames) + ";";
    s += "elapsedNs=" + std::to_string(r.elapsedNs) + ";";
    s += "p95FrameNs=" + std::to_string(r.p95FrameNs) + ";";
    s += "surfaceWidth=" + std::to_string(r.width) + ";";
    s += "surfaceHeight=" + std::to_string(r.height) + ";";
    s += "presentMode=" + r.presentMode + ";";
    s += "presentedFps=" + std::to_string(r.presentedFps) + ";";
    s += "workloadIterations=" + std::to_string(r.workloadIterations) + ";";
    s += "measuredSceneIterations=" + std::to_string(r.measuredSceneIterations) + ";";
    s += "diag=frames=" + std::to_string(r.frames) +
         ",elapsedMs=" + std::to_string(r.elapsedNs / 1000000ULL) +
         ",extent=" + std::to_string(r.width) + "x" + std::to_string(r.height) +
         ",present=" + r.presentMode +
         ",presentedFps=" + std::to_string(r.presentedFps) +
         ",iterationsPerPresent=" + std::to_string(r.workloadIterations) +
         ",sceneIterations=" + std::to_string(r.measuredSceneIterations) +
         ",p95Ns=" + std::to_string(r.p95FrameNs) + ";";
    s += "invalidReason=" + r.invalidReason;
    return s;
}

VkShaderModule createShader(VkDevice device, const unsigned char* bytes, size_t length) {
    VkShaderModuleCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    ci.codeSize = length;
    ci.pCode = reinterpret_cast<const uint32_t*>(bytes);
    VkShaderModule shader = VK_NULL_HANDLE;
    return vkCreateShaderModule(device, &ci, nullptr, &shader) == VK_SUCCESS ? shader : VK_NULL_HANDLE;
}

struct PushConstants {
    float resolution[2];
    float time;
    uint32_t frameIndex;
};

GraphicsResult runGraphics(ANativeWindow* window, uint32_t warmupMs, uint32_t durationMs) {
    GraphicsResult result;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkShaderModule vert = VK_NULL_HANDLE;
    VkShaderModule frag = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkSemaphore imageReady = VK_NULL_HANDLE;
    std::vector<VkSemaphore> renderDone;
    VkFence frameFence = VK_NULL_HANDLE;
    VkQueryPool queryPool = VK_NULL_HANDLE;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> framebuffers;
    VkQueue queue = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    float timestampPeriod = 0.0f;
    uint32_t timestampValidBits = 0;
    bool submitted = false;
    bool measuring = false;
    uint64_t officialStart = 0;
    uint64_t previousFrameStart = 0;
    bool pendingMeasured = false;

    auto fail = [&](const char* reason) {
        if (result.invalidReason.empty()) result.invalidReason = reason;
    };
    auto cleanup = [&]() {
        if (device) vkDeviceWaitIdle(device);
        if (queryPool) vkDestroyQueryPool(device, queryPool, nullptr);
        if (frameFence) vkDestroyFence(device, frameFence, nullptr);
        for (VkSemaphore semaphore : renderDone) vkDestroySemaphore(device, semaphore, nullptr);
        if (imageReady) vkDestroySemaphore(device, imageReady, nullptr);
        if (commandPool) vkDestroyCommandPool(device, commandPool, nullptr);
        for (VkFramebuffer fb : framebuffers) vkDestroyFramebuffer(device, fb, nullptr);
        if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
        if (pipelineLayout) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
        if (vert) vkDestroyShaderModule(device, vert, nullptr);
        if (frag) vkDestroyShaderModule(device, frag, nullptr);
        if (renderPass) vkDestroyRenderPass(device, renderPass, nullptr);
        for (VkImageView view : views) vkDestroyImageView(device, view, nullptr);
        if (swapchain) vkDestroySwapchainKHR(device, swapchain, nullptr);
        if (device) vkDestroyDevice(device, nullptr);
        if (surface) vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
    };

    const char* instanceExtensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "SiliconVerity Graphics";
    app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &app;
    instanceInfo.enabledExtensionCount = 2;
    instanceInfo.ppEnabledExtensionNames = instanceExtensions;
    if (vkCreateInstance(&instanceInfo, nullptr, &instance) != VK_SUCCESS) {
        fail("graphics instance"); cleanup(); return result;
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = window;
    if (vkCreateAndroidSurfaceKHR(instance, &surfaceInfo, nullptr, &surface) != VK_SUCCESS) {
        fail("android surface"); cleanup(); return result;
    }

    uint32_t physicalCount = 0;
    vkEnumeratePhysicalDevices(instance, &physicalCount, nullptr);
    std::vector<VkPhysicalDevice> physicals(physicalCount);
    if (physicalCount) vkEnumeratePhysicalDevices(instance, &physicalCount, physicals.data());
    uint32_t queueFamily = UINT32_MAX;
    for (VkPhysicalDevice candidate : physicals) {
        uint32_t count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &count, nullptr);
        std::vector<VkQueueFamilyProperties> props(count);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &count, props.data());
        for (uint32_t i = 0; i < count; ++i) {
            VkBool32 present = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &present);
            if ((props[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                physical = candidate;
                queueFamily = i;
                timestampValidBits = props[i].timestampValidBits;
                break;
            }
        }
        if (physical) break;
    }
    if (!physical || queueFamily == UINT32_MAX) {
        fail("no graphics+present queue"); cleanup(); return result;
    }

    VkPhysicalDeviceProperties physicalProps{};
    vkGetPhysicalDeviceProperties(physical, &physicalProps);
    result.deviceName = physicalProps.deviceName;
    result.driverVersion = versionString(physicalProps.driverVersion);
    result.vulkanVersion = versionString(physicalProps.apiVersion);
    timestampPeriod = physicalProps.limits.timestampPeriod;

    const float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1;
    deviceInfo.ppEnabledExtensionNames = deviceExtensions;
    if (vkCreateDevice(physical, &deviceInfo, nullptr, &device) != VK_SUCCESS) {
        fail("graphics device"); cleanup(); return result;
    }
    vkGetDeviceQueue(device, queueFamily, 0, &queue);

    VkSurfaceCapabilitiesKHR caps{};
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &caps);
    uint32_t formatCount = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &formatCount, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    if (formatCount) vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &formatCount, formats.data());
    if (formats.empty()) { fail("no surface format"); cleanup(); return result; }
    VkSurfaceFormatKHR format = formats[0];
    for (const auto& candidate : formats) {
        if ((candidate.format == VK_FORMAT_R8G8B8A8_UNORM || candidate.format == VK_FORMAT_B8G8R8A8_UNORM) &&
            candidate.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            format = candidate;
            break;
        }
    }
    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = std::clamp<uint32_t>(1920, caps.minImageExtent.width, caps.maxImageExtent.width);
        extent.height = std::clamp<uint32_t>(1080, caps.minImageExtent.height, caps.maxImageExtent.height);
    }
    result.width = extent.width;
    result.height = extent.height;

    uint32_t presentCount = 0;
    vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, &presentCount, nullptr);
    std::vector<VkPresentModeKHR> presentModes(presentCount);
    if (presentCount) vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, &presentCount, presentModes.data());
    VkPresentModeKHR selectedPresent = VK_PRESENT_MODE_FIFO_KHR;
    if (std::find(presentModes.begin(), presentModes.end(), VK_PRESENT_MODE_MAILBOX_KHR) != presentModes.end()) {
        selectedPresent = VK_PRESENT_MODE_MAILBOX_KHR;
        result.presentMode = "MAILBOX";
    } else {
        result.presentMode = "FIFO";
    }

    uint32_t imageCount = caps.minImageCount + 1;
    if (caps.maxImageCount > 0) imageCount = std::min(imageCount, caps.maxImageCount);
    VkCompositeAlphaFlagBitsKHR composite = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    const VkCompositeAlphaFlagBitsKHR alphaModes[] = {
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR};
    for (auto mode : alphaModes) if (caps.supportedCompositeAlpha & mode) { composite = mode; break; }
    VkSwapchainCreateInfoKHR swapInfo{};
    swapInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapInfo.surface = surface;
    swapInfo.minImageCount = imageCount;
    swapInfo.imageFormat = format.format;
    swapInfo.imageColorSpace = format.colorSpace;
    swapInfo.imageExtent = extent;
    swapInfo.imageArrayLayers = 1;
    swapInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swapInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapInfo.preTransform = caps.currentTransform;
    swapInfo.compositeAlpha = composite;
    swapInfo.presentMode = selectedPresent;
    swapInfo.clipped = VK_TRUE;
    if (vkCreateSwapchainKHR(device, &swapInfo, nullptr, &swapchain) != VK_SUCCESS) {
        fail("swapchain"); cleanup(); return result;
    }
    vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr);
    std::vector<VkImage> images(imageCount);
    vkGetSwapchainImagesKHR(device, swapchain, &imageCount, images.data());

    views.resize(imageCount, VK_NULL_HANDLE);
    for (uint32_t i = 0; i < imageCount; ++i) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = images[i];
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = format.format;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.layerCount = 1;
        if (vkCreateImageView(device, &viewInfo, nullptr, &views[i]) != VK_SUCCESS) {
            fail("swapchain image view"); cleanup(); return result;
        }
    }

    VkAttachmentDescription attachment{};
    attachment.format = format.format;
    attachment.samples = VK_SAMPLE_COUNT_1_BIT;
    attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    VkAttachmentReference colorRef{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorRef;
    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &attachment;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;
    if (vkCreateRenderPass(device, &renderPassInfo, nullptr, &renderPass) != VK_SUCCESS) {
        fail("graphics render pass"); cleanup(); return result;
    }

    vert = createShader(device, procedural_glow_vert_spv, procedural_glow_vert_spv_len);
    frag = createShader(device, procedural_glow_frag_spv, procedural_glow_frag_spv_len);
    if (!vert || !frag) { fail("graphics shader"); cleanup(); return result; }
    uint64_t hash = fnv1a(procedural_glow_vert_spv, procedural_glow_vert_spv_len);
    result.shaderHash = hex64(fnv1a(procedural_glow_frag_spv, procedural_glow_frag_spv_len, hash));

    VkPushConstantRange pushRange{};
    pushRange.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    pushRange.size = sizeof(PushConstants);
    VkPipelineLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.pushConstantRangeCount = 1;
    layoutInfo.pPushConstantRanges = &pushRange;
    if (vkCreatePipelineLayout(device, &layoutInfo, nullptr, &pipelineLayout) != VK_SUCCESS) {
        fail("graphics pipeline layout"); cleanup(); return result;
    }

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vert;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = frag;
    stages[1].pName = "main";
    VkPipelineVertexInputStateCreateInfo vertexInput{};
    vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo assembly{};
    assembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkViewport viewport{0.0f, 0.0f, static_cast<float>(extent.width), static_cast<float>(extent.height), 0.0f, 1.0f};
    VkRect2D scissor{{0, 0}, extent};
    VkPipelineViewportStateCreateInfo viewportState{};
    viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.pViewports = &viewport;
    viewportState.scissorCount = 1;
    viewportState.pScissors = &scissor;
    VkPipelineRasterizationStateCreateInfo raster{};
    raster.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.cullMode = VK_CULL_MODE_NONE;
    raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    raster.lineWidth = 1.0f;
    VkPipelineMultisampleStateCreateInfo multisample{};
    multisample.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState blendAttachment{};
    blendAttachment.colorWriteMask = 0xf;
    VkPipelineColorBlendStateCreateInfo blend{};
    blend.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    blend.attachmentCount = 1;
    blend.pAttachments = &blendAttachment;
    VkGraphicsPipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipelineInfo.stageCount = 2;
    pipelineInfo.pStages = stages;
    pipelineInfo.pVertexInputState = &vertexInput;
    pipelineInfo.pInputAssemblyState = &assembly;
    pipelineInfo.pViewportState = &viewportState;
    pipelineInfo.pRasterizationState = &raster;
    pipelineInfo.pMultisampleState = &multisample;
    pipelineInfo.pColorBlendState = &blend;
    pipelineInfo.layout = pipelineLayout;
    pipelineInfo.renderPass = renderPass;
    if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline) != VK_SUCCESS) {
        fail("graphics pipeline"); cleanup(); return result;
    }

    framebuffers.resize(imageCount, VK_NULL_HANDLE);
    for (uint32_t i = 0; i < imageCount; ++i) {
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = renderPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = &views[i];
        framebufferInfo.width = extent.width;
        framebufferInfo.height = extent.height;
        framebufferInfo.layers = 1;
        if (vkCreateFramebuffer(device, &framebufferInfo, nullptr, &framebuffers[i]) != VK_SUCCESS) {
            fail("graphics framebuffer"); cleanup(); return result;
        }
    }

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = queueFamily;
    if (vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool) != VK_SUCCESS) {
        fail("graphics command pool"); cleanup(); return result;
    }
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;
    if (vkAllocateCommandBuffers(device, &allocInfo, &commandBuffer) != VK_SUCCESS) {
        fail("graphics command buffer"); cleanup(); return result;
    }
    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    if (vkCreateSemaphore(device, &semaphoreInfo, nullptr, &imageReady) != VK_SUCCESS) {
        fail("graphics semaphore"); cleanup(); return result;
    }
    renderDone.resize(imageCount, VK_NULL_HANDLE);
    for (VkSemaphore& semaphore : renderDone) {
        if (vkCreateSemaphore(device, &semaphoreInfo, nullptr, &semaphore) != VK_SUCCESS) {
            fail("graphics present semaphore"); cleanup(); return result;
        }
    }
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (vkCreateFence(device, &fenceInfo, nullptr, &frameFence) != VK_SUCCESS) {
        fail("graphics fence"); cleanup(); return result;
    }
    if (timestampValidBits > 0) {
        VkQueryPoolCreateInfo queryInfo{};
        queryInfo.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
        queryInfo.queryType = VK_QUERY_TYPE_TIMESTAMP;
        queryInfo.queryCount = 2;
        vkCreateQueryPool(device, &queryInfo, nullptr, &queryPool);
    }

    result.supported = true;
    const uint64_t runStart = nowNs();
    const uint64_t warmupNs = static_cast<uint64_t>(std::clamp<uint32_t>(warmupMs, 500, 5000)) * 1000000ULL;
    const uint64_t durationNs = static_cast<uint64_t>(std::clamp<uint32_t>(durationMs, 10000, 30000)) * 1000000ULL;
    uint32_t frameIndex = 0;
    std::vector<uint64_t> gpuTimes;
    constexpr uint32_t workloadIterations = 1;
    constexpr uint32_t pendingIterations = 1;
    uint64_t measuredGpuNs = 0;

    while (!g_cancelGraphics.load(std::memory_order_relaxed)) {
        VkResult waitResult = vkWaitForFences(device, 1, &frameFence, VK_TRUE, 1000000000ULL);
        if (waitResult != VK_SUCCESS) { fail("graphics fence timeout"); break; }
        if (submitted && queryPool) {
            uint64_t timestamps[2]{};
            if (vkGetQueryPoolResults(device, queryPool, 0, 2, sizeof(timestamps), timestamps,
                    sizeof(uint64_t), VK_QUERY_RESULT_64_BIT) == VK_SUCCESS && timestamps[1] > timestamps[0]) {
                uint64_t ticks = timestamps[1] - timestamps[0];
                if (timestampValidBits < 64) ticks &= ((1ULL << timestampValidBits) - 1ULL);
                const uint64_t gpuNs = static_cast<uint64_t>(ticks * timestampPeriod);
                if (pendingMeasured) {
                    gpuTimes.push_back(gpuNs);
                    measuredGpuNs += gpuNs;
                    result.measuredSceneIterations += pendingIterations;
                    result.sceneIterationNs.push_back(std::max<uint64_t>(1, gpuNs / pendingIterations));
                }
            }
        }
        submitted = false;

        const uint64_t frameStart = nowNs();
        if (!measuring && frameStart - runStart >= warmupNs) {
            measuring = true;
            result.workloadIterations = workloadIterations;
            officialStart = frameStart;
            previousFrameStart = frameStart;
        }
        if (measuring && frameStart - officialStart >= durationNs) break;

        uint32_t imageIndex = 0;
        VkResult acquired = vkAcquireNextImageKHR(device, swapchain, 500000000ULL, imageReady, VK_NULL_HANDLE, &imageIndex);
        if (acquired == VK_TIMEOUT) continue;
        if (acquired != VK_SUCCESS && acquired != VK_SUBOPTIMAL_KHR) { fail("acquire swapchain image"); break; }

        vkResetCommandBuffer(commandBuffer, 0);
        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS) { fail("begin graphics command"); break; }
        if (queryPool) {
            vkCmdResetQueryPool(commandBuffer, queryPool, 0, 2);
            vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, 0);
        }
        VkClearValue clear{};
        clear.color.float32[3] = 1.0f;
        VkRenderPassBeginInfo passBegin{};
        passBegin.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        passBegin.renderPass = renderPass;
        passBegin.framebuffer = framebuffers[imageIndex];
        passBegin.renderArea.extent = extent;
        passBegin.clearValueCount = 1;
        passBegin.pClearValues = &clear;
        vkCmdBeginRenderPass(commandBuffer, &passBegin, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        PushConstants push{{static_cast<float>(extent.width), static_cast<float>(extent.height)},
                           static_cast<float>(frameIndex) / 60.0f, frameIndex};
        vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(push), &push);
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderPass(commandBuffer);
        if (queryPool) vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, 1);
        if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) { fail("end graphics command"); break; }

        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{};
        submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &imageReady;
        submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &commandBuffer;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &renderDone[imageIndex];
        vkResetFences(device, 1, &frameFence);
        if (vkQueueSubmit(queue, 1, &submit, frameFence) != VK_SUCCESS) { fail("submit graphics frame"); break; }
        submitted = true;

        VkPresentInfoKHR present{};
        present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        present.waitSemaphoreCount = 1;
        present.pWaitSemaphores = &renderDone[imageIndex];
        present.swapchainCount = 1;
        present.pSwapchains = &swapchain;
        present.pImageIndices = &imageIndex;
        VkResult presented = vkQueuePresentKHR(queue, &present);
        if (presented != VK_SUCCESS && presented != VK_SUBOPTIMAL_KHR) { fail("present graphics frame"); break; }

        pendingMeasured = measuring;
        if (measuring) {
            if (previousFrameStart > 0 && frameStart > previousFrameStart) result.frameNs.push_back(frameStart - previousFrameStart);
            previousFrameStart = frameStart;
            result.frames++;
        }
        frameIndex++;
    }

    if (submitted) {
        vkWaitForFences(device, 1, &frameFence, VK_TRUE, 1000000000ULL);
        if (pendingMeasured && queryPool) {
            uint64_t timestamps[2]{};
            if (vkGetQueryPoolResults(device, queryPool, 0, 2, sizeof(timestamps), timestamps,
                    sizeof(uint64_t), VK_QUERY_RESULT_64_BIT) == VK_SUCCESS && timestamps[1] > timestamps[0]) {
                uint64_t ticks = timestamps[1] - timestamps[0];
                if (timestampValidBits < 64) ticks &= ((1ULL << timestampValidBits) - 1ULL);
                const uint64_t gpuNs = static_cast<uint64_t>(ticks * timestampPeriod);
                gpuTimes.push_back(gpuNs);
                measuredGpuNs += gpuNs;
                result.measuredSceneIterations += pendingIterations;
                result.sceneIterationNs.push_back(std::max<uint64_t>(1, gpuNs / pendingIterations));
            }
        }
    }
    const uint64_t end = nowNs();
    if (officialStart > 0 && end > officialStart) result.elapsedNs = end - officialStart;
    result.medianFrameNs = median(result.sceneIterationNs);
    result.p95FrameNs = percentile95(result.frameNs);
    result.medianGpuNs = median(gpuTimes);
    result.cv = robustCv(result.sceneIterationNs);
    if (result.elapsedNs > 0) result.presentedFps = static_cast<double>(result.frames) * 1e9 / result.elapsedNs;
    if (measuredGpuNs > 0) {
        result.sceneIterationsPerSecond = static_cast<double>(result.measuredSceneIterations) * 1e9 /
            static_cast<double>(measuredGpuNs);
    }
    result.valid = result.invalidReason.empty() && !g_cancelGraphics.load() && result.frames > 0 &&
                   result.elapsedNs >= durationNs && result.width > 0 && result.height > 0 &&
                   result.sceneIterationNs.size() >= 8 && result.sceneIterationsPerSecond > 0.0;
    if (!result.valid && result.invalidReason.empty()) {
        result.invalidReason = g_cancelGraphics.load() ? "graphics cancelled" :
            (queryPool ? "graphics duration or GPU samples incomplete" : "graphics timestamps unavailable");
    }
    cleanup();
    return result;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativegpu_VulkanBench_nativeRunVulkanGraphics(
    JNIEnv* env, jobject, jobject surfaceObject, jint warmupMs, jint durationMs) {
    g_cancelGraphics.store(false, std::memory_order_relaxed);
    if (!surfaceObject) return env->NewStringUTF("supported=0;checksumValid=0;invalidReason=surface unavailable");
    ANativeWindow* window = ANativeWindow_fromSurface(env, surfaceObject);
    if (!window) return env->NewStringUTF("supported=0;checksumValid=0;invalidReason=native window unavailable");
    GraphicsResult result = runGraphics(window, static_cast<uint32_t>(warmupMs), static_cast<uint32_t>(durationMs));
    ANativeWindow_release(window);
    return env->NewStringUTF(toProtocol(result).c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_siliconverity_nativegpu_VulkanBench_nativeCancelVulkanGraphics(JNIEnv*, jobject) {
    g_cancelGraphics.store(true, std::memory_order_relaxed);
}
