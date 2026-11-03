package com.vincenthzr.locationspoofer

import com.vincenthzr.locationspoofer.ui.components.AlertDialog
import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import com.vincenthzr.locationspoofer.ui.screen.InitializingScreen
import com.vincenthzr.locationspoofer.ui.screen.LanguageSelectionScreen
import com.vincenthzr.locationspoofer.ui.theme.AppColorSchemeDark
import com.vincenthzr.locationspoofer.ui.theme.AppColorSchemeLight
import com.vincenthzr.locationspoofer.ui.theme.LocalAppDarkTheme
import com.vincenthzr.locationspoofer.ui.R as UiR
import com.vincenthzr.locationspoofer.utils.BackgroundPower
import com.vincenthzr.locationspoofer.utils.LocaleUtils
import com.vincenthzr.locationspoofer.utils.SettingsManager
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.getSavedLanguage
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val KEY_BACKGROUND_POWER_OPENED = "background_power_opened"
private const val STATE_BACKGROUND_POWER_DISMISSED = "background_power_dismissed"

// 和 enableEdgeToEdge 默认的导航栏遮罩一样
private val LIGHT_NAV_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION]
                ?: (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            if (locationGranted) checkBackgroundLocation()
            // 悬浮窗权限与运行时权限无关，被拒绝时也照样检查
            requestOverlayPermissionIfNeeded()
            if (locationGranted) requestIgnoreBatteryOptimizations()
            permissionsRequested = true
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isLangSet = prefs.getBoolean("is_language_set", false)
        val lang = if (isLangSet) prefs.getString("language", "") ?: "" else ""

        val context = if (lang.isNotEmpty()) {
            LocaleUtils.wrap(newBase, lang)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 仅在初始启动或语言变更后同步系统 Locale
        // AppCompatDelegate.setApplicationLocales 会自动处理持久化
        val savedLang = viewModel.getSavedLanguage()
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (savedLang.isNotEmpty() && (currentLocales.isEmpty || currentLocales.toLanguageTags() != savedLang)) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
            )
        }

        // 语言切换、旋转等导致的重建不重复跳转授权页，只在冷启动时检查一次
        overlayPromptHandled = savedInstanceState != null
        checkAndRequestPermissions()

        backgroundPowerPromptDismissed = savedInstanceState?.getBoolean(STATE_BACKGROUND_POWER_DISMISSED) == true

        setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val systemDark = isSystemInDarkTheme()
                val isDark = when (uiState.themeMode) {
                    SettingsManager.THEME_MODE_LIGHT -> false
                    SettingsManager.THEME_MODE_DARK -> true
                    else -> systemDark
                }
                // 状态栏图标的深浅默认只看系统，手动选了深浅色要跟着改
                DisposableEffect(isDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { isDark }
                    )
                    onDispose { }
                }
                val colorScheme = if (isDark) AppColorSchemeDark else AppColorSchemeLight
                // 窗口背景和系统启动画面也要和软件的深浅色一致，否则冷启动会闪一下系统的颜色
                SideEffect { window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb())) }
                LaunchedEffect(uiState.themeMode) { applyWindowNightMode(uiState.themeMode) }
                val baseDensity = LocalDensity.current
                val appDensity = remember(baseDensity.density, baseDensity.fontScale, uiState.adaptiveScale, uiState.pageScale) {
                    Density(
                        baseDensity.density * uiState.adaptiveScale * uiState.pageScale,
                        baseDensity.fontScale.coerceAtMost(1.15f)
                    )
                }

                // 核心：在 Compose 层级内部通过 CompositionLocalProvider 动态刷新
                // 使用 remember(uiState.currentLanguage) 确保语言切换时重新计算 Context
                val context = LocalContext.current
                val wrappedContext = remember(uiState.currentLanguage) {
                    if (uiState.currentLanguage.isNotEmpty()) {
                        LocaleUtils.wrap(context, uiState.currentLanguage)
                    } else {
                        context
                    }
                }
                val configuration = wrappedContext.resources.configuration

                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalConfiguration provides configuration,
                    LocalDensity provides appDensity,
                    LocalAppDarkTheme provides isDark
                ) {
                    MaterialTheme(colorScheme = colorScheme) {
                        top.yukonga.miuix.kmp.theme.MiuixTheme(
                            colors = if (isDark) {
                                top.yukonga.miuix.kmp.theme.darkColorScheme()
                            } else {
                                top.yukonga.miuix.kmp.theme.lightColorScheme()
                            }
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                MainScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    isDark = isDark
                                )
                                if (!uiState.isInitializing) BackgroundPowerPrompt()
                            }
                        }
                    }
                }
            }
        }
    }

    /** 让系统下次画启动画面时就用选好的深浅色。Android 12 以下没有这个接口，只能靠窗口背景 */
    private fun applyWindowNightMode(themeMode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (themeMode) {
                SettingsManager.THEME_MODE_LIGHT -> UiModeManager.MODE_NIGHT_NO
                SettingsManager.THEME_MODE_DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            runtimePermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            checkBackgroundLocation()
            requestOverlayPermissionIfNeeded()
            requestIgnoreBatteryOptimizations()
            permissionsRequested = true
        }
    }

    private var permissionsRequested = false
    private var backgroundPowerPromptDismissed = false
    private var showBackgroundPowerPrompt by mutableStateOf(false)

    /**
     * vivo 要允许后台高耗电，否则免 Root 模式会被冻结。系统权限弹窗和授权页都在上面时窗口没有焦点，
     * 拿回焦点说明它们都结束了，这时才提示。查不到开关状态：每次启动都提示，点过"去设置"才不再提示
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !permissionsRequested || backgroundPowerPromptDismissed || !BackgroundPower.needsSetup) return
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_BACKGROUND_POWER_OPENED, false)) showBackgroundPowerPrompt = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 选语言、旋转屏幕导致的重建不算新的启动
        outState.putBoolean(STATE_BACKGROUND_POWER_DISMISSED, backgroundPowerPromptDismissed)
    }

    @Composable
    private fun BackgroundPowerPrompt() {
        if (!showBackgroundPowerPrompt) return
        AlertDialog(
            onDismissRequest = { dismissBackgroundPowerPrompt() },
            title = { Text(stringResource(UiR.string.background_power_title)) },
            text = { Text(stringResource(UiR.string.background_power_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundPowerPrompt = false
                    getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                        .putBoolean(KEY_BACKGROUND_POWER_OPENED, true).apply()
                    BackgroundPower.openSettings(this@MainActivity)
                }) {
                    Text(stringResource(UiR.string.background_power_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissBackgroundPowerPrompt() }) {
                    Text(stringResource(UiR.string.background_power_later))
                }
            }
        )
    }

    private fun dismissBackgroundPowerPrompt() {
        showBackgroundPowerPrompt = false
        backgroundPowerPromptDismissed = true
    }

    private var overlayPromptHandled = false

    /** 悬浮摇杆需要"显示在其他应用上层"权限：启动时检查，没有就直接跳到系统授权页 */
    private fun requestOverlayPermissionIfNeeded() {
        if (overlayPromptHandled || android.provider.Settings.canDrawOverlays(this)) return
        overlayPromptHandled = true
        android.widget.Toast.makeText(
            this,
            getString(com.vincenthzr.locationspoofer.ui.R.string.overlay_permission_on_launch),
            android.widget.Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    @android.annotation.SuppressLint("BatteryLife") // 后台持续模拟需要豁免电池优化，本应用不上架 Play 商店
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:$packageName".toUri())
            )
        } catch (_: Exception) {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    uiState: com.vincenthzr.locationspoofer.data.model.AppState,
    isDark: Boolean
) {
    when {
        uiState.isInitializing -> InitializingScreen(isDark)
        !uiState.isLanguageSet -> LanguageSelectionScreen(viewModel)
        else -> com.vincenthzr.locationspoofer.ui.screen.MainScaffoldScreen(
            viewModel = viewModel,
            uiState = uiState
        )
    }
    com.vincenthzr.locationspoofer.ui.screen.CollectionInfoDialogHost(viewModel, uiState)
    com.vincenthzr.locationspoofer.ui.screen.settings.MockSetupDialogHost(viewModel, uiState)
}
