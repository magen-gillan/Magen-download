package com.magen.download.ui.page

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.magen.download.App
import com.magen.download.MainActivity
import com.magen.download.R
import com.magen.download.ui.common.HapticFeedback.slightHapticFeedback
import com.magen.download.ui.common.LocalWindowWidthState
import com.magen.download.ui.common.Route
import com.magen.download.ui.common.animatedComposable
import com.magen.download.ui.common.animatedComposableVariant
import com.magen.download.ui.common.arg
import com.magen.download.ui.common.id
import com.magen.download.ui.common.slideInVerticallyComposable
import com.magen.download.ui.page.command.TaskListPage
import com.magen.download.ui.page.command.TaskLogPage
import com.magen.download.ui.page.ModeSelectionOverlay
import com.magen.download.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.magen.download.ui.page.downloadv2.DownloadPageV2
import com.magen.download.ui.page.settings.SettingsPage
import com.magen.download.ui.page.settings.about.AboutPage
import com.magen.download.ui.page.settings.about.CreditsPage
import com.magen.download.ui.page.settings.about.UpdatePage
import com.magen.download.ui.page.settings.appearance.AppearancePreferences
import com.magen.download.ui.page.settings.appearance.DarkThemePreferences
import com.magen.download.ui.page.settings.appearance.LanguagePage
import com.magen.download.ui.page.settings.command.TemplateEditPage
import com.magen.download.ui.page.settings.command.TemplateListPage
import com.magen.download.ui.page.settings.directory.DownloadDirectoryPreferences
import com.magen.download.ui.page.settings.format.DownloadFormatPreferences
import com.magen.download.ui.page.settings.format.SubtitlePreference
import com.magen.download.ui.page.settings.general.GeneralDownloadPreferences
import com.magen.download.ui.page.settings.interaction.InteractionPreferencePage
import com.magen.download.ui.page.settings.network.CookieProfilePage
import com.magen.download.ui.page.settings.network.CookiesViewModel
import com.magen.download.ui.page.settings.network.NetworkPreferences
import com.magen.download.ui.page.settings.network.WebViewPage
import com.magen.download.ui.page.settings.troubleshooting.TroubleShootingPage
import com.magen.download.ui.page.videolist.VideoListPage
import com.magen.download.util.APP_MODE_SELECTED
import com.magen.download.util.PreferenceUtil.getBoolean
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val TAG = "HomeEntry"

private val TopDestinations =
    listOf(Route.HOME, Route.TASK_LIST, Route.SETTINGS_PAGE, Route.DOWNLOADS)

@Composable
fun AppEntry(dialogViewModel: DownloadDialogViewModel) {

    val navController = rememberNavController()
    val context = LocalContext.current
    val view = LocalView.current
    val windowWidth = LocalWindowWidthState.current
    val sheetState by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
    val cookiesViewModel: CookiesViewModel = koinViewModel()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val versionReport = App.packageInfo.versionName.toString()
    val appName = stringResource(R.string.app_name)
    val scope = rememberCoroutineScope()

    val onNavigateBack: () -> Unit = {
        with(navController) {
            if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                popBackStack()
            }
        }
    }

    if (sheetState is DownloadDialogViewModel.SheetState.Configure) {
        if (navController.currentDestination?.route != Route.HOME) {
            navController.popBackStack(route = Route.HOME, inclusive = false, saveState = true)
        }
    }

    // App shortcuts (long-press on the launcher icon): navigate once consumed.
    val pendingRoute by MainActivity.pendingRouteFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        if (pendingRoute == null) return@LaunchedEffect
        when (pendingRoute) {
            MainActivity.PENDING_ROUTE_DOWNLOAD_SHEET -> {
                if (navController.currentDestination?.route != Route.HOME) {
                    navController.popBackStack(
                        route = Route.HOME,
                        inclusive = false,
                        saveState = true,
                    )
                }
                dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet())
            }

            MainActivity.PENDING_ROUTE_HISTORY ->
                if (navController.currentDestination?.route != Route.DOWNLOADS) {
                    navController.navigate(Route.DOWNLOADS) {
                        launchSingleTop = true
                        popUpTo(route = Route.HOME)
                    }
                }

            MainActivity.PENDING_ROUTE_SETTINGS ->
                if (navController.currentDestination?.route != Route.SETTINGS_PAGE) {
                    navController.navigate(Route.SETTINGS_PAGE) {
                        launchSingleTop = true
                        popUpTo(route = Route.HOME)
                    }
                }
        }
        MainActivity.clearPendingRoute()
    }

    // Clipboard download suggestion: offer to download a freshly copied link.
    val sheetValue by dialogViewModel.sheetValueFlow.collectAsStateWithLifecycle()
    val clipboardSuggestion by
        dialogViewModel.clipboardSuggestionFlow.collectAsStateWithLifecycle()
    if (clipboardSuggestion != null && sheetValue == DownloadDialogViewModel.SheetValue.Hidden) {
        ClipboardSuggestionDialog(
            urls = clipboardSuggestion.orEmpty(),
            onConfirm = { urls ->
                dialogViewModel.clearClipboardSuggestion()
                dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(urls))
            },
            onDismiss = { dialogViewModel.clearClipboardSuggestion() },
        )
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var currentTopDestination by rememberSaveable { mutableStateOf(currentRoute) }

    LaunchedEffect(currentRoute) {
        if (currentRoute in TopDestinations) {
            currentTopDestination = currentRoute
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationDrawer(
            windowWidth = windowWidth,
            drawerState = drawerState,
            currentRoute = currentRoute,
            currentTopDestination = currentTopDestination,
            showQuickSettings = true,
            gesturesEnabled = currentRoute == Route.HOME,
            onDismissRequest = { drawerState.close() },
            onNavigateToRoute = {
                if (currentRoute != it) {
                    navController.navigate(it) {
                        launchSingleTop = true
                        popUpTo(route = Route.HOME)
                    }
                }
            },
            footer = {
                Text(
                    appName + "\n" + versionReport + "\n" + currentRoute,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            },
        ) {
            NavHost(
                modifier = Modifier.align(Alignment.Center),
                navController = navController,
                startDestination = Route.HOME,
            ) {
                animatedComposable(Route.HOME) {
                    DownloadPageV2(
                        dialogViewModel = dialogViewModel,
                        onMenuOpen = {
                            view.slightHapticFeedback()
                            scope.launch { drawerState.open() }
                        },
                    )
                }
                animatedComposable(Route.DOWNLOADS) { VideoListPage { onNavigateBack() } }
                animatedComposableVariant(Route.TASK_LIST) {
                    TaskListPage(
                        onNavigateBack = onNavigateBack,
                        onNavigateToDetail = { navController.navigate(Route.TASK_LOG id it) },
                    )
                }
                slideInVerticallyComposable(
                    Route.TASK_LOG arg Route.TASK_HASHCODE,
                    arguments = listOf(navArgument(Route.TASK_HASHCODE) { type = NavType.IntType }),
                ) {
                    TaskLogPage(
                        onNavigateBack = onNavigateBack,
                        taskHashCode = it.arguments?.getInt(Route.TASK_HASHCODE) ?: -1,
                    )
                }

                settingsGraph(
                    onNavigateBack = onNavigateBack,
                    onNavigateTo = { route ->
                        navController.navigate(route = route) { launchSingleTop = true }
                    },
                    cookiesViewModel = cookiesViewModel,
                )
            }

            AppUpdater()
            YtdlpUpdater()
        }

        // First launch: choose Simple or Advanced mode
        var modeSelected by rememberSaveable { mutableStateOf(APP_MODE_SELECTED.getBoolean()) }
        if (!modeSelected) {
            ModeSelectionOverlay(onConfirmed = { modeSelected = true })
        }
    }
}

fun NavGraphBuilder.settingsGraph(
    onNavigateBack: () -> Unit,
    onNavigateTo: (route: String) -> Unit,
    cookiesViewModel: CookiesViewModel,
) {
    navigation(startDestination = Route.SETTINGS_PAGE, route = Route.SETTINGS) {
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences(onNavigateBack)
        }
        animatedComposable(Route.SETTINGS_PAGE) {
            SettingsPage(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.GENERAL_DOWNLOAD_PREFERENCES) {
            GeneralDownloadPreferences(onNavigateBack = { onNavigateBack() }) {
                onNavigateTo(Route.TEMPLATE)
            }
        }
        animatedComposable(Route.DOWNLOAD_FORMAT) {
            DownloadFormatPreferences(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.SUBTITLE_PREFERENCES)
            }
        }
        animatedComposable(Route.SUBTITLE_PREFERENCES) { SubtitlePreference { onNavigateBack() } }
        animatedComposable(Route.ABOUT) {
            AboutPage(
                onNavigateBack = onNavigateBack,
                onNavigateToCreditsPage = { onNavigateTo(Route.CREDITS) },
                onNavigateToUpdatePage = { onNavigateTo(Route.AUTO_UPDATE) },
            )
        }
        animatedComposable(Route.CREDITS) { CreditsPage(onNavigateBack) }
        animatedComposable(Route.AUTO_UPDATE) { UpdatePage(onNavigateBack) }
        animatedComposable(Route.APPEARANCE) {
            AppearancePreferences(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.INTERACTION) { InteractionPreferencePage(onBack = onNavigateBack) }
        animatedComposable(Route.LANGUAGES) { LanguagePage { onNavigateBack() } }
        animatedComposable(Route.TEMPLATE) {
            TemplateListPage(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.TEMPLATE_EDIT id it)
            }
        }
        animatedComposable(
            Route.TEMPLATE_EDIT arg Route.TEMPLATE_ID,
            arguments = listOf(navArgument(Route.TEMPLATE_ID) { type = NavType.IntType }),
        ) {
            TemplateEditPage(onNavigateBack, it.arguments?.getInt(Route.TEMPLATE_ID) ?: -1)
        }
        animatedComposable(Route.DARK_THEME) { DarkThemePreferences { onNavigateBack() } }
        animatedComposable(Route.NETWORK_PREFERENCES) {
            NetworkPreferences(
                navigateToCookieProfilePage = { onNavigateTo(Route.COOKIE_PROFILE) }
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_PROFILE) {
            CookieProfilePage(
                cookiesViewModel = cookiesViewModel,
                navigateToCookieGeneratorPage = { onNavigateTo(Route.COOKIE_GENERATOR_WEBVIEW) },
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_GENERATOR_WEBVIEW) {
            WebViewPage(cookiesViewModel = cookiesViewModel) {
                onNavigateBack()
                CookieManager.getInstance().flush()
            }
        }
        animatedComposable(Route.TROUBLESHOOTING) {
            TroubleShootingPage(onNavigateTo = onNavigateTo, onBack = onNavigateBack)
        }
    }
}
