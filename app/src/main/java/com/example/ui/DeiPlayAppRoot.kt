package com.example.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.model.AccountRole
import com.example.ui.components.DeiPlayOfficialLogo
import com.example.ui.components.FloatingMiniPlayer
import com.example.ui.navigation.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

internal fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Activity context required")
}

@Composable
fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalContext.current.findActivity()
        var pipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
        DisposableEffect(activity) {
            val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
                pipMode = info.isInPictureInPictureMode
            }
            activity.addOnPictureInPictureModeChangedListener(observer)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
        }
        return pipMode
    }
    return false
}

private data class BottomTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeObj: Any,
    val testTag: String
)

@Composable
fun DeiPlayAppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val inSystemPipMode = rememberIsInPipMode()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val homeVideos by viewModel.filteredHomeVideos.collectAsStateWithLifecycle()
    val shorts by viewModel.publicShorts.collectAsStateWithLifecycle()
    val channels by viewModel.activeChannels.collectAsStateWithLifecycle()
    val homeCategory by viewModel.homeCategory.collectAsStateWithLifecycle()
    val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()
    val homeLanguage by viewModel.homeLanguage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val showAuthModal by viewModel.showAuthModal.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isWatchScreen = currentDestination?.hasRoute<WatchRoute>() == true
    val isShortsScreen = currentDestination?.hasRoute<ShortsRoute>() == true

    // Configure Android Picture-in-Picture params dynamically
    fun triggerSystemPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val w = playerState.videoWidth.coerceAtLeast(1)
            val h = playerState.videoHeight.coerceAtLeast(1)
            val rawRatio = w.toFloat() / h.toFloat()
            val rational = when {
                rawRatio > 2.3f -> Rational(23, 10)
                rawRatio < 0.44f -> Rational(10, 23)
                else -> Rational(w, h)
            }
            val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(isWatchScreen && playerState.isPlaying)
            }
            context.findActivity().enterPictureInPictureMode(builder.build())
        } else {
            viewModel.emitMessage("Picture-in-Picture del sistema requiere Android 8.0+")
        }
    }

    val bottomTabs = listOf(
        BottomTab(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, HomeRoute, "nav_home"),
        BottomTab(R.string.nav_shorts, Icons.Filled.Bolt, Icons.Outlined.Bolt, ShortsRoute, "nav_shorts"),
        BottomTab(R.string.nav_publish, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, PublishRoute(false), "nav_publish"),
        BottomTab(R.string.nav_subscriptions, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, SubscriptionsRoute, "nav_subscriptions"),
        BottomTab(R.string.nav_library, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, LibraryRoute, "nav_library")
    )

    val showChrome = !inSystemPipMode && !playerState.isFullscreen && !isWatchScreen

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showChrome,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DeiPlaySurface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeiPlayOfficialLogo(size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("DeiPlay", style = MaterialTheme.typography.titleLarge, color = DeiPlayWhite)
                            Text(stringResource(R.string.slogan), style = MaterialTheme.typography.labelSmall, color = DeiPlayGold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.no_ads_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = DeiPlaySuccess
                    )
                }
                HorizontalDivider(color = DeiPlayOutline)

                DrawerActionItem(Icons.Outlined.Home, "Inicio") {
                    scope.launch { drawerState.close() }
                    navController.navigate(HomeRoute) { launchSingleTop = true }
                }
                DrawerActionItem(Icons.Outlined.LocalFireDepartment, "Tendencias mundiales") {
                    scope.launch { drawerState.close() }
                    viewModel.setHomeCategory("Todos")
                    navController.navigate(HomeRoute) { launchSingleTop = true }
                }
                DrawerActionItem(Icons.Outlined.Public, "Buscador mundial") {
                    scope.launch { drawerState.close() }
                    navController.navigate(SearchRoute)
                }
                DrawerActionItem(Icons.Outlined.DownloadForOffline, "Descargas sin conexión") {
                    scope.launch { drawerState.close() }
                    navController.navigate(DownloadsRoute)
                }
                DrawerActionItem(Icons.Outlined.Analytics, "Panel de Creadores") {
                    scope.launch { drawerState.close() }
                    navController.navigate(CreatorStudioRoute)
                }
                DrawerActionItem(Icons.Outlined.Notifications, "Notificaciones") {
                    scope.launch { drawerState.close() }
                    navController.navigate(NotificationsRoute)
                }
                if (activeAccount?.role == AccountRole.ADMIN.name) {
                    DrawerActionItem(Icons.Outlined.AdminPanelSettings, "Panel Administrativo", tint = DeiPlayGold) {
                        scope.launch { drawerState.close() }
                        navController.navigate(AdminPanelRoute)
                    }
                }
                DrawerActionItem(Icons.Outlined.Settings, "Ajustes") {
                    scope.launch { drawerState.close() }
                    navController.navigate(SettingsRoute)
                }
            }
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWideLayout = maxWidth >= 700.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (showChrome && currentDestination?.hasRoute<HomeRoute>() == true) {
                        HomeTopBar(
                            activeAccount = activeAccount,
                            unreadNotifications = unreadNotifications,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenSearch = { navController.navigate(SearchRoute) },
                            onOpenPublish = { navController.navigate(PublishRoute(false)) },
                            onOpenNotifications = { navController.navigate(NotificationsRoute) },
                            onOpenProfileOrAuth = {
                                if (activeAccount == null) viewModel.openAuthModal()
                                else navController.navigate(SettingsRoute)
                            }
                        )
                    }
                },
                bottomBar = {
                    if (showChrome && !isWideLayout) {
                        NavigationBar(
                            containerColor = DeiPlaySurface,
                            contentColor = DeiPlayWhite
                        ) {
                            bottomTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.routeObj::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.routeObj) {
                                            popUpTo(HomeRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DeiPlayRed,
                                        selectedTextColor = DeiPlayWhite,
                                        unselectedIconColor = DeiPlayTextSecondary,
                                        unselectedTextColor = DeiPlayTextMuted,
                                        indicatorColor = DeiPlayRedDeep.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (showChrome && !isWideLayout && !isShortsScreen) innerPadding.calculateBottomPadding() else 0.dp,
                            top = if (showChrome && currentDestination?.hasRoute<HomeRoute>() == true) innerPadding.calculateTopPadding() else 0.dp
                        )
                ) {
                    if (showChrome && isWideLayout) {
                        NavigationRail(containerColor = DeiPlaySurface) {
                            bottomTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.routeObj::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.routeObj) {
                                            popUpTo(HomeRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, null) },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = DeiPlayRed,
                                        selectedTextColor = DeiPlayWhite,
                                        unselectedIconColor = DeiPlayTextSecondary,
                                        unselectedTextColor = DeiPlayTextMuted,
                                        indicatorColor = DeiPlayRedDeep.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    categories = categories,
                                    videos = homeVideos,
                                    shorts = shorts,
                                    channels = channels,
                                    activeAccount = activeAccount,
                                    selectedCategory = homeCategory,
                                    selectedCountry = homeCountry,
                                    selectedLanguage = homeLanguage,
                                    isSyncing = isSyncing,
                                    syncError = syncError,
                                    onWatchVideo = { item ->
                                        viewModel.playVideo(item, homeVideos)
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) },
                                    onOpenShorts = { navController.navigate(ShortsRoute) },
                                    onOpenSearch = { navController.navigate(SearchRoute) }
                                )
                            }

                            composable<ShortsRoute> {
                                ShortsScreen(
                                    viewModel = viewModel,
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) },
                                    onOpenPublishShort = { navController.navigate(PublishRoute(defaultShort = true)) }
                                )
                            }

                            composable<PublishRoute> { backStack ->
                                val route = backStack.toRoute<PublishRoute>()
                                PublishScreen(
                                    viewModel = viewModel,
                                    defaultShort = route.defaultShort,
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) }
                                )
                            }

                            composable<SubscriptionsRoute> {
                                SubscriptionsScreen(
                                    viewModel = viewModel,
                                    onWatchVideo = { item ->
                                        viewModel.playVideo(item, homeVideos)
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) }
                                )
                            }

                            composable<LibraryRoute> {
                                LibraryScreen(
                                    viewModel = viewModel,
                                    onWatchVideo = { item ->
                                        viewModel.playVideo(item, homeVideos)
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) },
                                    onOpenDownloads = { navController.navigate(DownloadsRoute) },
                                    onOpenPublish = { navController.navigate(PublishRoute(false)) },
                                    onOpenCreatorStudio = { navController.navigate(CreatorStudioRoute) }
                                )
                            }

                            composable<SearchRoute> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    onWatchVideo = { item ->
                                        viewModel.playVideo(item, homeVideos)
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onOpenChannel = { chId -> navController.navigate(ChannelRoute(chId)) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<WatchRoute> { backStack ->
                                val route = backStack.toRoute<WatchRoute>()
                                WatchScreen(
                                    videoId = route.videoId,
                                    viewModel = viewModel,
                                    inSystemPipMode = inSystemPipMode,
                                    onEnterSystemPip = { triggerSystemPip() },
                                    onMinimizeToFloating = { viewModel.playerController.minimizePlayer() },
                                    onOpenChannel = { chId ->
                                        viewModel.playerController.minimizePlayer()
                                        navController.navigate(ChannelRoute(chId))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<ChannelRoute> { backStack ->
                                val route = backStack.toRoute<ChannelRoute>()
                                ChannelScreen(
                                    channelId = route.channelId,
                                    viewModel = viewModel,
                                    onWatchVideo = { item ->
                                        viewModel.playVideo(item, homeVideos)
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<DownloadsRoute> {
                                DownloadsScreen(
                                    viewModel = viewModel,
                                    onPlayOffline = { item ->
                                        viewModel.playVideo(item, emptyList())
                                        navController.navigate(WatchRoute(item.video.id))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<NotificationsRoute> {
                                NotificationsScreen(
                                    viewModel = viewModel,
                                    onOpenVideoById = { vid -> navController.navigate(WatchRoute(vid)) },
                                    onOpenChannelById = { cid -> navController.navigate(ChannelRoute(cid)) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<CreatorStudioRoute> {
                                CreatorStudioScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AdminPanelRoute> {
                                AdminPanelScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onOpenAdminPanel = { navController.navigate(AdminPanelRoute) },
                                    onOpenDownloads = { navController.navigate(DownloadsRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Floating In-App Mini-Player Overlay
                        if (playerState.currentVideo != null && playerState.isMinimized && !inSystemPipMode && !isWatchScreen) {
                            FloatingMiniPlayer(
                                controller = viewModel.playerController,
                                state = playerState,
                                onExpandToFullWatch = { vid ->
                                    navController.navigate(WatchRoute(vid)) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAuthModal) {
        AuthDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeAuthModal() }
        )
    }
}

@Composable
private fun DrawerActionItem(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = DeiPlayWhite,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, color = tint) },
        icon = { Icon(icon, null, tint = tint) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}
