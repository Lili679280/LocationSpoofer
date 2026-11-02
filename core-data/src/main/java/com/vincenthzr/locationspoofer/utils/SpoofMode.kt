package com.vincenthzr.locationspoofer.utils

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 运行模式：LSPosed 模块生效时走 Hook（Root 模式）；没有生效、或用户强制选择时，
 * 走系统开发者选项里的"模拟位置"接口（免 Root 模式），见 MockLocationPusher。
 */
object SpoofMode {

    fun isMockMode(settings: SettingsManager): Boolean =
        settings.forceMockMode || !XposedModuleStatus.isModuleActive.value

    /** 是否已在开发者选项里被选为"模拟位置信息应用" */
    fun hasMockLocationPermission(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
