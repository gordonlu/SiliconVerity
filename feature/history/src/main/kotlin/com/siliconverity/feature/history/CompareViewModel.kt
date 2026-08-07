package com.siliconverity.feature.history

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.benchmark.BenchmarkRun
import com.siliconverity.core.benchmark.ValidityLevel
import com.siliconverity.core.designsystem.SessionScorer
import com.siliconverity.core.storage.BenchmarkRunStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val store = BenchmarkRunStore(application.filesDir)

    private val _sessions = MutableStateFlow<List<SessionAggregate>>(emptyList())
    val sessions: StateFlow<List<SessionAggregate>> = _sessions.asStateFlow()

    /** 已选会话 id (A/B 最多 2 个)。 */
    val selected = mutableStateListOf<String>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val runs = runCatching { store.list() }.getOrDefault(emptyList())
            val sessions = groupRunsBySession(runs).map { (id, groupRuns) ->
                val sorted = groupRuns.sortedBy { it.startedAt }
                val scalarRuns = sorted.filter { it.identity.workloadId != "mem.latency.curve" }
                SessionAggregate(
                    id = id,
                    startedAt = sorted.first().startedAt,
                    runs = sorted,
                    score = SessionScorer.score(getApplication(), scalarRuns),
                    stableCount = sorted.count { it.validity.stability == ValidityLevel.STABLE },
                    variableCount = sorted.count { it.validity.stability == ValidityLevel.VARIABLE },
                    total = sorted.size,
                    gpuStatus = com.siliconverity.core.designsystem.GpuStatusDetector.display(getApplication(), sorted),
                )
            }
            _sessions.value = sessions
        }
    }

    fun toggle(sessionId: String) {
        when {
            selected.contains(sessionId) -> selected.remove(sessionId)
            selected.size < 2 -> selected.add(sessionId)
        }
    }

    fun clear() {
        selected.clear()
    }
}
