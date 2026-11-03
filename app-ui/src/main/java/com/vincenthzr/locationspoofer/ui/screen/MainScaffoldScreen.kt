package com.vincenthzr.locationspoofer.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.vincenthzr.locationspoofer.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vincenthzr.locationspoofer.data.model.effectiveMapEngine
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapView
import com.vincenthzr.locationspoofer.ui.components.LOCATE_ME_ZOOM
import com.vincenthzr.locationspoofer.ui.liquid.AndroidFloatingBottomBar
import com.vincenthzr.locationspoofer.ui.liquid.FloatingBottomBarItem
import com.vincenthzr.locationspoofer.ui.screen.settings.AdaptationProgressScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.AdaptationState
import com.vincenthzr.locationspoofer.ui.screen.settings.AppearanceLanguageScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.EnvDataScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.EnvTokensScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.HookStatusScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.LanguageSettingsScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.MapEngineSettingsScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.MotionRealismScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.SimulationSettingsScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.SystemAdaptationScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.ThemeSettingsScreen
import com.vincenthzr.locationspoofer.ui.screen.settings.rememberAdaptationState
import com.vincenthzr.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateViewModel
import com.vincenthzr.locationspoofer.viewmodel.getIgnoredVersion
import com.vincenthzr.locationspoofer.viewmodel.handleSpoofingIntent
import com.vincenthzr.locationspoofer.viewmodel.isDomesticEnvironment
import com.vincenthzr.locationspoofer.viewmodel.setDebugDumpSystemServices
import com.vincenthzr.locationspoofer.viewmodel.setIgnoredVersion
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 启动时把地图移到初始坐标：最多重试的次数、两次之间的间隔、判定"已到位"的误差（约 100 米） */
private const val INITIAL_CENTER_ATTEMPTS = 30
private const val INITIAL_CENTER_RETRY_MS = 200L
private const val INITIAL_CENTER_TOLERANCE_DEG = 0.001

private const val SUB_SCREEN_TRANSITION_MS = 300
private const val BACK_CANCEL_MS = 180
private const val BACK_SCALE_DELTA = 0.1f
private val BACK_EDGE_MARGIN = 8.dp
private val BACK_CORNER = 28.dp
private val BACK_ELEVATION = 12.dp

enum class BottomTab(val icon: ImageVector) {
    Location(Icons.Rounded.MyLocation),
    Route(Icons.Rounded.Route),
    Features(Icons.Rounded.Extension),
    Info(Icons.Rounded.Settings);

    @Composable
    fun getLabel(): String {
        return when (this) {
            Location -> stringResource(R.string.tab_location)
            Route -> stringResource(R.string.tab_route)
            Features -> stringResource(R.string.tab_features)
            Info -> stringResource(R.string.tab_info)
        }
    }
}

@Composable
fun MainScaffoldScreen(
    viewModel: MainViewModel,
    uiState: AppState
) {
    val pageCount = BottomTab.values().size
    var selectedTab by remember { mutableIntStateOf(BottomTab.Location.ordinal) }
    // 打开的页面，最后一个在最上面
    var subScreenStack by remember { mutableStateOf(emptyList<MainSubScreen>()) }
    // 正在画的页面：比 subScreenStack 多出还在滑出去的页面
    val renderedSubScreens = remember { mutableStateListOf<MainSubScreen>() }
    val adaptationState = rememberAdaptationState()
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val isDark = isAppInDarkTheme()
    val updateViewModel: UpdateViewModel = koinViewModel()

    val backdrop = rememberLayerBackdrop()
    val glassActive = uiState.floatingBottomBar && uiState.liquidGlass

    // 预测性返回的手势进度，0 表示没有在返回
    val backProgress = remember { Animatable(0f) }
    var backFromLeftEdge by remember { mutableStateOf(true) }
    var backGesturePage by remember { mutableStateOf<MainSubScreen?>(null) }
    var backGestureActive by remember { mutableStateOf(false) }
    val backGesturing by remember { derivedStateOf { backGestureActive && backProgress.value > 0f } }
    val backScope = rememberCoroutineScope()
    val openSubScreen: (MainSubScreen) -> Unit = { page ->
        if (page !in subScreenStack) {
            // 刚用手势退出同一页又马上打开时，不能沿用还没还原的缩小状态
            backGesturePage = null
            backScope.launch { backProgress.snapTo(0f) }
            renderedSubScreens.remove(page)
            renderedSubScreens.add(page)
            subScreenStack = subScreenStack.pushSubScreen(page)
        }
    }

    val updateUiState by updateViewModel.uiState.collectAsState()
    var hasAutoCheckedUpdates by remember { mutableStateOf(false) }
    var showStartupUpdateDialog by remember { mutableStateOf(false) }
    val targetReleases = remember(updateUiState.releases, uiState.checkBetaUpdates) {
        if (uiState.checkBetaUpdates) updateUiState.releases else updateUiState.releases.filter { !it.isPrerelease }
    }
    val latestRelease = targetReleases.firstOrNull()

    // 软件启动进入主界面时，立即在后台检索是否有新版本发布
    LaunchedEffect(Unit) {
        updateViewModel.fetchReleases()
    }

    // 检测到未被忽略的新版本时弹出启动更新提示框
    LaunchedEffect(targetReleases, updateUiState.isLoading) {
        if (!hasAutoCheckedUpdates && !updateUiState.isLoading && targetReleases.isNotEmpty()) {
            if (latestRelease != null) {
                val latestVersion = latestRelease.versionName
                val currentVersion = BuildConfig.VERSION_NAME
                val ignoredVersion = viewModel.getIgnoredVersion()
                if (isNewerVersion(
                        latestVersion,
                        currentVersion
                    ) && latestVersion != ignoredVersion
                ) {
                    showStartupUpdateDialog = true
                }
            }
            hasAutoCheckedUpdates = true
        }
    }

    // Key 不再内置：没有可用的 Key 时搜索和路线规划都用不了，启动后提醒一次去"地图配置"填写。
    // "自动"模式只要高德、百度填了任意一个就不提醒；手动选了某家时提醒填那一家。
    // （Google 地图的显示 Key 走编译期 manifest，这里不检查）
    val context = LocalContext.current
    var hasShownMissingKeyHint by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isInitializing, uiState.mapEngine) {
        if (uiState.isInitializing || hasShownMissingKeyHint) return@LaunchedEffect
        val missingKeyHint = when (uiState.mapEngine) {
            MapEngine.AUTO ->
                if (uiState.amapApiKey.isBlank() && uiState.baiduApiKey.isBlank()) {
                    context.getString(R.string.map_key_missing_auto_hint)
                } else null
            MapEngine.AMAP ->
                if (uiState.amapApiKey.isBlank()) {
                    context.getString(R.string.map_key_missing_hint, context.getString(R.string.engine_amap_short))
                } else null
            MapEngine.BAIDU ->
                if (uiState.baiduApiKey.isBlank()) {
                    context.getString(R.string.map_key_missing_hint, context.getString(R.string.engine_baidu_short))
                } else null
            MapEngine.GOOGLE -> null
        }
        if (missingKeyHint != null) {
            hasShownMissingKeyHint = true
            Toast.makeText(context, missingKeyHint, Toast.LENGTH_LONG).show()
        }
    }

    // 启动时初始坐标（模拟中的坐标 / 上次位置 / 当前定位 / 默认坐标）是异步拿到的，
    // 拿到后把地图移过去一次；之后的移动交给用户和各 Tab 自己的逻辑
    var hasCenteredInitially by remember { mutableStateOf(false) }
    LaunchedEffect(mapController, uiState.latitudeInput, uiState.longitudeInput) {
        if (hasCenteredInitially) return@LaunchedEffect
        val controller = mapController ?: return@LaunchedEffect
        val lat = uiState.latitudeInput.toDoubleOrNull() ?: return@LaunchedEffect
        val lng = uiState.longitudeInput.toDoubleOrNull() ?: return@LaunchedEffect
        // 高德地图刚加载完的一小段时间里会无视镜头移动（相机仍停在 SDK 默认的北京），没开模拟、
        // 真实位置在地图加载后才拿到时就会这样。移动后确认镜头真的到位，没到位稍后重试；
        // 居中之前镜头的变化不算用户选点，免得把北京当成选中的位置存下来
        repeat(INITIAL_CENTER_ATTEMPTS) {
            controller.moveCamera(lat, lng, 15f)
            val cameraLat = controller.cameraTargetLat
            val cameraLng = controller.cameraTargetLng
            if (cameraLat != null && cameraLng != null &&
                abs(cameraLat - lat) < INITIAL_CENTER_TOLERANCE_DEG && abs(cameraLng - lng) < INITIAL_CENTER_TOLERANCE_DEG
            ) {
                hasCenteredInitially = true
                return@LaunchedEffect
            }
            delay(INITIAL_CENTER_RETRY_MS)
        }
        hasCenteredInitially = true
    }

    // 视图模型请求的镜头移动（例如停止模拟后自动回到真实位置，效果同点一次"当前定位"）
    LaunchedEffect(uiState.mapRecenterRequest, mapController) {
        val request = uiState.mapRecenterRequest ?: return@LaunchedEffect
        mapController?.animateCamera(request.lat, request.lng, LOCATE_ME_ZOOM)
    }

    // 手势松手后页面从缩小的状态滑出去，滑完才还原
    LaunchedEffect(subScreenStack) {
        delay(SUB_SCREEN_TRANSITION_MS.toLong())
        if (!backGestureActive) {
            backProgress.snapTo(0f)
            backGesturePage = null
        }
    }

    // 针对非首页 Tab（路线/功能/信息）的系统返回拦截：返回到定位首页
    BackHandler(enabled = subScreenStack.isEmpty() && selectedTab != BottomTab.Location.ordinal) {
        selectedTab = BottomTab.Location.ordinal
    }

    LaunchedEffect(selectedTab, mapController, uiState.mapType) {
        if (selectedTab == 0) {
            mapController?.clear()
            mapController?.setMapType(uiState.mapType)
        }
    }

    val selectPage: (Int) -> Unit = { index ->
        selectedTab = index.coerceIn(0, pageCount - 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    // 返回手势进行中底栏先出来，露出的就是返回后的样子
                    visible = subScreenStack.isEmpty() || (subScreenStack.size == 1 && backGesturing),
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                ) {
                    if (!uiState.floatingBottomBar) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            BottomTab.values().forEachIndexed { index, tab ->
                                val label = tab.getLabel()
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectPage(index) },
                                    icon = { Icon(tab.icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentBlue,
                                        selectedTextColor = AccentBlue,
                                        indicatorColor = AccentBlue.copy(alpha = if (isDark) 0.25f else 0.16f)
                                    )
                                )
                            }
                        }
                    } else Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AndroidFloatingBottomBar(
                            selectedIndex = { selectedTab },
                            onSelected = selectPage,
                            backdrop = backdrop,
                            tabsCount = pageCount,
                            isBlurEnabled = uiState.liquidGlass,
                            glassStrength = uiState.liquidGlassStrength
                        ) {
                            BottomTab.values().forEachIndexed { index, tab ->
                                val isSelected = selectedTab == index
                                FloatingBottomBarItem(
                                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                                    onClick = { selectPage(index) }
                                ) {
                                    val label = tab.getLabel()
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = label,
                                        tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (isDark) 0.65f else 0.70f
                                        ),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (isDark) 0.65f else 0.70f
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                // 底层地图层（定位与路线 Tab 共享）
                Box(modifier = Modifier.fillMaxSize()) {
                    AppMapView(
                        modifier = Modifier.fillMaxSize(),
                        mapEngine = uiState.effectiveMapEngine,
                        isDomestic = viewModel.isDomesticEnvironment(),
                        onMapReady = { controller ->
                            mapController = controller
                            controller.disableUiControls()
                            controller.setMapType(uiState.mapType)
                            // 坐标还没就绪（启动时定位中）就先不动，由下面的 LaunchedEffect 在拿到坐标后居中
                            val initLat = uiState.latitudeInput.toDoubleOrNull()
                            val initLng = uiState.longitudeInput.toDoubleOrNull()
                            if (initLat != null && initLng != null) {
                                controller.moveCamera(initLat, initLng, 15f)
                            }

                            controller.setOnCameraChangeListener { lat, lng ->
                                // 首次居中前地图停在 SDK 默认视野，这时的镜头位置不算用户选点
                                if (hasCenteredInitially && currentSelectedTab == BottomTab.Location.ordinal) {
                                    viewModel.handleSpoofingIntent(
                                        SpoofingIntent.ConfirmMapPoint(
                                            lat,
                                            lng
                                        )
                                    )
                                }
                            }
                            controller.setOnCameraMoveListener { lat, lng ->
                                if (hasCenteredInitially && currentSelectedTab == BottomTab.Location.ordinal) {
                                    viewModel.handleSpoofingIntent(
                                        SpoofingIntent.MapPointMoved(
                                            lat,
                                            lng
                                        )
                                    )
                                }
                            }
                        }
                    )

                    // 定位选点十字准心（仅在定位 Tab 显示）
                    AnimatedVisibility(
                        visible = selectedTab == 0,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            Icons.Rounded.AddLocationAlt,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(bottom = 16.dp)
                        )
                    }
                }

                // 底部 Tab 内容切换（纯左右完整切换，无渐变）
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val isForward = targetState > initialState
                        if (isForward) {
                            slideInHorizontally(
                                animationSpec = tween(
                                    300,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> width }
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            300,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> -width })
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(
                                    300,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> -width }
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            300,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> width })
                        }
                    },
                    label = "main_tabs_transition"
                ) { targetTab ->
                    when (targetTab) {
                        BottomTab.Location.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.LocationTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            onCollectionRouteSelected = { selectedTab = BottomTab.Route.ordinal },
                            tabBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Route.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.RouteTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            mapController = mapController,
                            isActive = true,
                            bottomBarHeight = paddingValues.calculateBottomPadding()
                        )

                        BottomTab.Features.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.FeaturesTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToEnvData = { openSubScreen(MainSubScreen.EnvData) },
                            onNavigateToSimulation = { openSubScreen(MainSubScreen.SimulationSettings) }
                        )

                        BottomTab.Info.ordinal -> com.vincenthzr.locationspoofer.ui.screen.tabs.InfoTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            updateUiState = updateUiState,
                            tabBarHeight = paddingValues.calculateBottomPadding(),
                            onNavigateToUpdate = { openSubScreen(MainSubScreen.Update) },
                            onNavigateToAppearance = { openSubScreen(MainSubScreen.AppearanceLanguage) },
                            onNavigateToMapEngine = { openSubScreen(MainSubScreen.MapEngineSettings) },
                            onNavigateToSystemAdaptation = { openSubScreen(MainSubScreen.SystemAdaptation) }
                        )
                    }
                }
            }
        }

        // 全屏子页面，一层盖一层（纯左右完整滑入与退出，无内部冲突动画）
        renderedSubScreens.toList().forEach { page ->
            key(page) {
                val visibleState = remember { MutableTransitionState(false) }
                visibleState.targetState = page in subScreenStack
                LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
                    if (visibleState.isIdle && !visibleState.currentState) renderedSubScreens.remove(page)
                }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInHorizontally(
                        tween(
                            SUB_SCREEN_TRANSITION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) { width -> width },
                    exit = slideOutHorizontally(tween(SUB_SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing)) { width -> width }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 跟手缩小并移向手势方向，露出下面一层
                                val p = if (backGesturePage == page) backProgress.value else 0f
                                val scale = 1f - BACK_SCALE_DELTA * p
                                scaleX = scale
                                scaleY = scale
                                val maxShift = (size.width * BACK_SCALE_DELTA / 2f - BACK_EDGE_MARGIN.toPx()).coerceAtLeast(0f)
                                translationX = maxShift * p * if (backFromLeftEdge) 1f else -1f
                                shape = RoundedCornerShape(BACK_CORNER.toPx() * p)
                                clip = p > 0f
                                shadowElevation = BACK_ELEVATION.toPx() * p
                            }
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SubScreenPage(
                            page = page,
                            viewModel = viewModel,
                            uiState = uiState,
                            updateViewModel = updateViewModel,
                            adaptationState = adaptationState,
                            isDark = isDark,
                            onOpen = openSubScreen,
                            onClose = { subScreenStack = subScreenStack.popSubScreen(page) }
                        )
                    }
                }
            }
        }

        // 所有页面的返回都在这里处理。有页面时才注册，这样排在各 Tab 自己的返回拦截之后、优先生效
        if (renderedSubScreens.isNotEmpty()) {
            if (uiState.predictiveBack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PredictiveBackHandler(enabled = subScreenStack.isNotEmpty()) { progress ->
                    backGesturePage = subScreenStack.lastOrNull()
                    backGestureActive = true
                    try {
                        progress.collect { event ->
                            backFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                            backProgress.snapTo(event.progress)
                        }
                        subScreenStack = subScreenStack.dropLast(1)
                    } catch (e: CancellationException) {
                        backScope.launch { backProgress.animateTo(0f, tween(BACK_CANCEL_MS)) }
                        throw e
                    } finally {
                        backGestureActive = false
                    }
                }
            } else {
                BackHandler(enabled = subScreenStack.isNotEmpty()) {
                    subScreenStack = subScreenStack.dropLast(1)
                }
            }
        }

        // 启动时若检测到新版本，弹出更新弹窗（支持忽略此版本、前往更新）
        if (showStartupUpdateDialog && latestRelease != null) {
            StartupUpdateDialog(
                latestRelease = latestRelease,
                onDismiss = { showStartupUpdateDialog = false },
                onNavigateToUpdate = {
                    showStartupUpdateDialog = false
                    openSubScreen(MainSubScreen.Update)
                },
                onIgnore = { version ->
                    viewModel.setIgnoredVersion(version)
                    showStartupUpdateDialog = false
                }
            )
        }
    }
}

@Composable
private fun SubScreenPage(
    page: MainSubScreen,
    viewModel: MainViewModel,
    uiState: AppState,
    updateViewModel: UpdateViewModel,
    adaptationState: AdaptationState,
    isDark: Boolean,
    onOpen: (MainSubScreen) -> Unit,
    onClose: () -> Unit
) {
    when (page) {
        MainSubScreen.EnvData -> EnvDataScreen(uiState, isDark, onOpen, onClose)

        MainSubScreen.SimulationSettings -> SimulationSettingsScreen(uiState, isDark, onOpen, onClose)

        MainSubScreen.AppearanceLanguage -> AppearanceLanguageScreen(viewModel, uiState, isDark, onOpen, onClose)

        MainSubScreen.CoordinateConfig -> AppCoordinateScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = onClose
        )

        MainSubScreen.ScannerMap -> ScannerMapScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.ManageData -> ManageDataScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.Update -> UpdateScreen(
            updateViewModel = updateViewModel,
            viewModel = viewModel,
            isDark = isDark,
            onBack = onClose
        )

        MainSubScreen.LanguageSettings -> LanguageSettingsScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.MapEngineSettings -> MapEngineSettingsScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.MotionRealism -> MotionRealismScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.SystemAdaptation -> SystemAdaptationScreen(
            viewModel = viewModel,
            uiState = uiState,
            state = adaptationState,
            isDark = isDark,
            onOpenHookStatus = { onOpen(MainSubScreen.HookStatus) },
            onOpenFullProgress = { onOpen(MainSubScreen.AdaptationProgress) },
            onClose = onClose
        )

        MainSubScreen.HookStatus -> HookStatusScreen(
            reports = adaptationState.hookReports,
            loading = adaptationState.hookLoading,
            onRefresh = { adaptationState.refreshHookStatus() },
            debugDump = uiState.debugDumpSystemServices,
            onDebugDumpChange = { viewModel.setDebugDumpSystemServices(it) },
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.AdaptationProgress -> AdaptationProgressScreen(
            document = adaptationState.document,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.EnvTokens -> EnvTokensScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.ThemeSettings -> ThemeSettingsScreen(
            viewModel = viewModel,
            uiState = uiState,
            isDark = isDark,
            onClose = onClose
        )

        MainSubScreen.SystemHookApps -> SystemHookAppsScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = onClose
        )
    }
}
