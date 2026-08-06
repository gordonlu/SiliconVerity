package com.siliconverity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siliconverity.benchmark.BenchmarkController
import com.siliconverity.feature.hardware.HardwareScreen
import com.siliconverity.feature.hardware.HardwareViewModel
import com.siliconverity.ui.theme.SiliconVerityTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SiliconVerityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val hardwareVm: HardwareViewModel = viewModel()
                    val benchmarkVm: BenchmarkController = viewModel()
                    val hardwareState by hardwareVm.state.collectAsState()
                    val benchmarkState by benchmarkVm.state.collectAsState()

                    HardwareScreen(
                        hardwareState = hardwareState,
                        benchmarkState = benchmarkState,
                        onRefresh = { hardwareVm.load() },
                        onRunBenchmark = { benchmarkVm.run() },
                    )
                }
            }
        }
    }
}
