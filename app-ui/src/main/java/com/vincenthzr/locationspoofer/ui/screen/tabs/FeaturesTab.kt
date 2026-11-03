package com.vincenthzr.locationspoofer.ui.screen.tabs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.vincenthzr.locationspoofer.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.screen.ImportExportDataCard
import com.vincenthzr.locationspoofer.ui.components.ImportExportSelectionDialog
import com.vincenthzr.locationspoofer.data.model.ImportExportCounts
import com.vincenthzr.locationspoofer.data.model.ImportExportSelection
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentGreen
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.AppColors
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.applyImportPackage
import com.vincenthzr.locationspoofer.viewmodel.collectExportCounts
import com.vincenthzr.locationspoofer.viewmodel.exportEnvironmentData
import com.vincenthzr.locationspoofer.viewmodel.parseImportPackage

@Composable
fun FeaturesTab(
    viewModel: MainViewModel,
    uiState: AppState,
    tabBarHeight: Dp = 90.dp,
    onNavigateToEnvData: () -> Unit,
    onNavigateToSimulation: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val context = LocalContext.current

    // 导出：先让用户选内容，再拉起系统选择器（SAF 必须在拉起前就知道要写什么）
    var exportCounts by remember { mutableStateOf<ImportExportCounts?>(null) }
    var exportSelection by remember { mutableStateOf(ImportExportSelection()) }
    // 导入：先解析出文件里有什么，再让用户勾选要应用的部分
    var pendingImportPackage by remember {
        mutableStateOf<com.vincenthzr.locationspoofer.data.db.LocationSpooferDataPackage?>(null)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportEnvironmentData(it, exportSelection) { success ->
                Toast.makeText(
                    context,
                    context.getString(if (success) R.string.export_success else R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.parseImportPackage(it) { pkg ->
                if (pkg == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_parse_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    pendingImportPackage = pkg
                }
            }
        }
    }

    exportCounts?.let { counts ->
        ImportExportSelectionDialog(
            isExport = true,
            counts = counts,
            onConfirm = { selection ->
                exportSelection = selection
                exportCounts = null
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                exportLauncher.launch("environment_data_$stamp.json")
            },
            onDismiss = { exportCounts = null }
        )
    }

    pendingImportPackage?.let { pkg ->
        ImportExportSelectionDialog(
            isExport = false,
            counts = ImportExportCounts(
                locations = pkg.locations.size + pkg.collectionRoutes.size,
                savedLocations = pkg.savedLocations.size,
                savedRoutes = pkg.savedRoutes.size,
                appCoordinateSystems = pkg.appCoordinateSystems.size,
                settings = if (pkg.settings != null) 1 else 0,
                apiKeys = pkg.apiKeys?.let { k ->
                    listOf(k.amapApiKey, k.baiduApiKey, k.googleApiKey, k.wigleApiToken, k.opencellidApiToken)
                        .count { it.isNotBlank() }
                } ?: 0
            ),
            onConfirm = { selection ->
                pendingImportPackage = null
                viewModel.applyImportPackage(pkg, selection) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_merge_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { pendingImportPackage = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background(isDark))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = tabBarHeight + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶部和设置页同样的居中样式、同样的高度，切换标签时卡片不会上下跳
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 24.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), clip = false)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Extension, null, tint = AccentBlue, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.features_tab_title),
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.features_tab_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                item {
                    InfoEntryCard(
                        icon = Icons.Rounded.Map,
                        tint = AccentGreen,
                        title = stringResource(R.string.env_data_title),
                        description = stringResource(R.string.env_data_desc),
                        previewChip = if (uiState.isContinuousScanning) stringResource(R.string.env_data_collecting) else null,
                        isDark = isDark,
                        onClick = onNavigateToEnvData
                    )
                }

                item {
                    InfoEntryCard(
                        icon = Icons.Rounded.Tune,
                        tint = AccentOrange,
                        title = stringResource(R.string.simulation_settings_title),
                        description = stringResource(R.string.simulation_settings_desc),
                        previewChip = currentRealismLevelName(uiState),
                        isDark = isDark,
                        onClick = onNavigateToSimulation
                    )
                }

                item {
                    ImportExportDataCard(
                        isDark = isDark,
                        onImportClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportClick = {
                            // 先查各分类数量，弹出选择对话框；实际拉起 SAF 在对话框确认之后。
                            // 文件名在那里再带上时间戳，保证每次导出都是新文件名：
                            // 固定用 environment_data.json 时第二次导出必然撞名，而部分 ROM 的
                            // DocumentsProvider 处理重名的方式是另建一个 xxx_1.json 空占位文件、
                            // 却把返回的 Uri 指向原来那个旧文件，导致"旧备份被覆盖 + 多出 0B 空文件"（issue #57）。
                            viewModel.collectExportCounts { counts -> exportCounts = counts }
                        }
                    )
                }
            }
        }
    }
}
