#pragma once

#include <cstdint>
#include <ctime>

extern volatile uint64_t g_sink;

static inline uint64_t monotonic_nanos() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}
