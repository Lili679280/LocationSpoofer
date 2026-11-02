package com.vincenthzr.locationspoofer.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * vivo 系统会冻结后台进程，前台服务和电池优化白名单都挡不住：免 Root 模式几秒后就停止推送位置，
 * 高德这类持续定位的 App 随即回到真实位置。只有用户在"后台高耗电"里允许才不会被冻结。
 * 系统没有接口能查询这个开关，只能引导用户去设置。
 */
object BackgroundPower {
    /** OriginOS 6（Android 16）上实测能打开的是第一个；后面是旧版系统的入口，这台手机不允许外部打开，没有验证过 */
    private val VIVO_PAGES = listOf(
        ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.BackgroundHighUsageActivity"),
        ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
        ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity")
    )

    val needsSetup: Boolean
        get() = Build.MANUFACTURER.equals("vivo", ignoreCase = true)

    /** 打开"后台高耗电"页面；各入口都打不开时退回本应用的详情页 */
    fun openSettings(context: Context) {
        for (page in VIVO_PAGES) {
            try {
                context.startActivity(Intent().setComponent(page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: RuntimeException) {
                // 不同系统版本的入口不同：不存在或不允许外部打开时试下一个
            }
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
