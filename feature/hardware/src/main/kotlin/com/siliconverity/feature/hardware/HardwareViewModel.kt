package com.siliconverity.feature.hardware

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siliconverity.core.hardware.HardwareProvider
import com.siliconverity.core.model.HardwareFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HardwareUiState(
    val loading: Boolean = true,
    val facts: List<HardwareFact> = emptyList(),
    val error: String? = null,
)

class HardwareViewModel(application: Application) : AndroidViewModel(application) {

    private val provider = HardwareProvider(application)

    private val _state = MutableStateFlow(HardwareUiState())
    val state: StateFlow<HardwareUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { provider.collectAll() }
            _state.update {
                it.copy(
                    loading = false,
                    facts = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}
