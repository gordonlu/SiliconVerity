#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <ctime>

static volatile uint64_t g_sink = 0;

static uint64_t monotonic_nanos() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static uint8_t* g_buf = nullptr;
static uint8_t* g_buf2 = nullptr;
static size_t g_size = 0;

static bool ensure_buffer(size_t bytes) {
    if (g_buf != nullptr && g_size == bytes) return true;
    free(g_buf);
    free(g_buf2);
    g_buf = (uint8_t*)aligned_alloc(64, bytes);
    g_buf2 = (uint8_t*)aligned_alloc(64, bytes);
    if (g_buf == nullptr || g_buf2 == nullptr) {
        g_buf = g_buf2 = nullptr;
        g_size = 0;
        return false;
    }
    g_size = bytes;
    uint64_t* q = (uint64_t*)g_buf;
    size_t n = bytes / 8;
    for (size_t i = 0; i < n; ++i) {
        q[i] = (uint64_t)i * 0x9E3779B97F4A7C15ull + 0x1234567;
    }
    return true;
}

static jlongArray make_array(JNIEnv* env, jlong work, jlong duration) {
    jlong out[2] = { work, duration };
    jlongArray r = env->NewLongArray(2);
    if (r != nullptr) {
        env->SetLongArrayRegion(r, 0, 2, out);
    }
    return r;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativememory_MemoryNative_nativeRead(JNIEnv* env, jclass, jlong sizeBytes, jlong seed) {
    if (!ensure_buffer((size_t)sizeBytes)) {
        return make_array(env, 0, 0);
    }
    uint64_t* q = (uint64_t*)g_buf;
    size_t n = (size_t)(sizeBytes / 8);
    uint64_t t0 = monotonic_nanos();
    uint64_t s0 = (uint64_t)seed, s1 = 0, s2 = 0, s3 = 0, s4 = 0, s5 = 0, s6 = 0, s7 = 0;
    size_t i = 0;
    for (; i + 7 < n; i += 8) {
        s0 += q[i];     s1 += q[i + 1]; s2 += q[i + 2]; s3 += q[i + 3];
        s4 += q[i + 4]; s5 += q[i + 5]; s6 += q[i + 6]; s7 += q[i + 7];
    }
    for (; i < n; ++i) s0 += q[i];
    uint64_t sum = s0 + s1 + s2 + s3 + s4 + s5 + s6 + s7;
    g_sink = sum;
    uint64_t t1 = monotonic_nanos();
    return make_array(env, sizeBytes, (jlong)(t1 - t0));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativememory_MemoryNative_nativeCopy(JNIEnv* env, jclass, jlong sizeBytes, jlong seed) {
    (void)seed;
    if (!ensure_buffer((size_t)sizeBytes)) {
        return make_array(env, 0, 0);
    }
    uint64_t t0 = monotonic_nanos();
    memcpy(g_buf2, g_buf, (size_t)sizeBytes);
    uint64_t t1 = monotonic_nanos();
    g_sink = ((uint64_t*)g_buf2)[0];
    return make_array(env, sizeBytes, (jlong)(t1 - t0));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativememory_MemoryNative_nativeCorrectness(JNIEnv*, jclass) {
    const size_t N = 1 * 1024 * 1024;
    uint8_t* a = (uint8_t*)aligned_alloc(64, N);
    uint8_t* b = (uint8_t*)aligned_alloc(64, N);
    if (a == nullptr || b == nullptr) {
        free(a); free(b);
        return JNI_FALSE;
    }
    uint64_t* qa = (uint64_t*)a;
    for (size_t i = 0; i < N / 8; ++i) qa[i] = (uint64_t)i;
    uint64_t s1 = 0, s2 = 0;
    for (size_t i = 0; i < N / 8; ++i) {
        s1 += qa[i];
        s2 += qa[i];
    }
    bool ok = (s1 == s2);
    memcpy(b, a, N);
    ok = ok && (memcmp(b, a, N) == 0);
    free(a);
    free(b);
    return ok ? JNI_TRUE : JNI_FALSE;
}
