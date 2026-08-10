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
enum class LastOperation { NONE, READ, COPY };
static LastOperation g_last_operation = LastOperation::NONE;
static uint64_t g_last_read_checksum = 0;
static uint64_t g_last_read_seed = 0;
static uint32_t g_last_read_repeats = 0;

static bool ensure_buffer(size_t bytes) {
    if (g_buf != nullptr && g_size == bytes) return true;
    free(g_buf);
    free(g_buf2);
    uint8_t* next = (uint8_t*)aligned_alloc(64, bytes);
    uint8_t* next2 = (uint8_t*)aligned_alloc(64, bytes);
    if (next == nullptr || next2 == nullptr) {
        free(next);
        free(next2);
        g_buf = nullptr;
        g_buf2 = nullptr;
        g_size = 0;
        return false;
    }
    g_buf = next;
    g_buf2 = next2;
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
Java_com_siliconverity_nativememory_MemoryNative_nativeRead(JNIEnv* env, jclass, jlong sizeBytes, jlong seed, jint repeats) {
    if (!ensure_buffer((size_t)sizeBytes)) {
        return make_array(env, 0, 0);
    }
    uint64_t* q = (uint64_t*)g_buf;
    size_t n = (size_t)(sizeBytes / 8);
    uint32_t reps = repeats > 0 ? (uint32_t)repeats : 1u;
    uint64_t combined = 0;
    uint64_t t0 = monotonic_nanos();
    for (uint32_t rep = 0; rep < reps; ++rep) {
        uint64_t s0 = (uint64_t)seed + rep, s1 = 0, s2 = 0, s3 = 0, s4 = 0, s5 = 0, s6 = 0, s7 = 0;
        size_t i = 0;
        for (; i + 7 < n; i += 8) {
            s0 += q[i];     s1 += q[i + 1]; s2 += q[i + 2]; s3 += q[i + 3];
            s4 += q[i + 4]; s5 += q[i + 5]; s6 += q[i + 6]; s7 += q[i + 7];
        }
        for (; i < n; ++i) s0 += q[i];
        combined ^= s0 + s1 + s2 + s3 + s4 + s5 + s6 + s7;
    }
    g_sink = combined;
    uint64_t t1 = monotonic_nanos();
    g_last_operation = LastOperation::READ;
    g_last_read_checksum = combined;
    g_last_read_seed = (uint64_t)seed;
    g_last_read_repeats = reps;
    return make_array(env, sizeBytes * (jlong)reps, (jlong)(t1 - t0));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativememory_MemoryNative_nativeCopy(JNIEnv* env, jclass, jlong sizeBytes, jlong seed, jint repeats) {
    (void)seed;
    if (!ensure_buffer((size_t)sizeBytes)) {
        return make_array(env, 0, 0);
    }
    uint32_t reps = repeats > 0 ? (uint32_t)repeats : 1u;
    uint64_t t0 = monotonic_nanos();
    for (uint32_t rep = 0; rep < reps; ++rep) memcpy(g_buf2, g_buf, (size_t)sizeBytes);
    uint64_t t1 = monotonic_nanos();
    g_sink = ((uint64_t*)g_buf2)[0];
    g_last_operation = LastOperation::COPY;
    return make_array(env, sizeBytes * (jlong)reps, (jlong)(t1 - t0));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siliconverity_nativememory_MemoryNative_nativeCorrectness(JNIEnv*, jclass, jlong sizeBytes) {
    if (g_buf == nullptr || g_buf2 == nullptr || g_size != (size_t)sizeBytes) return JNI_FALSE;
    if (g_last_operation == LastOperation::COPY) {
        return memcmp(g_buf, g_buf2, g_size) == 0 ? JNI_TRUE : JNI_FALSE;
    }
    if (g_last_operation != LastOperation::READ) return JNI_FALSE;
    uint64_t bufferSum = 0;
    const size_t n = g_size / sizeof(uint64_t);
    for (size_t i = 0; i < n; ++i) {
        bufferSum += (uint64_t)i * 0x9E3779B97F4A7C15ull + 0x1234567ull;
    }
    uint64_t expected = 0;
    for (uint32_t rep = 0; rep < g_last_read_repeats; ++rep) {
        expected ^= bufferSum + g_last_read_seed + rep;
    }
    return g_last_read_checksum == expected ? JNI_TRUE : JNI_FALSE;
}
