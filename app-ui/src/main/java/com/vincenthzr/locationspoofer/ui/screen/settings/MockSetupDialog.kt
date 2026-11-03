package com.vincenthzr.locationspoofer.ui.screen.settings

import com.vincenthzr.locationspoofer.ui.components.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.data.model.AppState
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.dismissMockSetupDialog
import com.vincenthzr.locationspoofer.viewmodel.openDeveloperOptions

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
