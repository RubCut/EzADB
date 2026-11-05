package dev.ezadb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ezadb.ui.screens.AppsScreen
import dev.ezadb.ui.screens.DashboardScreen
import dev.ezadb.ui.screens.FilesScreen
import dev.ezadb.ui.screens.SettingsScreen
import dev.ezadb.ui.screens.TerminalScreen
import dev.ezadb.ui.viewmodel.EzViewModel

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("dashboard", "Обзор", Icons.Filled.Dashboard),
    NavItem("terminal", "Терминал", Icons.Filled.Terminal),
    NavItem("apps", "Приложения", Icons.Filled.Apps),
    NavItem("files", "Файлы", Icons.Filled.Folder),
    NavItem("settings", "Настройки", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EzApp(vm: EzViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConnect by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.consumeMessage()
        }
    }

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints {
        val wide = maxWidth >= 720.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(navItems.firstOrNull { it.route == currentRoute }?.label?.let { if (it == "Обзор") "EzADB" else it } ?: "EzADB")
                            if (state.demo) Badge(modifier = Modifier.padding(start = 8.dp)) { Text("демо") }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showConnect = true }) {
                            Icon(if (state.connected) Icons.Filled.Link else Icons.Filled.Cable, "Подключение")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { navigate(item.route) },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding)) {
                if (wide) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigate(item.route) },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    NavHost(navController, startDestination = "dashboard", modifier = Modifier.fillMaxHeight()) {
                        composable("dashboard") { DashboardScreen(vm, state, onConnect = { showConnect = true }) }
                        composable("terminal") { TerminalScreen(vm, state) }
                        composable("apps") { AppsScreen(vm, state) }
                        composable("files") { FilesScreen(vm, state) }
                        composable("settings") { SettingsScreen(vm, state) }
                    }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }

    if (showConnect) {
        ConnectSheet(vm, state, onDismiss = { showConnect = false })
    }
}
