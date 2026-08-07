#include <jni.h>
#include <cstdint>
#include <thread>
#include <barrier>
#include <vector>
#include <unistd.h>
#include "sv_cpu_internal.h"

// 多线程: N 个线程 (在线核心数) 各跑 int ALU mix。用 std::barrier 同步,
// 只计时并发工作区间 (排除线程创建与 join 开销)。
static uint64_t mt_work(uint64_t seed, uint64_t itersPerThread, int threads) {
    std::barrier sync(static_cast<ptrdiff_t>(threads + 1));
    std::vector<std::thread> ts;
    ts.reserve(threads);
    std::vector<uint64_t> accs(threads, 0);

    auto worker = [&, seed, itersPerThread](int idx) {
        sync.arrive_and_wait();  // phase 1: 全部就绪
        uint64_t a = seed ^ ((uint64_t)idx * 0x9E3779B97F4A7C15ull);
        for (uint64_t i = 0; i < itersPerThread; ++i) {
            a = a * 0x100000001B3ull ^ (i + 0xABCDEFull);
            a = (a << 7) | (a >> 57);
            a ^= a >> 31;
            a *= 0xFF51AFD7ED558CCDull;
            a ^= a >> 33;
        }
        accs[idx] = a;
        sync.arrive_and_wait();  // phase 2: 全部完成
    };

    for (int i = 0; i < threads; ++i) ts.emplace_back(worker, i);
    sync.arrive_and_wait();  // main phase 1: 全部就绪 -> 开始计时
    uint64_t t0 = monotonic_nanos();
    sync.arrive_and_wait();  // main phase 2: 等全部完成
    uint64_t t1 = monotonic_nanos();
    for (auto& t : ts) t.join();

    uint64_t sum = 0;
    for (auto x : accs) sum += x;
    g_sink = sum;
    return t1 - t0;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_MultithreadWorkload_nativeRunOnce(JNIEnv* env, jclass, jlong seed, jlong itersPerThread) {
    int threads = (int)sysconf(_SC_NPROCESSORS_ONLN);
    if (threads < 1) threads = 1;
    uint64_t ipt = (itersPerThread > 0) ? (uint64_t)itersPerThread : 8000000ull;
    uint64_t dur = mt_work((uint64_t)seed, ipt, threads);
    uint64_t totalOps = (uint64_t)threads * ipt;
    jlong out[2] = { (jlong)totalOps, (jlong)dur };
    jlongArray r = env->NewLongArray(2);
    if (r != nullptr) env->SetLongArrayRegion(r, 0, 2, out);
    return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_siliconverity_nativecpu_MultithreadWorkload_nativeThreadCount(JNIEnv*, jclass) {
    int t = (int)sysconf(_SC_NPROCESSORS_ONLN);
    return (jint)(t > 0 ? t : 1);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativecpu_MultithreadWorkload_nativeCorrectnessCheck(JNIEnv*, jclass) {
    mt_work(42ull, 1000ull, 2);
    uint64_t a = g_sink;
    mt_work(42ull, 1000ull, 2);
    return (a == g_sink) ? JNI_TRUE : JNI_FALSE;
}
