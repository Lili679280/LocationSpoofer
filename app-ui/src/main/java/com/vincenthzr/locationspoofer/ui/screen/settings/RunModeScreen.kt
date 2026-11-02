package com.vincenthzr.locationspoofer.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import com.vincenthzr.locationspoofer.utils.SpoofMode
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.dismissMockSetupDialog
import com.vincenthzr.locationspoofer.viewmodel.openDeveloperOptions
import com.vincenthzr.locationspoofer.viewmodel.setForceMockMode
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/** 运行模式：当前用 Root（LSPosed Hook）还是免 Root（系统模拟位置），以及免 Root 模式需要的设置和限制 */
@Composable
fun RunModeScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean = isSystemInDarkTheme(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // 从开发者选项回来时重新检查是否已被选为模拟位置信息应用
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeCount++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasMockPermission = remember(resumeCount) { SpoofMode.hasMockLocationPermission(context) }

    BackHandler(onBack = onClose)

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
                        .noRippleClickable(onClick = onClose),
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

                Column {
                    Text(
                        text = stringResource(R.string.run_mode_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.run_mode_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 当前模式与原因
                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    val tint = if (uiState.isMockMode) AccentOrange else AccentGreen
                    Text(
                        text = stringResource(if (uiState.isMockMode) R.string.run_mode_current_mock else R.string.run_mode_current_root),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint
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
                }

                // 强制免 Root
                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
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

                // 模拟位置信息应用
                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasMockPermission) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (hasMockPermission) AccentGreen else AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.mock_permission_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(if (hasMockPermission) R.string.mock_permission_granted else R.string.mock_permission_missing),
                            fontSize = 12.sp,
                            color = if (hasMockPermission) AccentGreen else AccentOrange
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.mock_permission_steps),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = AppColors.textSecondary(isDark)
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentBlue)
                            .noRippleClickable { viewModel.openDeveloperOptions() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.open_developer_options),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 限制
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

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 免 Root 模式开始模拟时还没设置"模拟位置信息应用"：说明步骤并提供跳转 */
@Composable
fun MockSetupDialogHost(viewModel: MainViewModel, uiState: AppState) {
    if (!uiState.showMockSetupDialog) return
    AlertDialog(
        onDismissRequest = { viewModel.dismissMockSetupDialog() },
        title = { Text(stringResource(R.string.mock_setup_dialog_title)) },
        text = {
            Text(
                text = stringResource(R.string.mock_setup_dialog_text) + "\n\n" + stringResource(R.string.mock_permission_steps),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.openDeveloperOptions() }) {
                Text(stringResource(R.string.open_developer_options))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissMockSetupDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
