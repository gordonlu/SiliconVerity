#include <jni.h>
#include <cstdint>
#include "sv_cpu_internal.h"

static uint64_t int_branch_loop(uint64_t seed, uint64_t iterations) {
    uint64_t acc = seed ^ 0x9E3779B97F4A7C15ull;
    for (uint64_t i = 0; i < iterations; ++i) {
        if (acc & 1u) {
            acc = acc * 0x100000001B3ull + i;
            acc ^= 0xABCDEFull;
        } else {
            acc = acc * 0xFF51AFD7ED558CCDull + (i ^ 0x55ull);
            acc ^= acc >> 31;
        }
    }
    g_sink = acc;
    return acc;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_IntBranchWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed, jlong iterations) {
    uint64_t iters = (iterations > 0) ? (uint64_t)iterations : 50000000ull;
    uint64_t t0 = monotonic_nanos();
    int_branch_loop((uint64_t)seed, iters);
    uint64_t t1 = monotonic_nanos();
    jlong out[2] = { (jlong)iters, (jlong)(t1 - t0) };
    jlongArray r = env->NewLongArray(2);
    if (r != nullptr) env->SetLongArrayRegion(r, 0, 2, out);
    return r;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_IntBranchWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    uint64_t a = int_branch_loop(42ull, 10000ull);
    uint64_t b = int_branch_loop(42ull, 10000ull);
    return (a == b) ? JNI_TRUE : JNI_FALSE;
}
