#include <jni.h>
#include <cmath>
#include "sv_cpu_internal.h"

static float fp32_fma_loop(uint64_t seed, uint64_t iterations) {
    float base = (float)(seed & 0xffffff) * (1.0f / 16777216.0f) + 1.0f;
    float a0 = base;
    float a1 = base + 0.1f;
    float a2 = base + 0.2f;
    float a3 = base + 0.3f;
    float a4 = base + 0.4f;
    float a5 = base + 0.5f;
    float a6 = base + 0.6f;
    float a7 = base + 0.7f;
    const float x = 1.0000001f;
    const float y = 0.0000001f;
    for (uint64_t i = 0; i < iterations; ++i) {
        a0 = a0 * x + y;
        a1 = a1 * x + y;
        a2 = a2 * x + y;
        a3 = a3 * x + y;
        a4 = a4 * x + y;
        a5 = a5 * x + y;
        a6 = a6 * x + y;
        a7 = a7 * x + y;
    }
    float sum = a0 + a1 + a2 + a3 + a4 + a5 + a6 + a7;
    g_sink = (uint64_t)(sum * 1e9f);
    return sum;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_Fp32FmaWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed) {
    constexpr uint64_t ITERATIONS = 25000000ull;
    constexpr uint64_t FLOP_PER_ITER = 16ull;
    uint64_t t0 = monotonic_nanos();
    fp32_fma_loop((uint64_t)seed, ITERATIONS);
    uint64_t t1 = monotonic_nanos();
    jlong out[2] = { (jlong)(ITERATIONS * FLOP_PER_ITER), (jlong)(t1 - t0) };
    jlongArray result = env->NewLongArray(2);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 2, out);
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_Fp32FmaWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    float r1 = fp32_fma_loop(99ull, 10000ull);
    float r2 = fp32_fma_loop(99ull, 10000ull);
    return (r1 == r2 && std::isfinite(r1) && std::isfinite(r2)) ? JNI_TRUE : JNI_FALSE;
}
