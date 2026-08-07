#include <jni.h>
#include "sv_cpu_internal.h"

volatile uint64_t g_sink = 0;

static uint64_t int_alu_loop(uint64_t seed, uint64_t iterations) {
    uint64_t acc = seed ^ 0x9E3779B97F4A7C15ull;
    uint64_t mix = 0;
    for (uint64_t i = 0; i < iterations; ++i) {
        acc = acc * 0x100000001B3ull ^ (i + 0xABCDEF);
        acc = (acc << 7) | (acc >> 57);
        acc ^= acc >> 31;
        acc *= 0xFF51AFD7ED558CCDull;
        acc ^= acc >> 33;
        mix += acc;
    }
    g_sink = mix;
    return mix;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeProbe(JNIEnv* env, jclass) {
    return env->NewStringUTF("sv_cpu_int:r29:c++20");
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed, jlong iterations) {
    uint64_t iters = (iterations > 0) ? (uint64_t)iterations : 50000000ull;
    uint64_t t0 = monotonic_nanos();
    int_alu_loop((uint64_t)seed, iters);
    uint64_t t1 = monotonic_nanos();
    jlong out[2] = { (jlong)iters, (jlong)(t1 - t0) };
    jlongArray result = env->NewLongArray(2);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 2, out);
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    uint64_t m1 = int_alu_loop(42ull, 10000ull);
    uint64_t m2 = int_alu_loop(42ull, 10000ull);
    return (m1 == m2) ? JNI_TRUE : JNI_FALSE;
}
