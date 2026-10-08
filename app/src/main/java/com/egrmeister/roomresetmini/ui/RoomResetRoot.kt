package com.egrmeister.roomresetmini.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.roomresetmini.AppContainer
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.editor.EditorViewModel
import com.egrmeister.roomresetmini.ui.editor.TemplateEditorScreen
import com.egrmeister.roomresetmini.ui.editor.TemplatesScreen
import com.egrmeister.roomresetmini.ui.editor.TemplatesViewModel
import com.egrmeister.roomresetmini.ui.history.HistoryDetailScreen
import com.egrmeister.roomresetmini.ui.history.HistoryScreen
import com.egrmeister.roomresetmini.ui.history.HistoryViewModel
import com.egrmeister.roomresetmini.ui.history.SessionDetailViewModel
import com.egrmeister.roomresetmini.ui.results.ResultScreen
import com.egrmeister.roomresetmini.ui.results.ReviewScreen
import com.egrmeister.roomresetmini.ui.room.RoomScreen
import com.egrmeister.roomresetmini.ui.room.RoomViewModel
import com.egrmeister.roomresetmini.ui.settings.PrivacyScreen
import com.egrmeister.roomresetmini.ui.settings.SettingsScreen
import com.egrmeister.roomresetmini.ui.settings.SettingsViewModel
import com.egrmeister.roomresetmini.ui.zone.ZoneScreen

object Routes {
    const val ROOM = "room"
    const val ZONE = "zone/{zone}"
    const val REVIEW = "review"
    const val RESULT = "result/{id}"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val TEMPLATES = "templates"
    const val EDITOR = "editor/{id}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun zone(zone: ZoneType) = "zone/${zone.name}"
    fun result(id: Long) = "result/$id"
    fun historyDetail(id: Long) = "history/$id"

    /** id = -1 creates a new room. */
    fun editor(id: Long?) = "editor/${id ?: -1L}"
}

@Composable
fun RoomResetRoot(container: AppContainer) {
    val navController = rememberNavController()

    // Activity-scoped: the active session survives navigation between room, zones and review.
    val roomVm: RoomViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RoomViewModel(
                    templates = container.templates,
                    sessions = container.sessions,
                    settings = container.settings,
                    saver = container.saveCoordinator,
                    monotonic = container.monotonicClock,
                    wall = container.wallClock,
                    boot = container.bootReference,
                    sound = container.sound,
                )
            }
        },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_START) { roomVm.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { roomVm.onHidden() }

    val roomState by roomVm.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.ROOM) {
        composable(Routes.ROOM) {
            RoomScreen(
                state = roomState,
                viewModel = roomVm,
                onOpenZone = { navController.navigate(Routes.zone(it)) },
                onFinish = { navController.navigate(Routes.REVIEW) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onEditRooms = { navController.navigate(Routes.TEMPLATES) },
                onCreateRoom = { navController.navigate(Routes.editor(null)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.ZONE,
            arguments = listOf(navArgument("zone") { type = NavType.StringType }),
        ) { entry ->
            val zone = ZoneType.fromKey(entry.arguments?.getString("zone").orEmpty()) ?: ZoneType.DESK
            ZoneScreen(
                zone = zone,
                state = roomState,
                viewModel = roomVm,
                onBack = { navController.popBackStack() },
                onFinish = { navController.navigate(Routes.REVIEW) },
                onEditTemplate = { id -> navController.navigate(Routes.editor(id)) },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                state = roomState,
                viewModel = roomVm,
                onContinue = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Routes.result(id)) {
                        popUpTo(Routes.ROOM)
                    }
                },
                onDiscarded = { navController.popBackStack(Routes.ROOM, inclusive = false) },
            )
        }
        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            val detailVm: SessionDetailViewModel = viewModel(
                key = "result-$id",
                factory = viewModelFactory { initializer { SessionDetailViewModel(id, container.sessions) } },
            )
            ResultScreen(
                viewModel = detailVm,
                onBackToRooms = { navController.backToRoom() },
                onStartAnother = { session ->
                    roomVm.startAnother(session)
                    navController.backToRoom()
                },
            )
        }
        composable(Routes.HISTORY) {
            val historyVm: HistoryViewModel = viewModel(
                factory = viewModelFactory { initializer { HistoryViewModel(container.sessions) } },
            )
            HistoryScreen(
                viewModel = historyVm,
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Routes.historyDetail(it)) },
            )
        }
        composable(
            Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            val detailVm: SessionDetailViewModel = viewModel(
                key = "history-$id",
                factory = viewModelFactory { initializer { SessionDetailViewModel(id, container.sessions) } },
            )
            HistoryDetailScreen(
                viewModel = detailVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TEMPLATES) {
            val templatesVm: TemplatesViewModel = viewModel(
                factory = viewModelFactory { initializer { TemplatesViewModel(container.templates) } },
            )
            TemplatesScreen(
                viewModel = templatesVm,
                activeTemplateName = roomState.session?.templateName,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editor(it)) },
                onCreate = { navController.navigate(Routes.editor(null)) },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val editorVm: EditorViewModel = viewModel(
                key = "editor-${id ?: "new"}",
                factory = viewModelFactory { initializer { EditorViewModel(id, container.templates) } },
            )
            TemplateEditorScreen(
                viewModel = editorVm,
                sessionActive = roomState.session != null,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val settingsVm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(container.settings, container.templates, container.sessions, container.dataResetter)
                    }
                },
            )
            SettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.backToRoom() {
    if (!popBackStack(Routes.ROOM, inclusive = false)) {
        navigate(Routes.ROOM) { launchSingleTop = true }
    }
}
