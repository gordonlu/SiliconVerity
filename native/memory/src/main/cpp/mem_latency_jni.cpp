#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <ctime>
#include <algorithm>
#include <vector>

static volatile uint64_t lat_sink = 0;

static uint64_t lat_monotonic() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

// 单环 pointer chase: 64B stride, perm 随机置换后令 slot[perm[i]] -> perm[(i+1)%count],
// 从 perm[0] 出发恰好遍历全部 count 槽后回到 perm[0] (单一环, 覆盖完整工作集)。
// 每 size 跑 `rounds` 轮取 median ns/access。
static double chase_median_ns(jlong sizeBytes, jlong accessesPerRound, int rounds) {
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
    // 单环: slot perm[i] 存 perm[(i+1)%count]
    for (size_t i = 0; i < count; i++) {
        *(uint32_t*)(base + (size_t)perm[i] * STRIDE) = perm[(i + 1) % count];
    }
    uint32_t start = perm[0];
    free(perm);
    uint32_t cur = start;

    std::vector<double> times;
    times.reserve(rounds);
    for (int r = 0; r < rounds; r++) {
        cur = start;
        uint64_t t0 = lat_monotonic();
        for (jlong a = 0; a < accessesPerRound; a++) {
            cur = *(uint32_t*)(base + (size_t)cur * STRIDE);
        }
        uint64_t t1 = lat_monotonic();
        times.push_back((double)(t1 - t0) / (double)accessesPerRound);
    }
    lat_sink = cur;  // 依赖循环结果, 防止 -O3 消除 chase 循环
    free(base);

    if (times.empty()) return -1.0;
    std::sort(times.begin(), times.end());
    size_t n = times.size();
    return (n % 2 == 1) ? times[n / 2] : (times[n / 2 - 1] + times[n / 2]) / 2.0;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_siliconverity_nativememory_MemoryLatencyBench_nativeRunLatency(JNIEnv*, jclass, jlong sizeBytes, jlong accesses, jint rounds) {
    return chase_median_ns(sizeBytes, accesses, rounds);
}
