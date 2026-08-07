package com.siliconverity.benchmark

import kotlinx.coroutines.sync.Mutex

/**
 * 全局运行协调器: 同一时间只允许一个主动 Benchmark 会话。
 * 防止并发 native 调用 (静态 Vulkan Harness / 全局 buffer / 全局 sink 无线程安全保护)。
 */
object BenchmarkRunCoordinator {
    private val mutex = Mutex()

    /** 尝试获取; 已有会话运行则返回 false。 */
    fun tryAcquire(): Boolean = mutex.tryLock()

    fun release() {
        runCatching { if (mutex.isLocked) mutex.unlock() }
    }

    val busy: Boolean get() = mutex.isLocked
}
