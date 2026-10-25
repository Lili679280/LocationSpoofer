package com.vincenthzr.locationspoofer.ui.screen.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincenthzr.locationspoofer.ui.R
import com.vincenthzr.locationspoofer.ui.components.rememberPlainTextCopier
import com.vincenthzr.locationspoofer.ui.theme.AccentBlue
import com.vincenthzr.locationspoofer.ui.theme.AccentOrange
import com.vincenthzr.locationspoofer.ui.theme.noRippleClickable
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/**
 * 地图配置页里的"申请 Key 用的应用信息"：本软件的包名与签名 SHA1（点击复制），
 * 以及高德 / 百度 / 谷歌各自的申请步骤。原来单独放在设置里的"运行环境与签名"页，
 * 只在申请地图 Key 时用得到，所以并到这里。
 */
@Composable
internal fun KeyApplicationInfoCard(appSha1: String, isDark: Boolean) {
    val context = LocalContext.current
    val copyPlainText = rememberPlainTextCopier()
    var showGuide by remember { mutableStateOf(false) }

    fun copy(text: String) {
        if (text.isBlank()) return
        copyPlainText(text)
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    val rowBackground = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)

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
                    .background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Security, null, tint = AccentOrange, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.key_app_info_title),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.key_app_info_desc),
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyRow(
                label = stringResource(R.string.app_package_name),
                value = context.packageName,
                background = rowBackground,
                onCopy = { copy(context.packageName) }
            )
            CopyRow(
                label = stringResource(R.string.app_sha1),
                value = appSha1.ifBlank { stringResource(R.string.reading_signature) },
                background = rowBackground,
                onCopy = { copy(appSha1) }
            )

            // 申请步骤（默认收起）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(rowBackground)
                    .noRippleClickable { showGuide = !showGuide }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.key_guide_title),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = showGuide) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GuideSection(stringResource(R.string.custom_amap_key), stringResource(R.string.key_guide_amap))
                    GuideSection(stringResource(R.string.custom_baidu_key), stringResource(R.string.key_guide_baidu))
                    GuideSection(stringResource(R.string.custom_google_key), stringResource(R.string.key_guide_google))
                }
            }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, background: Color, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .noRippleClickable(onClick = onCopy)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            tint = AccentBlue,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun GuideSection(title: String, steps: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = steps,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}
