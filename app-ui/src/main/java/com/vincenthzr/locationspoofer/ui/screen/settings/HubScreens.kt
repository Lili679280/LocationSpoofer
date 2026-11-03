package com.vincenthzr.locationspoofer.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.screen.AppCoordinateConfigCard
import com.vincenthzr.locationspoofer.ui.screen.MainSubScreen
import com.vincenthzr.locationspoofer.ui.screen.ManageDataCard
import com.vincenthzr.locationspoofer.ui.screen.ScannerMapCard
import com.vincenthzr.locationspoofer.ui.screen.SystemHookAppsConfigCard
import com.vincenthzr.locationspoofer.ui.screen.tabs.InfoEntryCard
import com.vincenthzr.locationspoofer.ui.screen.tabs.currentLanguageName
import com.vincenthzr.locationspoofer.ui.screen.tabs.currentRealismLevelName
import com.vincenthzr.locationspoofer.ui.screen.tabs.currentThemeModeName
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel

/** 环境数据：采集、管理和在线数据源 */
@Composable
fun EnvDataScreen(uiState: AppState, isDark: Boolean, onOpen: (MainSubScreen) -> Unit, onClose: () -> Unit) {
    HubPage(
        title = stringResource(R.string.env_data_title),
        subtitle = stringResource(R.string.env_data_desc),
        isDark = isDark,
        onClose = onClose
    ) {
        ScannerMapCard(isDark = isDark, uiState = uiState, onClick = { onOpen(MainSubScreen.ScannerMap) })
        ManageDataCard(isDark = isDark, onClick = { onOpen(MainSubScreen.ManageData) })
        InfoEntryCard(
            icon = Icons.Rounded.VpnKey,
            tint = AccentBlue,
            title = stringResource(R.string.env_datasource_tokens_title),
            description = stringResource(R.string.env_datasource_tokens_desc),
            isDark = isDark,
            onClick = { onOpen(MainSubScreen.EnvTokens) }
        )
    }
}

/** 模拟设置：运动真实度、对哪些应用模拟、应用坐标系 */
@Composable
fun SimulationSettingsScreen(uiState: AppState, isDark: Boolean, onOpen: (MainSubScreen) -> Unit, onClose: () -> Unit) {
    HubPage(
        title = stringResource(R.string.simulation_settings_title),
        subtitle = stringResource(R.string.simulation_settings_desc),
        isDark = isDark,
        onClose = onClose
    ) {
        // 免 Root 模式下哪些设置不生效
        if (uiState.isMockMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentOrange.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.simulation_settings_mock_notice),
                    fontSize = 12.5.sp,
                    color = AccentOrange
                )
            }
        }
        InfoEntryCard(
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            tint = AccentOrange,
            title = stringResource(R.string.motion_realism_title),
            description = stringResource(R.string.motion_realism_desc),
            previewChip = currentRealismLevelName(uiState),
            isDark = isDark,
            onClick = { onOpen(MainSubScreen.MotionRealism) }
        )
        SystemHookAppsConfigCard(uiState = uiState, isDark = isDark, onClick = { onOpen(MainSubScreen.SystemHookApps) })
        AppCoordinateConfigCard(isDark = isDark, onClick = { onOpen(MainSubScreen.CoordinateConfig) })
    }
}

/** 外观与语言 */
@Composable
fun AppearanceLanguageScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onOpen: (MainSubScreen) -> Unit,
    onClose: () -> Unit
) {
    HubPage(
        title = stringResource(R.string.appearance_language_title),
        subtitle = null,
        isDark = isDark,
        onClose = onClose
    ) {
        InfoEntryCard(
            icon = Icons.Rounded.Palette,
            tint = AccentBlue,
            title = stringResource(R.string.theme_title),
            description = stringResource(R.string.theme_desc),
            previewChip = currentThemeModeName(uiState),
            isDark = isDark,
            onClick = { onOpen(MainSubScreen.ThemeSettings) }
        )
        InfoEntryCard(
            icon = Icons.Rounded.Language,
            tint = AccentGreen,
            title = stringResource(R.string.select_language),
            description = stringResource(R.string.switch_language_desc),
            previewChip = currentLanguageName(viewModel),
            isDark = isDark,
            onClick = { onOpen(MainSubScreen.LanguageSettings) }
        )
    }
}

@Composable
private fun HubPage(
    title: String,
    subtitle: String?,
    isDark: Boolean,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
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
            SettingsPageHeader(title = title, subtitle = subtitle, isDark = isDark, onBack = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}
