package com.vincenthzr.locationspoofer.data.model

import androidx.annotation.StringRes
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.utils.GaitTemplate
import com.vincenthzr.locationspoofer.utils.AltitudeModel
import com.vincenthzr.locationspoofer.utils.MotionRealism

enum class WifiLoadStatus { IDLE, LOADING, DONE }

enum class SimMode(@StringRes val labelResId: Int, val speedMs: Double) {
    STILL(R.string.still, 0.0),
    WALKING(R.string.walking, 1.4),
    RUNNING(R.string.running, 3.0),
    CYCLING(R.string.cycling, 5.5),
    DRIVING(R.string.driving, 15.0),
    CUSTOM(R.string.custom, 0.0)
}

enum class SearchMode {
    NETWORK,
    LOCAL
}

/** 路线规划阶段 */
enum class RoutePlanStage {
    /** 未开始，默认状态 */
    IDLE,

    /** 正在点击地图添加路点 */
    SELECTING,

    /** 已结束选点，等待配置并启动 */
    READY,

    /** 路线模拟运行中 */
    RUNNING
}

/**
 * 实际使用的地图引擎。手动选了哪家就用哪家；"自动"时选填了 Key 的：高德优先，其次百度。
 * 谷歌地图的显示 Key 只能编译时写进 manifest，"自动"不会选它。两家都没填时仍用高德（底图通常还能显示）。
 * 地图显示、地点搜索、路线规划都以它为准，保证三者是同一家。
 */
val AppState.effectiveMapEngine: MapEngine
    get() = when {
        mapEngine != MapEngine.AUTO -> mapEngine
        amapApiKey.isNotBlank() -> MapEngine.AMAP
        baiduApiKey.isNotBlank() -> MapEngine.BAIDU
        else -> MapEngine.AMAP
    }

/** 让主地图把镜头移到指定位置（GCJ-02）的一次性请求；[id] 保证同一坐标重复请求也会触发 */
data class MapRecenterRequest(val lat: Double, val lng: Double, val id: Long = System.nanoTime())

/** 真实路线规划按哪种出行方式算路 */
enum class RouteTravelMode(@StringRes val labelResId: Int) {
    WALK(R.string.route_travel_walk),
    DRIVE(R.string.route_travel_drive);

    companion object {
        /** 选速度档位时顺带切换出行方式；自定义速度不改，保留用户上次的选择 */
        fun suggestedFor(mode: SimMode): RouteTravelMode? = when (mode) {
            SimMode.WALKING, SimMode.RUNNING -> WALK
            SimMode.CYCLING, SimMode.DRIVING -> DRIVE
            else -> null
        }
    }
}

/** 路线运行模式 */
enum class RouteRunMode {
    /** 手动模式：摇杆控制移动方向和速度 */
    MANUAL,

    /** 循环模式：按路线自动来回移动 */
    LOOP
}

sealed interface GaitRecordingState {
    data object Idle : GaitRecordingState
    data class Countdown(val secondsLeft: Int) : GaitRecordingState
    data class Recording(val progress: Float) : GaitRecordingState
    data object Processing : GaitRecordingState
    data class Failed(val reason: GaitTemplate.Reason) : GaitRecordingState
}

enum class AppMapType {
    NORMAL,
    SATELLITE,
    MAP_3D
}

data class AppState(
    val mapType: AppMapType = AppMapType.NORMAL,
    val mapEngine: MapEngine = MapEngine.AUTO,
    val isInitializing: Boolean = true,
    val isLanguageSet: Boolean = true, // 默认为 true，以避免在不需要时发生闪烁
    val currentLanguage: String = "",
    val hasRootAccess: Boolean = false,
    val debugDumpSystemServices: Boolean = false,
    val realismLevel: Int = MotionRealism.DEFAULT_LEVEL_ID,
    val speedFluctuationPct: Int = MotionRealism.DEFAULT_SPEED_FLUCTUATION_PCT,
    /** 已录制步态模板的步频（步/分钟），null 表示尚未录制 */
    val gaitTemplateCadence: Int? = null,
    val gaitTemplateStrides: Int = 0,
    val useGaitTemplate: Boolean = false,
    val gaitRecording: GaitRecordingState = GaitRecordingState.Idle,
    val keepLastMapPosition: Boolean = true,
    val isTestingRootSetup: Boolean = false,
    val rootSetupTestResult: RootSetupTestResult? = null,
    val isRestartingHookedApps: Boolean = false,
    /** 非空即触发"确认重启应用"弹窗；内容来自 lsposedManager.getHookedApps() */
    val hookedAppsToRestart: List<AppInfoItem>? = null,
    val isLSPosedActive: Boolean = false,
    val longitudeInput: String = "",
    val latitudeInput: String = "",
    val showCoordinateError: Boolean = false,
    val isSavingConfig: Boolean = false,
    val isSpoofingActive: Boolean = false,
    val wifiLoadStatus: WifiLoadStatus = WifiLoadStatus.IDLE,
    val wifiApCount: Int = 0,
    val savedLocations: List<SavedLocation> = emptyList(),
    val searchMode: SearchMode = SearchMode.NETWORK,
    val searchResults: List<SavedLocation> = emptyList(),
    val simBearing: Float = 0f,
    val savedRoutes: List<SavedRoute> = emptyList(),
    // 路线规划
    val routePoints: List<RoutePoint> = emptyList(),
    val routePlanStage: RoutePlanStage = RoutePlanStage.IDLE,
    /** 路线运行模式（手动 / 循环） */
    val routeRunMode: RouteRunMode = RouteRunMode.LOOP,
    /** 循环模式使用的速度 */
    val routeSimMode: SimMode = SimMode.WALKING,
    /** 自定义速度 (m/s)，仅当 routeSimMode == CUSTOM 时使用 */
    val customSpeedMs: Double = 3.0,
    /** 是否开启步频模拟 */
    val enableStepSimulation: Boolean = true,
    /** 步频设定 (SPM, 步/分钟) */
    val stepCadenceSpm: Int = 165,
    /** 是否根据速度自动计算最佳步频 */
    val isAutoCadence: Boolean = true,
    /** 是否使用真实路线规划 */
    val useRealRoute: Boolean = false,
    /** 主地图待执行的镜头移动（例如停止模拟后回到真实位置），由 MainScaffoldScreen 执行 */
    val mapRecenterRequest: MapRecenterRequest? = null,
    /** 真实路线规划的出行方式，默认跟随默认的步行速度 */
    val routeTravelMode: RouteTravelMode = RouteTravelMode.WALK,
    /** 到达终点后是否停下 */
    val stopAtDestination: Boolean = false,
    /** 是否正在向地图API请求真实路线 */
    val isFetchingRoute: Boolean = false,
    /** 首页地图已确认的选点（点击地图后出现确认按钮，确认后填充坐标） */
    val mapConfirmedPoint: Pair<Double, Double>? = null,
    val amapApiKey: String = "",
    val baiduApiKey: String = "",
    val googleApiKey: String = "",
    val wigleToken: String = "",
    val opencellidToken: String = "",
    val appSha1: String = "",
    val appCoordinateSystems: Map<String, String> = emptyMap(),
    val isContinuousScanning: Boolean = false,
    val isStoppingCollection: Boolean = false,
    val pendingCollectionLocations: List<com.vincenthzr.locationspoofer.data.db.LocationRecord> = emptyList(),
    val isSavingCollectionInfo: Boolean = false,
    val isRouteCollection: Boolean = false,
    val isDrawingCollectionRoute: Boolean = false,
    val collectionRoutePoints: List<RoutePoint> = emptyList(),
    val pendingCollectionRoute: com.vincenthzr.locationspoofer.data.db.CollectionRouteRecord? = null,
    val selectedCollectionRouteId: Long? = null,
    val environmentRecordCount: Int = 0,
    val scannedWifiCount: Int = 0,
    val scannedCellCount: Int = 0,
    val scannedBluetoothCount: Int = 0,
    val hookedApps: List<AppInfoItem> = emptyList(),
    /** system_server 级定位 Hook 勾选生效的目标 App 包名集合 */
    val systemHookPackages: Set<String> = emptySet(),
    /** 是否开启全局模拟模式（除自身与系统核心组件外对全设备所有应用生效） */
    val isSystemHookGlobalMode: Boolean = false,
    /** "系统级模拟应用"选择页展示的全量已安装 App 列表，进入该页时按需加载 */
    val installedAppsForSystemHook: List<AppInfoItem> = emptyList(),
    val isLoadingInstalledApps: Boolean = false,
    // 采集到的本地环境数据
    val collectedWifiJson: String = "[]",
    val collectedCellJson: String = "[]",
    val collectedBluetoothJson: String = "[]",
    // 模拟开关
    val mockWifi: Boolean = true,
    val mockCell: Boolean = true,
    val mockBluetooth: Boolean = true,
    val enableJitter: Boolean = true,
    val restartAppsOnSpoof: Boolean = true,
    val altitudeInput: String = "0.0",
    val altitudeVariationM: Int = AltitudeModel.DEFAULT_VARIATION_M,
    val satelliteCountInput: String = "20",
    val canMockWifi: Boolean = false,
    val canMockCell: Boolean = false,
    val canMockBluetooth: Boolean = false,

    // 锁定选中的本地采集点位
    val pinnedCollectedLocationId: Long? = null,
    val pinnedLocationName: String? = null,

    // 是否接收测试版更新 (Beta 通道)
    val checkBetaUpdates: Boolean = false
)
