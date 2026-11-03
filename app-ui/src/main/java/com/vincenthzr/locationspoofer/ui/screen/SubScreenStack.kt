package com.vincenthzr.locationspoofer.ui.screen

enum class MainSubScreen {
    EnvData,
    SimulationSettings,
    AppearanceLanguage,
    CoordinateConfig,
    ScannerMap,
    ManageData,
    Update,
    LanguageSettings,
    MapEngineSettings,
    SystemAdaptation,
    HookStatus,
    AdaptationProgress,
    MotionRealism,
    EnvTokens,
    ThemeSettings,
    SystemHookApps
}

/** 打开页面：已经打开的不重复入栈 */
internal fun List<MainSubScreen>.pushSubScreen(page: MainSubScreen): List<MainSubScreen> =
    if (page in this) this else this + page

/** 关闭页面：只有最上面的页面能关，页面滑出途中再点一次返回不会多退一层 */
internal fun List<MainSubScreen>.popSubScreen(page: MainSubScreen): List<MainSubScreen> =
    if (lastOrNull() == page) dropLast(1) else this
