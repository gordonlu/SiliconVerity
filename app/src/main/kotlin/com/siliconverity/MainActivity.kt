package com.siliconverity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.siliconverity.core.designsystem.SvTheme
import com.siliconverity.feature.hardware.HardwareScreen
import com.siliconverity.feature.hardware.HardwareViewModel
import com.siliconverity.feature.history.HistoryScreen
import com.siliconverity.feature.history.HistoryViewModel
import com.siliconverity.feature.history.RunDetailScreen
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

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.weight(1f),
        ) {
            composable("home") {
                HardwareScreen(
                    hardwareState = hardwareState,
                    benchmarkState = benchmarkState,
                    onRefresh = { hardwareVm.load() },
                    onRunBenchmark = { benchmarkVm.run() },
                )
            }
            composable("history") {
                val historyVm: HistoryViewModel = viewModel()
                val historyState by historyVm.state.collectAsStateWithLifecycle()
                HistoryScreen(historyState, onOpenRun = { runId -> nav.navigate("run/$runId") })
            }
            composable("settings") {
                SettingsPlaceholder()
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
        Triple("home", "HOME", Icons.Filled.Home),
        Triple("history", "HISTORY", Icons.Filled.Info),
        Triple("settings", "SETTINGS", Icons.Filled.Settings),
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

@Composable
private fun SettingsPlaceholder() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("SETTINGS", style = MaterialTheme.typography.titleLarge)
        Text(
            "设置页待实现（省电模式检测、性能模式、导出、关于、隐私等）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
