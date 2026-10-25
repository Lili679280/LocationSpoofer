package com.vincenthzr.locationspoofer.ui.components

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

/** LocalClipboard 的 setClipEntry 是挂起函数，这里包一层，供点击回调里直接复制纯文本 */
@Composable
fun rememberPlainTextCopier(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(null, text).toClipEntry())
            }
        }
    }
}
