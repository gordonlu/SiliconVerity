#include <jni.h>
#include "sv_cpu_internal.h"

volatile uint64_t g_sink = 0;

static inline uint64_t rotl64(uint64_t x, int r) {
    return (x << r) | (x >> (64 - r));
}

// 单核标量整数 ILP: 8 条独立链 (rotl/mul/xor), 链内递推、链间互不依赖,
// 靠乱序执行 + 多发射并行。workUnits = iterations * 8 (chain-updates)。
// 编译: -fno-vectorize -fno-slp-vectorize (保持标量, 防向量化)。
static uint64_t int_alu_ilp_loop(uint64_t seed, uint64_t iterations) {
    uint64_t a0 = seed ^ 0x9E3779B97F4A7C15ull;
    uint64_t a1 = seed ^ 0xD1B54A32D192ED03ull;
    uint64_t a2 = seed ^ 0x94D049BB133111EBull;
    uint64_t a3 = seed ^ 0xBF58476D1CE4E5B9ull;
    uint64_t a4 = seed ^ 0xDB4F0B9175AE2165ull;
    uint64_t a5 = seed ^ 0xBBE0563303A4615Full;
    uint64_t a6 = seed ^ 0xA24BAED4963EE407ull;
    uint64_t a7 = seed ^ 0x9FB21C651E98DF25ull;
    for (uint64_t i = 0; i < iterations; ++i) {
        a0 = rotl64(a0 * 0x9E3779B97F4A7C15ull + i, 7)  ^ (a0 >> 29);
        a1 = rotl64(a1 * 0xD1B54A32D192ED03ull + i, 11) ^ (a1 >> 31);
        a2 = rotl64(a2 * 0x94D049BB133111EBull + i, 13) ^ (a2 >> 27);
        a3 = rotl64(a3 * 0xBF58476D1CE4E5B9ull + i, 17) ^ (a3 >> 33);
        a4 = rotl64(a4 * 0xDB4F0B9175AE2165ull + i, 19) ^ (a4 >> 25);
        a5 = rotl64(a5 * 0xBBE0563303A4615Full + i, 23) ^ (a5 >> 35);
        a6 = rotl64(a6 * 0xA24BAED4963EE407ull + i, 29) ^ (a6 >> 21);
        a7 = rotl64(a7 * 0x9FB21C651E98DF25ull + i, 31) ^ (a7 >> 37);
    }
    uint64_t result = a0 ^ a1 ^ a2 ^ a3 ^ a4 ^ a5 ^ a6 ^ a7;
    g_sink = result;
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeProbe(JNIEnv* env, jclass) {
    return env->NewStringUTF("sv_cpu_int:r29:c++20:ilp8");
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed, jlong iterations) {
    uint64_t iters = (iterations > 0) ? (uint64_t)iterations : 50000000ull;
    uint64_t t0 = monotonic_nanos();
    int_alu_ilp_loop((uint64_t)seed, iters);
    uint64_t t1 = monotonic_nanos();
    jlong out[2] = { (jlong)(iters * 8ull), (jlong)(t1 - t0) };
    jlongArray result = env->NewLongArray(2);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 2, out);
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_CpuIntegerWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    constexpr uint64_t GOLDEN = 0x3f5422e1f7b205c8ull;
    return int_alu_ilp_loop(42ull, 10000ull) == GOLDEN ? JNI_TRUE : JNI_FALSE;
}
