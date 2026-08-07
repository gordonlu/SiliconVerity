package com.siliconverity.core.designsystem

import com.siliconverity.core.benchmark.BenchmarkCategory

/**
 * workloadId -> 展示层信息 (分类/显示名/技术描述)。
 * 显示名与描述为技术术语, 中英一致; 分类为进度/结果页分组。
 */
object SvWorkloads {

    fun category(workloadId: String): BenchmarkCategory = when {
        workloadId.startsWith("cpu.") -> BenchmarkCategory.CPU
        workloadId.startsWith("mem.") -> BenchmarkCategory.MEMORY
        workloadId.startsWith("vulkan.") -> BenchmarkCategory.GPU
        workloadId.startsWith("storage.") -> BenchmarkCategory.APP_IO
        else -> BenchmarkCategory.MEMORY
    }

    fun nameRes(workloadId: String): Int = when (workloadId) {
        "cpu.int.ilp" -> R.string.wl_cpu_ilp
        "cpu.fp32.fma" -> R.string.wl_cpu_fp32
        "cpu.int.branch" -> R.string.wl_cpu_branch
        "cpu.hash.cached" -> R.string.wl_cpu_hash
        "cpu.multithread" -> R.string.wl_cpu_mt
        "mem.bandwidth.read" -> R.string.wl_mem_read
        "mem.bandwidth.copy" -> R.string.wl_mem_copy
        "mem.latency.curve" -> R.string.wl_mem_latency
        "vulkan.fp32.independent" -> R.string.wl_gpu_fp32_par
        "vulkan.fp32.dependency" -> R.string.wl_gpu_fp32_dep
        "vulkan.buffer.throughput" -> R.string.wl_gpu_buffer
        "storage.seq_write.buffered" -> R.string.wl_io_buffered
        "storage.seq_write.durable" -> R.string.wl_io_durable
        "storage.random_write.fsync" -> R.string.wl_io_fsync
        "storage.seq_read.warm" -> R.string.wl_io_read
        else -> R.string.wl_unknown
    }

    fun descRes(workloadId: String): Int = when (workloadId) {
        "cpu.int.ilp" -> R.string.wl_cpu_ilp_desc
        "cpu.fp32.fma" -> R.string.wl_cpu_fp32_desc
        "cpu.int.branch" -> R.string.wl_cpu_branch_desc
        "cpu.hash.cached" -> R.string.wl_cpu_hash_desc
        "cpu.multithread" -> R.string.wl_cpu_mt_desc
        "mem.bandwidth.read" -> R.string.wl_mem_read_desc
        "mem.bandwidth.copy" -> R.string.wl_mem_copy_desc
        "mem.latency.curve" -> R.string.wl_mem_latency_desc
        "vulkan.fp32.independent" -> R.string.wl_gpu_fp32_par_desc
        "vulkan.fp32.dependency" -> R.string.wl_gpu_fp32_dep_desc
        "vulkan.buffer.throughput" -> R.string.wl_gpu_buffer_desc
        "storage.seq_write.buffered" -> R.string.wl_io_buffered_desc
        "storage.seq_write.durable" -> R.string.wl_io_durable_desc
        "storage.random_write.fsync" -> R.string.wl_io_fsync_desc
        "storage.seq_read.warm" -> R.string.wl_io_read_desc
        else -> R.string.wl_unknown_desc
    }

    fun categoryNameRes(category: BenchmarkCategory): Int = when (category) {
        BenchmarkCategory.CPU -> R.string.wl_cat_cpu
        BenchmarkCategory.MEMORY -> R.string.wl_cat_memory
        BenchmarkCategory.GPU -> R.string.wl_cat_gpu
        BenchmarkCategory.APP_IO -> R.string.wl_cat_io
    }

    /** 分类内项目数 (套件固定 15 项: 5+3+3+4)。 */
    fun categorySize(category: BenchmarkCategory): Int = when (category) {
        BenchmarkCategory.CPU -> 5
        BenchmarkCategory.MEMORY -> 3
        BenchmarkCategory.GPU -> 3
        BenchmarkCategory.APP_IO -> 4
    }

    /** 分类在套件中的起始序号 (1-based)。 */
    fun categoryStartIndex(category: BenchmarkCategory): Int = when (category) {
        BenchmarkCategory.CPU -> 1
        BenchmarkCategory.MEMORY -> 6
        BenchmarkCategory.GPU -> 9
        BenchmarkCategory.APP_IO -> 12
    }
}
