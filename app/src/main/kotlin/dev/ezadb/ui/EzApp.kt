package dev.ezadb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ezadb.R
import dev.ezadb.ui.components.EzNavItem
import dev.ezadb.ui.components.EzTopBar
import dev.ezadb.ui.components.ExpressiveWaveProgress
import dev.ezadb.ui.components.FloatingNavBar
import dev.ezadb.ui.components.FloatingNavRail
import dev.ezadb.ui.intro.IntroScreen
import dev.ezadb.ui.screens.AppsScreen
import dev.ezadb.ui.screens.DashboardScreen
import dev.ezadb.ui.screens.FilesScreen
import dev.ezadb.ui.screens.SettingsScreen
import dev.ezadb.ui.screens.TerminalScreen
import dev.ezadb.ui.theme.EzMotionSpecs
import dev.ezadb.ui.viewmodel.EzViewModel

@Composable
fun EzApp(vm: EzViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var showConnect by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"

    val navItems = listOf(
        EzNavItem("dashboard", stringResource(R.string.overview), Icons.Filled.Dashboard),
        EzNavItem("terminal", stringResource(R.string.terminal), Icons.Filled.Terminal),
        EzNavItem("apps", stringResource(R.string.apps), Icons.Filled.Apps),
        EzNavItem("files", stringResource(R.string.files), Icons.Filled.Folder),
        EzNavItem("settings", stringResource(R.string.settings), Icons.Filled.Settings),
    )

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.consumeMessage()
        }
    }

    fun navigate(route: String) {
        if (state.prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Crossfade(targetState = state.prefs.introSeen, animationSpec = EzMotionSpecs.spatial(), label = "introCrossfade") { seen ->
        if (!seen) {
            IntroScreen(onFinish = { vm.setIntroSeen(true) })
        } else {
            BoxWithConstraints {
                val wide = maxWidth >= 720.dp
                val title = if (currentRoute == "dashboard") stringResource(R.string.app_name) else navItems.firstOrNull { it.route == currentRoute }?.label ?: stringResource(R.string.app_name)
                Scaffold(
                    topBar = {
                        EzTopBar(
                            title = title,
                            subtitle = state.device?.model ?: stringResource(R.string.connect_to_device),
                            connected = state.connected,
                            demo = state.demo,
                            onConnect = { showConnect = true },
                            connectContentDescription = stringResource(R.string.connect),
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!wide) {
                            FloatingNavBar(items = navItems, currentRoute = currentRoute, onNavigate = ::navigate)
                        }
                    },
                ) { padding ->
                    Row(Modifier.padding(padding)) {
                        if (wide) {
                            FloatingNavRail(items = navItems, currentRoute = currentRoute, onNavigate = ::navigate)
                        }
                        Box(Modifier.weight(1f)) {
                            NavHost(
                                navController,
                                startDestination = "dashboard",
                                modifier = Modifier.fillMaxHeight(),
                                enterTransition = {
                                    fadeIn(EzMotionSpecs.fade()) +
                                        slideInHorizontally(EzMotionSpecs.spatial()) { it / 6 } +
                                        scaleIn(EzMotionSpecs.spatial(), initialScale = 0.96f)
                                },
                                exitTransition = {
                                    fadeOut(EzMotionSpecs.fade()) +
                                        slideOutHorizontally(EzMotionSpecs.spatial()) { -it / 6 } +
                                        scaleOut(EzMotionSpecs.spatial(), targetScale = 0.96f)
                                },
                                popEnterTransition = {
                                    fadeIn(EzMotionSpecs.fade()) +
                                        slideInHorizontally(EzMotionSpecs.spatial()) { -it / 6 } +
                                        scaleIn(EzMotionSpecs.spatial(), initialScale = 0.96f)
                                },
                                popExitTransition = {
                                    fadeOut(EzMotionSpecs.fade()) +
                                        slideOutHorizontally(EzMotionSpecs.spatial()) { it / 6 } +
                                        scaleOut(EzMotionSpecs.spatial(), targetScale = 0.96f)
                                },
                            ) {
                                composable("dashboard") { DashboardScreen(vm, state, onConnect = { showConnect = true }) }
                                composable("terminal") { TerminalScreen(vm, state) }
                                composable("apps") { AppsScreen(vm, state) }
                                composable("files") { FilesScreen(vm, state) }
                                composable("settings") { SettingsScreen(vm, state) }
                            }
                            AnimatedVisibility(
                                visible = state.busy,
                                modifier = Modifier.align(Alignment.TopCenter),
                                enter = fadeIn(EzMotionSpecs.fade()) + expandVertically(EzMotionSpecs.fastSpatial()),
                                exit = fadeOut(EzMotionSpecs.fade()) + shrinkVertically(EzMotionSpecs.fastSpatial()),
                            ) {
                                ExpressiveWaveProgress(Modifier.fillMaxWidth().height(4.dp))
                            }
                        }
                    }
                }
            }

            if (showConnect) {
                ConnectSheet(vm, state, onDismiss = { showConnect = false })
            }
        }
    }
}
