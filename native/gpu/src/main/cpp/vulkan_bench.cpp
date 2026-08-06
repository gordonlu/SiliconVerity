#include <jni.h>
#include <string>
#include "fp32_spv.h"
#include "buffer_triad_spv.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativegpu_VulkanBench_nativeProbe(JNIEnv* env, jclass) {
    std::string s = "sv_gpu:fp32_spv_len=";
    s += std::to_string(fp32_spv_len);
    s += ",buffer_triad_spv_len=";
    s += std::to_string(buffer_triad_spv_len);
    return env->NewStringUTF(s.c_str());
}
