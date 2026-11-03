package com.vincenthzr.locationspoofer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

// 弹窗是单独的窗口，里面的 LocalDensity、LocalContext、LocalConfiguration 会被重置成系统值，
// 界面缩放和软件内选的语言就不生效了。软件里的弹窗都用这两个（DialogUsageTest 会检查）。

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalContext provides context,
            LocalConfiguration provides configuration,
            content = content
        )
    }
}

/** 样式和 Material 3 的 AlertDialog 一样，但整个弹窗（包括边距）都跟着界面缩放 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (text != null) {
                    // 正文很长时让出位置给按钮
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
