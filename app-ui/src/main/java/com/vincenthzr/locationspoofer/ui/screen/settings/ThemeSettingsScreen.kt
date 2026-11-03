package com.vincenthzr.locationspoofer.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.ui.theme.isAppInDarkTheme
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import com.vincenthzr.locationspoofer.utils.SettingsManager
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.setFloatingBottomBar
import com.vincenthzr.locationspoofer.viewmodel.setLiquidGlass
import com.vincenthzr.locationspoofer.viewmodel.setLiquidGlassStrength
import com.vincenthzr.locationspoofer.viewmodel.setPageScale
import com.vincenthzr.locationspoofer.viewmodel.setPredictiveBack
import com.vincenthzr.locationspoofer.viewmodel.setThemeMode
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/** 主题：深色模式、底栏样式和液态玻璃、界面缩放、预测性返回 */
@Composable
fun ThemeSettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean = isAppInDarkTheme(),
    onClose: () -> Unit
) {
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
                title = stringResource(R.string.theme_title),
                subtitle = stringResource(R.string.theme_desc),
                isDark = isDark,
                onBack = onClose
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = stringResource(R.string.theme_mode_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SettingsManager.THEME_MODE_SYSTEM to R.string.theme_mode_system,
                            SettingsManager.THEME_MODE_LIGHT to R.string.theme_mode_light,
                            SettingsManager.THEME_MODE_DARK to R.string.theme_mode_dark
                        ).forEach { (mode, label) ->
                            val selected = uiState.themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                    .noRippleClickable { viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(label),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    ThemeSwitchRow(
                        title = stringResource(R.string.theme_floating_bar_title),
                        description = stringResource(R.string.theme_floating_bar_desc),
                        checked = uiState.floatingBottomBar,
                        onCheckedChange = { viewModel.setFloatingBottomBar(it) }
                    )
                    // 液态玻璃只画在悬浮底栏上
                    if (uiState.floatingBottomBar) {
                        Spacer(Modifier.height(16.dp))
                        ThemeSwitchRow(
                            title = stringResource(R.string.theme_glass_title),
                            description = stringResource(R.string.theme_glass_desc),
                            checked = uiState.liquidGlass,
                            onCheckedChange = { viewModel.setLiquidGlass(it) }
                        )
                        if (uiState.liquidGlass) {
                            Spacer(Modifier.height(12.dp))
                            ThemeSliderRow(
                                title = stringResource(R.string.theme_glass_strength),
                                value = uiState.liquidGlassStrength,
                                range = SettingsManager.GLASS_STRENGTH_MIN..SettingsManager.GLASS_STRENGTH_MAX,
                                steps = 5,
                                onValueChangeFinished = { viewModel.setLiquidGlassStrength(it) }
                            )
                        }
                    }
                }

                MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                    Text(
                        text = stringResource(R.string.theme_page_scale_desc),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.height(10.dp))
                    // 松手才生效：拖动时整个界面跟着变大变小，滑块也会从手指下跑开
                    ThemeSliderRow(
                        title = stringResource(R.string.theme_page_scale_title),
                        value = uiState.pageScale,
                        range = SettingsManager.PAGE_SCALE_MIN..SettingsManager.PAGE_SCALE_MAX,
                        steps = 5,
                        onValueChangeFinished = { viewModel.setPageScale(it) }
                    )
                }

                // 系统从 Android 14 起才把返回手势的进度交给软件
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, insideMargin = PaddingValues(16.dp)) {
                        ThemeSwitchRow(
                            title = stringResource(R.string.theme_predictive_back_title),
                            description = stringResource(R.string.theme_predictive_back_desc),
                            checked = uiState.predictiveBack,
                            onCheckedChange = { viewModel.setPredictiveBack(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${(sliderValue * 100).roundToInt()}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AccentBlue
        )
    }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChangeFinished(sliderValue) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = AccentBlue,
            activeTrackColor = AccentBlue,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            activeTickColor = Color.White,
            inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    )
}
