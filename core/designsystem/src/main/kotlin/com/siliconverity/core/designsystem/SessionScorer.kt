package com.siliconverity.core.designsystem

import android.content.Context
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ScorePack
import com.siliconverity.core.benchmark.ScorePackLoader
import com.siliconverity.core.benchmark.ScoreReport
import com.siliconverity.core.benchmark.ScoringEngine

/**
 * 会话级评分: 从 assets 加载评分包, 对一组运行计算 ScoreReport。
 * 首页"最近一次"与历史页会话聚合共用。
 */
object SessionScorer {

    @Volatile
    private var pack: ScorePack? = null

    fun score(context: Context, runs: List<BenchmarkRun>): ScoreReport? {
        val p = pack ?: synchronized(this) {
            pack ?: loadPack(context).also { pack = it }
        } ?: return null
        return runCatching { ScoringEngine(p).score(runs) }.getOrNull()
    }

    private fun loadPack(context: Context): ScorePack? = runCatching {
        val stream = context.assets.open("scorepacks/svs-1.0.json")
        val text = stream.bufferedReader().use { it.readText() }
        ScorePackLoader.parseJson(text)
    }.getOrNull()
}
