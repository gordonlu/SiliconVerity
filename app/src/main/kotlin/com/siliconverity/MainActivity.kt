package com.siliconverity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.siliconverity.benchmark.BenchmarkController
import com.siliconverity.core.benchmark.BenchmarkUiState
import com.siliconverity.core.designsystem.SvTheme
import com.siliconverity.feature.hardware.HardwareScreen
import com.siliconverity.feature.hardware.HardwareViewModel
import com.siliconverity.feature.home.HomeScreen
import com.siliconverity.feature.home.ResultScreen
import com.siliconverity.feature.history.HistoryScreen
import com.siliconverity.feature.history.HistoryViewModel
import com.siliconverity.feature.history.BenchmarkRunDetailScreen
import com.siliconverity.feature.history.CompareScreen
import com.siliconverity.feature.history.CompareViewModel
import com.siliconverity.feature.history.RunDetailScreen
import com.siliconverity.feature.settings.SettingsScreen
import com.siliconverity.feature.sustained.SustainedScreen
import com.siliconverity.benchmark.SustainedController
import com.siliconverity.feature.gpu.GpuScreen
import com.siliconverity.benchmark.GpuController
import com.siliconverity.benchmark.LatencyController
import com.siliconverity.benchmark.LatencyScreen
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell()
                }
            }
        }
    }
}

@Composable
private fun AppShell() {
    val nav = rememberNavController()
    val hardwareVm: HardwareViewModel = viewModel()
    val benchmarkVm: BenchmarkController = viewModel()
    val hardwareState by hardwareVm.state.collectAsStateWithLifecycle()
    val benchmarkState by benchmarkVm.state.collectAsStateWithLifecycle()

    // 完成后自动进入结果页 (仅状态首次变为 Done 时)
    var lastDone by remember { mutableStateOf<BenchmarkUiState.Done?>(null) }
    val doneState = benchmarkState as? BenchmarkUiState.Done
    if (doneState != null && lastDone != doneState) {
        lastDone = doneState
        if (doneState.results.isNotEmpty() || doneState.error != null) {
            LaunchedEffect(doneState) {
                nav.navigate("benchmark-result") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.weight(1f),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("home") {
                val lastRun = (benchmarkState as? BenchmarkUiState.Done)
                    ?.results?.lastOrNull()?.manifest
                HomeScreen(
                    hardwareFacts = hardwareState.facts,
                    lastRun = lastRun,
                    benchmarkState = benchmarkState,
                    onStartBenchmark = { benchmarkVm.run() },
                    onStopBenchmark = { benchmarkVm.stop() },
                    onOpenHardware = { nav.navigate("hardware") },
                    onOpenSustained = { nav.navigate("sustained") },
                    onOpenGpu = { nav.navigate("gpu") },
                    onOpenLatency = { nav.navigate("latency") },
                    onOpenRun = { runId -> nav.navigate("run/$runId") },
                )
            }
            composable("benchmark-result") {
                val done = benchmarkState as? BenchmarkUiState.Done
                if (done == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                val context = LocalContext.current
                ResultScreen(
                    done = done,
                    hardwareFacts = hardwareState.facts,
                    onRunAgain = {
                        nav.popBackStack()
                        benchmarkVm.run()
                    },
                    onHistory = {
                        nav.navigate("history") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onShare = {
                        val score = done.score
                        val text = if (score != null) {
                            context.getString(
                                com.siliconverity.feature.home.R.string.result_share_text,
                                score.overallScore ?: 0,
                                (score.cpuScore ?: 0) * 10,
                                (score.gpuScore ?: 0) * 10,
                                (score.memoryScore ?: 0) * 10,
                                (score.appIoScore ?: 0) * 10,
                                score.scoreVersion,
                            )
                        } else {
                            done.error ?: "SiliconVerity"
                        }
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, null))
                        }
                    },
                )
            }
            composable("hardware") {
                HardwareScreen(
                    hardwareState = hardwareState,
                    onRefresh = { hardwareVm.load() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("sustained") {
                val sustainedVm: SustainedController = viewModel()
                val sustainedState by sustainedVm.state.collectAsStateWithLifecycle()
                SustainedScreen(
                    state = sustainedState,
                    onStart = { durationSec -> sustainedVm.start(durationSec) },
                    onStop = { sustainedVm.stop() },
                    onReset = { sustainedVm.reset() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("gpu") {
                val gpuVm: GpuController = viewModel()
                val gpuState by gpuVm.state.collectAsStateWithLifecycle()
                GpuScreen(
                    state = gpuState,
                    onRun = { gpuVm.run() },
                    onStop = { gpuVm.stop() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("latency") {
                val latencyVm: LatencyController = viewModel()
                val latencyState by latencyVm.state.collectAsStateWithLifecycle()
                LatencyScreen(
                    state = latencyState,
                    onRun = { latencyVm.run() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("history") {
                val historyVm: HistoryViewModel = viewModel()
                val historyState by historyVm.state.collectAsStateWithLifecycle()
                HistoryScreen(
                    historyState,
                    onOpenRun = { runId -> nav.navigate("brun/$runId") },
                    onClear = { historyVm.clear() },
                    onCompare = { nav.navigate("compare") },
                )
            }
            composable(
                route = "brun/{runId}",
                arguments = listOf(navArgument("runId") { type = NavType.StringType }),
            ) { entry ->
                val runId = entry.arguments?.getString("runId") ?: ""
                BenchmarkRunDetailScreen(runId = runId, onBack = { nav.popBackStack() })
            }
            composable("compare") {
                val compareVm: CompareViewModel = viewModel()
                CompareScreen(vm = compareVm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen()
            }
            composable(
                route = "run/{runId}",
                arguments = listOf(navArgument("runId") { type = NavType.StringType }),
            ) { entry ->
                val runId = entry.arguments?.getString("runId") ?: ""
                val context = LocalContext.current
                val runsDir = File(context.applicationContext.filesDir, "runs")
                RunDetailScreen(runId, runsDir, onBack = { nav.popBackStack() })
            }
        }
        SvBottomBar(nav)
    }
}

@Composable
private fun SvBottomBar(nav: NavController) {
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    val items = listOf(
        Triple("home", stringResource(R.string.nav_home), Icons.Filled.Home),
        Triple("history", stringResource(R.string.nav_history), Icons.Filled.Info),
        Triple("settings", stringResource(R.string.nav_settings), Icons.Filled.Settings),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (route, label, icon) ->
            val selected = currentRoute == route ||
                (route == "home" && currentRoute == null)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
