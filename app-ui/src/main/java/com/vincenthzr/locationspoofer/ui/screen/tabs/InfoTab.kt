package com.vincenthzr.locationspoofer.ui.screen.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.BuildConfig
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.data.model.MapEngine
import com.vincenthzr.locationspoofer.progress.AdaptationProgress
import com.vincenthzr.locationspoofer.progress.AdaptationProgressSource
import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.ui.screen.LANGUAGES
import com.vincenthzr.locationspoofer.ui.screen.UpdateCheckCard
import com.vincenthzr.locationspoofer.ui.screen.isNewerVersion
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateUiState
import com.vincenthzr.locationspoofer.viewmodel.getSavedLanguage
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun InfoTab(
    viewModel: MainViewModel,
    uiState: AppState,
    updateUiState: UpdateUiState? = null,
    tabBarHeight: Dp = 90.dp,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToMapEngine: () -> Unit = {},
    onNavigateToSystemAdaptation: () -> Unit = {},
    onNavigateToMotionRealism: () -> Unit = {},
    onNavigateToEnvTokens: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    val currentVersion = BuildConfig.VERSION_NAME
    val targetReleases = remember(updateUiState?.releases, uiState.checkBetaUpdates) {
        val list = updateUiState?.releases ?: emptyList()
        if (uiState.checkBetaUpdates) list else list.filter { !it.isPrerelease }
    }
    val latestRelease = targetReleases.firstOrNull()
    val hasNewVersion = remember(targetReleases) {
        latestRelease != null && isNewerVersion(latestRelease.versionName, currentVersion)
    }

    // 获取当前生效的语言名称
    val defaultLangText = stringResource(R.string.default_language)
    val savedLangCode = viewModel.getSavedLanguage()
    val currentLangName = remember(savedLangCode, defaultLangText) {
        LANGUAGES.firstOrNull { it.code == savedLangCode }?.nativeName ?: defaultLangText
    }

    // 本机在适配进度表里的状态（只读本地缓存 / 内置文档，不联网），非全局方案不依赖系统适配，不显示
    val progressContext = LocalContext.current
    val statusVerified = stringResource(R.string.progress_status_verified)
    val statusPartial = stringResource(R.string.progress_status_partial)
    val statusBroken = stringResource(R.string.progress_status_broken)
    val statusUnverified = stringResource(R.string.progress_status_unverified)
    val adaptationStatusChip = remember {
        val markdown = AdaptationProgressSource(progressContext).local().markdown
        val family = VendorProfile.current.family
        when (AdaptationProgress.findGlobalSystem(markdown, family.progressKeywords)?.status) {
            AdaptationProgress.Status.VERIFIED -> statusVerified
            AdaptationProgress.Status.PARTIAL -> statusPartial
            AdaptationProgress.Status.BROKEN -> statusBroken
            else -> statusUnverified
        }
    }

    val realismLevelNames = listOf(
        stringResource(R.string.realism_level_off),
        stringResource(R.string.realism_level_low),
        stringResource(R.string.realism_level_medium),
        stringResource(R.string.realism_level_high)
    )
    val currentRealismLevelName = realismLevelNames.getOrNull(uiState.realismLevel) ?: realismLevelNames[2]

    // 获取当前地图引擎名称
    val autoText = stringResource(R.string.map_engine_auto)
    val amapText = stringResource(R.string.map_engine_amap)
    val baiduText = stringResource(R.string.map_engine_baidu)
    val googleText = stringResource(R.string.map_engine_google)
    val currentEngineName = remember(uiState.mapEngine, autoText, amapText, baiduText, googleText) {
        when (uiState.mapEngine) {
            MapEngine.AUTO -> autoText
            MapEngine.AMAP -> amapText
            MapEngine.BAIDU -> baiduText
            MapEngine.GOOGLE -> googleText
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = tabBarHeight + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部软件品牌：图标在上，名称和简介在下，居中
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 24.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 软件图标：自己画一块不透明的白色圆角底再放定位准星。
                    // 不能直接用 getApplicationIcon()——系统返回的自适应图标是按启动器遮罩（Pixel 上是圆形）裁过的，
                    // 四个角透明，圆角矩形的阴影会从角里透出来，看起来就是一圈黑边。
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(18.dp),
                                clip = false
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(android.R.drawable.ic_menu_mylocation),
                            contentDescription = null,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 软件名称
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(6.dp))

                    // 软件简介介绍
                    Text(
                        text = stringResource(R.string.app_slogan),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // 1. 软件更新卡片
            item {
                UpdateCheckCard(
                    isDark = isDark,
                    hasNewVersion = hasNewVersion,
                    newVersionName = if (hasNewVersion) latestRelease?.versionName else null,
                    onCheckClick = onNavigateToUpdate
                )
            }

            // 2. 各项设置：和"功能"页一样一项一张卡片
            item {
                InfoEntryCard(
                    icon = Icons.Rounded.Language,
                    tint = AccentBlue,
                    title = stringResource(R.string.select_language),
                    description = stringResource(R.string.switch_language_desc),
                    previewChip = currentLangName,
                    isDark = isDark,
                    onClick = onNavigateToLanguage
                )
            }
            item {
                InfoEntryCard(
                    icon = Icons.Rounded.Map,
                    tint = AccentGreen,
                    title = stringResource(R.string.map_config),
                    description = stringResource(R.string.map_config_desc),
                    previewChip = currentEngineName,
                    isDark = isDark,
                    onClick = onNavigateToMapEngine
                )
            }
            item {
                InfoEntryCard(
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    tint = AccentOrange,
                    title = stringResource(R.string.motion_realism_title),
                    description = stringResource(R.string.motion_realism_desc),
                    previewChip = currentRealismLevelName,
                    isDark = isDark,
                    onClick = onNavigateToMotionRealism
                )
            }
            item {
                // 本机适配状态、运行检测与 Hook 运行状态，完整进度在它的二级页面
                InfoEntryCard(
                    icon = Icons.Rounded.Devices,
                    tint = AccentGreen,
                    title = stringResource(R.string.system_adaptation_title),
                    description = stringResource(R.string.system_adaptation_desc),
                    previewChip = adaptationStatusChip,
                    isDark = isDark,
                    onClick = onNavigateToSystemAdaptation
                )
            }
            item {
                InfoEntryCard(
                    icon = Icons.Rounded.VpnKey,
                    tint = AccentBlue,
                    title = stringResource(R.string.env_datasource_tokens_title),
                    description = stringResource(R.string.env_datasource_tokens_desc),
                    isDark = isDark,
                    onClick = onNavigateToEnvTokens
                )
            }
        }
    }
}

/** 信息页的一项设置入口，样式和"功能"页的卡片一致：图标 + 标题与说明 + 可选状态 chip + 箭头 */
@Composable
private fun InfoEntryCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    isDark: Boolean,
    onClick: () -> Unit,
    previewChip: String? = null
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = AppColors.textSecondary(isDark)
                )
            }
            if (previewChip != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = 0.1f))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(text = previewChip, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = AppColors.textSecondary(isDark).copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
