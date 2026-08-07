#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <ctime>

static volatile uint64_t lat_sink = 0;

static uint64_t lat_monotonic() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

// pointer chase: count = sizeBytes/64 个 cache line 槽, 每槽首 4 字节存下一槽索引
// (随机置换, 破硬件预取)。chase `accesses` 次, 返回 ns/access。
static double chase_latency_ns(jlong sizeBytes, jlong accesses) {
    const size_t STRIDE = 64;
    size_t count = (size_t)(sizeBytes / STRIDE);
    if (count < 2) count = 2;
    uint8_t* base = (uint8_t*)aligned_alloc(64, count * STRIDE);
    if (base == nullptr) return -1.0;

    uint32_t* perm = (uint32_t*)malloc(count * sizeof(uint32_t));
    if (perm == nullptr) { free(base); return -1.0; }
    for (size_t i = 0; i < count; i++) perm[i] = (uint32_t)i;
    srand(12345);
    for (size_t i = count - 1; i > 0; i--) {
        size_t j = (size_t)(rand() % (int)(i + 1));
        uint32_t t = perm[i]; perm[i] = perm[j]; perm[j] = t;
    }
    for (size_t i = 0; i < count; i++) {
        *(uint32_t*)(base + i * STRIDE) = perm[i];
    }
    free(perm);

    uint32_t cur = 0;
    uint64_t t0 = lat_monotonic();
    for (jlong a = 0; a < accesses; a++) {
        cur = *(uint32_t*)(base + (size_t)cur * STRIDE);
    }
    uint64_t t1 = lat_monotonic();
    lat_sink = cur;
    free(base);
    return (double)(t1 - t0) / (double)accesses;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_siliconverity_nativememory_MemoryLatencyBench_nativeRunLatency(JNIEnv*, jclass, jlong sizeBytes, jlong accesses) {
    return chase_latency_ns(sizeBytes, accesses);
}
