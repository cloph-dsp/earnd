package com.earnd.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.RowDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.earnd.app.ui.apps.AppsScreen
import com.earnd.app.ui.habits.CheckInRoute
import com.earnd.app.ui.habits.HabitDetailScreen
import com.earnd.app.ui.habits.HabitEditScreen
import com.earnd.app.ui.habits.ManageHabitsScreen
import com.earnd.app.ui.home.TodayScreen
import com.earnd.app.ui.onboarding.OnboardingScreen
import com.earnd.app.ui.onboarding.PermissionsScreen
import com.earnd.app.ui.photo.PhotoCheckInRoute
import com.earnd.app.ui.settings.SettingsScreen
import com.earnd.app.ui.stats.StatsScreen
import com.earnd.app.ui.tasks.TaskEditScreen
import com.earnd.app.ui.tasks.TasksScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.WbSunny),
    Tab(Routes.TASKS, "Tasks", Icons.Outlined.Checklist),
    Tab(Routes.STATS, "Stats", Icons.Outlined.BarChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun EarndNavHost(nav: NavHostController, startDestination: String) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                Column {
                RowDivider()
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Earnd.palette.amber, selectedTextColor = Earnd.palette.amber,
                                indicatorColor = MaterialTheme.colorScheme.background,
                                unselectedIconColor = Earnd.palette.muted, unselectedTextColor = Earnd.palette.muted,
                            ),
                        )
                    }
                }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = startDestination, modifier = Modifier.padding(padding)) {
            val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(Routes.TODAY) }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = back) }
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenHabit = { nav.navigate(Routes.habitDetail(it)) },
                    onAddHabit = { nav.navigate(Routes.habitEdit()) },
                    onPhotoCheckIn = { nav.navigate(Routes.photo(it)) },
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                    onManageHabits = { nav.navigate(Routes.HABITS_MANAGE) },
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(onOpenTask = { nav.navigate(Routes.taskEdit(it)) }, onNewTask = { nav.navigate(Routes.taskEdit()) })
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenApps = { nav.navigate(Routes.APPS) },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Routes.APPS) { AppsScreen(onBack = back) }
            composable(Routes.HABITS_MANAGE) {
                ManageHabitsScreen(onBack = back, onOpenHabit = { nav.navigate(Routes.habitDetail(it)) })
            }
            composable(
                Routes.HABIT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { e -> HabitEditScreen(habitId = e.arguments?.getLong("id") ?: -1L, onDone = back) }
            composable(
                Routes.HABIT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                val id = e.arguments?.getLong("id") ?: -1L
                HabitDetailScreen(habitId = id, onBack = back, onEdit = { nav.navigate(Routes.habitEdit(id)) })
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = Routes.DEEP_TASK },
                    navDeepLink { uriPattern = Routes.DEEP_ADD_TASK },
                ),
            ) { e ->
                TaskEditScreen(
                    taskId = e.arguments?.getLong("id") ?: -1L,
                    onDone = back,
                    onOpenTask = { nav.navigate(Routes.taskEdit(it)) },
                )
            }
            composable(
                Routes.CHECKIN,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L }),
                deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_CHECKIN }),
            ) { e ->
                val id = e.arguments?.getLong("habitId") ?: -1L
                CheckInRoute(
                    habitId = id,
                    onNeedsPhoto = { nav.navigate(Routes.photo(id)) { popUpTo(Routes.CHECKIN) { inclusive = true } } },
                    onDone = back,
                )
            }
            composable(
                Routes.PHOTO,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
            ) { e -> PhotoCheckInRoute(habitId = e.arguments?.getLong("habitId") ?: -1L, onDone = back) }
        }
    }
}
