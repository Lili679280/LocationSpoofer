package com.vincenthzr.locationspoofer.ui.screen.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.vincenthzr.locationspoofer.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vincenthzr.locationspoofer.data.model.HookStatusReport
import com.vincenthzr.locationspoofer.data.repository.HookStatusRepository
import com.vincenthzr.locationspoofer.progress.AdaptationProgressSource
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.utils.BackgroundPower
import com.vincenthzr.locationspoofer.utils.SpoofMode
import com.vincenthzr.locationspoofer.viewmodel.openDeveloperOptions
import com.vincenthzr.locationspoofer.viewmodel.setForceMockMode
import org.koin.compose.koinInject
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.confirmRestartHookedApps
import com.vincenthzr.locationspoofer.viewmodel.dismissRestartHookedAppsDialog
import com.vincenthzr.locationspoofer.viewmodel.dismissRootSetupTestResult
import com.vincenthzr.locationspoofer.viewmodel.requestRestartHookedApps
import com.vincenthzr.locationspoofer.viewmodel.testRootSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/** 适配进度文档和 Hook 运行状态，下一级页面也要用，所以放在页面外面 */
@Stable
class AdaptationState(
    context: Context,
    private val hookStatusRepository: HookStatusRepository,
    private val scope: CoroutineScope
) {
    // 主界面一启动就创建了这个对象，文档等第一次打开页面时才读
    private val source by lazy { AdaptationProgressSource(context) }
    private val bundledDocument by lazy { source.local() }
    private var remoteDocument by mutableStateOf<AdaptationProgressSource.Document?>(null)
    val document: AdaptationProgressSource.Document
        get() = remoteDocument ?: bundledDocument
    var loading by mutableStateOf(false)
        private set

    // 全局方案：各系统进程写出的 Hook 运行状态报告
    var hookReports by mutableStateOf<Map<String, HookStatusReport?>>(emptyMap())
        private set
    var hookLoading by mutableStateOf(false)
        private set

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            source.fetchRemote()?.let { remoteDocument = it }
            loading = false
        }
    }

    fun refreshHookStatus() {
        if (hookLoading) return
        hookLoading = true
        scope.launch {
            hookReports = hookStatusRepository.readAll()
            hookLoading = false
        }
    }
}

@Composable
fun rememberAdaptationState(): AdaptationState {
    val context = LocalContext.current
    val hookStatusRepository = koinInject<HookStatusRepository>()
    val scope = rememberCoroutineScope()
    return remember { AdaptationState(context.applicationContext, hookStatusRepository, scope) }
}

/** 运行模式与适配：当前模式、本机适配情况、运行检测，完整进度和 Hook 状态在下一级 */
@Composable
fun SystemAdaptationScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    state: AdaptationState,
    isDark: Boolean = isAppInDarkTheme(),
    onOpenHookStatus: () -> Unit,
    onOpenFullProgress: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val hookReports = state.hookReports
    val hookLoading = state.hookLoading
    LaunchedEffect(Unit) {
        state.refresh()
        state.refreshHookStatus()
    }

    // 从开发者选项回来时重新检查是否已被选为模拟位置信息应用
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeCount++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasMockPermission = remember(resumeCount) { SpoofMode.hasMockLocationPermission(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(
                title = stringResource(R.string.run_mode_title),
                subtitle = stringResource(R.string.run_mode_desc),
                isDark = isDark,
                onBack = onClose
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 当前模式、原因和强制免 Root
                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = stringResource(if (uiState.isMockMode) R.string.run_mode_current_mock else R.string.run_mode_current_root),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isMockMode) AccentOrange else AccentGreen
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            when {
                                !uiState.isMockMode -> R.string.run_mode_reason_root
                                uiState.forceMockMode -> R.string.run_mode_reason_forced
                                else -> R.string.run_mode_reason_auto
                            }
                        ),
                        fontSize = 13.sp,
                        color = AppColors.textSecondary(isDark)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.run_mode_force_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.run_mode_force_desc),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = uiState.forceMockMode, onCheckedChange = { viewModel.setForceMockMode(it) })
                    }
                }

                DeviceAdaptationCard(state.document.markdown)

                // 运行检测：原"Root 方案"页里真正有用的两个操作搬到这里（Root 方案选择本身不影响任何功能，已删除）
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Devices,
                                null,
                                tint = AccentBlue,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.runtime_checks_title),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    if (uiState.isMockMode) {
                        // 免 Root 模式没有 Root 和 Hook 可测，只看它自己依赖的两项系统设置
                        CheckActionRow(
                            title = stringResource(R.string.mock_permission_title),
                            // 没选的时候直接给出步骤
                            description = if (hasMockPermission) {
                                stringResource(R.string.mock_permission_granted)
                            } else {
                                stringResource(R.string.mock_permission_missing) + "\n" + stringResource(R.string.mock_permission_steps)
                            },
                            running = false,
                            tint = if (hasMockPermission) AccentGreen else AccentOrange,
                            isDark = isDark,
                            onClick = { viewModel.openDeveloperOptions() }
                        )

                        if (BackgroundPower.needsSetup) {
                            Spacer(Modifier.height(8.dp))

                            CheckActionRow(
                                title = stringResource(R.string.background_power_title),
                                description = stringResource(R.string.background_power_check_desc),
                                running = false,
                                tint = AccentOrange,
                                isDark = isDark,
                                onClick = { BackgroundPower.openSettings(context) }
                            )
                        }
                    } else {
                        // 测试 Root 权限与框架配置通道
                        CheckActionRow(
                            title = stringResource(R.string.test_root_setup_title),
                            description = stringResource(R.string.test_root_setup_desc),
                            running = uiState.isTestingRootSetup,
                            tint = AccentBlue,
                            isDark = isDark,
                            onClick = { if (!uiState.isTestingRootSetup) viewModel.testRootSetup() }
                        )

                        Spacer(Modifier.height(8.dp))

                        // 重启目标应用，让它们加载当前模块并重新订阅框架配置
                        CheckActionRow(
                            title = stringResource(R.string.restart_hooked_apps_title),
                            description = stringResource(R.string.restart_hooked_apps_desc),
                            running = uiState.isRestartingHookedApps,
                            tint = AccentOrange,
                            isDark = isDark,
                            onClick = { if (!uiState.isRestartingHookedApps) viewModel.requestRestartHookedApps() }
                        )
                    }
                }

                if (!uiState.isMockMode) {
                    val summary = hookStatusSummary(hookReports)
                    NavigationRowCard(
                        title = stringResource(R.string.hook_status_title),
                        chip = when {
                            hookLoading && hookReports.isEmpty() -> null
                            summary == null -> stringResource(R.string.hook_status_summary_none)
                            else -> stringResource(R.string.hook_status_summary, summary.first, summary.second)
                        },
                        chipColor = when {
                            summary == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            summary.first == summary.second -> AccentGreen
                            else -> AccentOrange
                        },
                        onClick = onOpenHookStatus
                    )
                }

                if (uiState.isMockMode) {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                        Text(
                            text = stringResource(R.string.run_mode_limits_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.run_mode_limits),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = AppColors.textSecondary(isDark)
                        )
                    }
                }

                NavigationRowCard(
                    title = stringResource(R.string.adaptation_progress_full),
                    onClick = onOpenFullProgress
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val testResult = uiState.rootSetupTestResult
    if (testResult != null) {
        RootSetupTestResultDialog(
            result = testResult,
            isDark = isDark,
            onDismiss = { viewModel.dismissRootSetupTestResult() }
        )
    }

    val appsToRestart = uiState.hookedAppsToRestart
    if (appsToRestart != null) {
        RestartHookedAppsConfirmDialog(
            apps = appsToRestart,
            isDark = isDark,
            onDismiss = { viewModel.dismissRestartHookedAppsDialog() },
            onConfirm = {
                viewModel.confirmRestartHookedApps { count ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.restart_hooked_apps_done, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

/** 运行检测卡片里的一行操作：标题 + 说明 + 右侧箭头，执行中显示转圈 */
@Composable
private fun CheckActionRow(
    title: String,
    description: String,
    running: Boolean,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        if (running) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** 一行可点击的卡片：标题 + 可选的右侧状态文字 + 箭头，点进二级页面 */
@Composable
private fun NavigationRowCard(
    title: String,
    chip: String? = null,
    chipColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (chip != null) {
                Text(chip, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = chipColor)
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 设置子页面顶部：圆形返回按钮 + 标题（+ 副标题），右侧可放操作按钮 */
@Composable
internal fun SettingsPageHeader(
    title: String,
    subtitle: String?,
    isDark: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF22272E) else Color.White)
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE5E8EC),
                    shape = CircleShape
                )
                .noRippleClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = if (isDark) Color.White else Color(0xFF1A1D20),
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        actions()
    }
}
