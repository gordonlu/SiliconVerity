package com.siliconverity.core.benchmark

interface Workload {
    val spec: BenchmarkSpec

    fun warmUp()

    fun runOnce(): Sample

    fun correctnessCheck(): Boolean
}
