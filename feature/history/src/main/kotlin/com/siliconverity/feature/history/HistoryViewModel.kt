package com.siliconverity.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ScoreReport
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.designsystem.SessionScorer
import com.siliconverity.core.storage.BenchmarkRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 会话聚合: 一组运行 + 会话级评分 + 有效性统计。 */
data class SessionAggregate(
    val id: String,
    val startedAt: String,
    val runs: List<BenchmarkRun>,
    val score: ScoreReport?,
    val stableCount: Int,
    val variableCount: Int,
    val total: Int,
)

/** 按 sessionId 分组 (latency 等独立运行归入自身)。 */
internal fun groupRunsBySession(runs: List<BenchmarkRun>): List<Pair<String, List<BenchmarkRun>>> {
    val groups = LinkedHashMap<String, MutableList<BenchmarkRun>>()
    for (run in runs) {
        val key = run.identity.sessionId.ifEmpty { run.identity.runId }
        groups.getOrPut(key) { mutableListOf() }.add(run)
    }
    return groups.map { (id, list) -> id to list }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val runs: List<BenchmarkRun> = emptyList(),
    val sessions: List<SessionAggregate> = emptyList(),
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BenchmarkRunStore(application.filesDir)

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val runs = runCatching { store.list() }.getOrDefault(emptyList())
            val sessions = groupSessions(runs).map { (id, groupRuns) ->
                val sorted = groupRuns.sortedBy { it.startedAt }
                val scalarRuns = sorted.filter { it.identity.workloadId != "mem.latency.curve" }
                val score = SessionScorer.score(getApplication(), scalarRuns)
                SessionAggregate(
                    id = id,
                    startedAt = sorted.first().startedAt,
                    runs = sorted,
                    score = score,
                    stableCount = sorted.count { it.validity.stability == ValidityLevel.STABLE },
                    variableCount = sorted.count { it.validity.stability == ValidityLevel.VARIABLE },
                    total = sorted.size,
                )
            }
            _state.value = HistoryUiState(loading = false, runs = runs, sessions = sessions)
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.clear() }
            _state.value = HistoryUiState(loading = false)
        }
    }

    private fun groupSessions(runs: List<BenchmarkRun>): List<Pair<String, List<BenchmarkRun>>> =
        groupRunsBySession(runs)

    private fun groupRunsBySession(runs: List<BenchmarkRun>): List<Pair<String, List<BenchmarkRun>>> {
        val groups = LinkedHashMap<String, MutableList<BenchmarkRun>>()
        for (run in runs) {
            val key = run.identity.sessionId.ifEmpty { run.identity.runId }
            groups.getOrPut(key) { mutableListOf() }.add(run)
        }
        return groups.map { (id, list) -> id to list }
    }
}
