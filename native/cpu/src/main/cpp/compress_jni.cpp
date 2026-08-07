#include <jni.h>
#include <cstdint>
#include <new>
#include "sv_cpu_internal.h"

// 混合 (int + memory): 对 256KB 缓冲区做 rolling hash, 模拟压缩/混合场景。
// 256KB 通常落在 LLC, 故为 int+cache 混合, 非纯内存带宽。
static uint8_t* g_cbuf = nullptr;
static size_t g_clen = 0;

static bool ensure_cbuf(size_t bytes) {
    if (g_cbuf != nullptr && g_clen == bytes) return true;
    delete[] g_cbuf;
    g_cbuf = new (std::nothrow) uint8_t[bytes];
    if (g_cbuf == nullptr) { g_clen = 0; return false; }
    g_clen = bytes;
    for (size_t i = 0; i < bytes; i++) g_cbuf[i] = (uint8_t)(i * 131u + 7u);
    return true;
}

static uint64_t hash_loop(uint64_t seed, uint64_t iterations) {
    const size_t N = 256 * 1024;
    if (!ensure_cbuf(N)) return 0;
    uint64_t h = seed;
    const uint8_t* p = g_cbuf;
    for (uint64_t it = 0; it < iterations; ++it) {
        for (size_t i = 0; i < N; i++) {
            h = h * 131ull + p[i];
        }
        h ^= h >> 27;
    }
    g_sink = h;
    return h;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_CompressionWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed) {
    constexpr uint64_t ITERATIONS = 200ull;
    const uint64_t N = 256ull * 1024ull;
    uint64_t t0 = monotonic_nanos();
    hash_loop((uint64_t)seed, ITERATIONS);
    uint64_t t1 = monotonic_nanos();
    jlong out[2] = { (jlong)(ITERATIONS * N), (jlong)(t1 - t0) }; // workUnits = bytes processed
    jlongArray r = env->NewLongArray(2);
    if (r != nullptr) env->SetLongArrayRegion(r, 0, 2, out);
    return r;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_CompressionWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    uint64_t a = hash_loop(7ull, 5ull);
    uint64_t b = hash_loop(7ull, 5ull);
    return (a == b) ? JNI_TRUE : JNI_FALSE;
}
