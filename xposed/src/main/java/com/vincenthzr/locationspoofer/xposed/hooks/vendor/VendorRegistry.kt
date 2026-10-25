@file:Suppress("SpellCheckingInspection")

package com.vincenthzr.locationspoofer.xposed.hooks.vendor

import com.vincenthzr.locationspoofer.vendor.VendorProfile
import com.vincenthzr.locationspoofer.xposed.hooks.vendor.profiles.AospVendor
import com.vincenthzr.locationspoofer.xposed.utils.XposedBridge

/**
 * 机型 / 系统适配器的注册表与统一入口。
 *
 * 共享 Hook 代码只跟这个对象（以及基于它的 [SystemClassLocator]）打交道，不直接引用任何具体的
 * [SystemHookVendor] 实现，从而实现"系统各走各的规则"而调用方无需感知分支：
 * - [classCandidates] —— "当前机型候选 → AOSP 基线候选"合并去重后的类名清单，[SystemClassLocator] 按它逐层查找；
 * - [removedBaseExemptPackages] —— 当前机型要从通用基线豁免名单里移除的包名，供
 *   [com.vincenthzr.locationspoofer.xposed.hooks.SystemHookUtils.EXEMPT_PACKAGES] 使用。
 *
 * **注册新适配器**：在下面的 [ALL] 列表里加上你的 `object` 即可，选择时按 [SystemHookVendor.priority] 排序。
 */
object VendorRegistry {

    /**
     * 所有已知适配器。顺序不影响选择结果（选择只看 [SystemHookVendor.priority]），
     * 但请把 [AospVendor] 放在最后，直观表达"它是兜底"。
     *
     * 目前只保留 AOSP 基线（Pixel 实测；vivo 等其他机型也落到它）。以后要给某个系统单独适配，
     * 新增一个适配器加到这里即可。
     */
    internal val ALL: List<SystemHookVendor> = listOf(
        AospVendor, // 兜底基线，priority = Int.MIN_VALUE
    )

    /** 当前设备命中的适配器：所有 [SystemHookVendor.matches] 为真者中 [SystemHookVendor.priority] 最大的一个。 */
    val active: SystemHookVendor by lazy { select(VendorProfile.current) }

    private var logged = false

    /** 选择逻辑本身是纯函数，便于单元测试直接喂伪造的设备画像 */
    internal fun select(profile: VendorProfile): SystemHookVendor {
        val chosen = ALL.filter {
            try {
                it.matches(profile)
            } catch (t: Throwable) {
                XposedBridge.log("[Vendor] matches() of ${it.id} threw: $t")
                false
            }
        }.maxByOrNull { it.priority } ?: AospVendor
        return chosen
    }

    /**
     * 打印一次当前设备画像与命中的适配器，便于排查用户反馈时快速确认"这台设备走了哪套规则"。
     * 幂等：多次调用只会真正输出一次。
     */
    fun logSelectionOnce() {
        if (logged) return
        logged = true
        try {
            XposedBridge.log("[Vendor] active=${active.id} (family=${active.family}) profile=${VendorProfile.current}")
        } catch (_: Throwable) {}
    }

    /**
     * 某系统组件的候选实现类名清单：当前机型候选在前，AOSP 基线候选在后，去重。
     * [SystemClassLocator] 在每一层查找时都只试这份名单。
     */
    fun classCandidates(component: SystemComponent): List<String> {
        val vendor = try {
            active.classCandidates(component)
        } catch (t: Throwable) {
            XposedBridge.log("[Vendor] classCandidates(${component}) of ${active.id} threw: $t")
            emptyList()
        }
        return (vendor + AospVendor.classCandidates(component)).distinct()
    }

    /**
     * 当前机型要从通用基线豁免名单里移除的包名（见 [SystemHookVendor.removedBaseExemptPackages]）。
     * 首次访问时求值并缓存——[SystemHookUtils.EXEMPT_PACKAGES] 在 `isTargetCaller` 这条热路径上
     * 每次系统 IPC 调用都会读取，不能在这里重复分配 Set。
     */
    val removedBaseExemptPackages: Set<String> by lazy {
        try {
            active.removedBaseExemptPackages()
        } catch (t: Throwable) {
            XposedBridge.log("[Vendor] removedBaseExemptPackages of ${active.id} threw: $t")
            emptySet()
        }
    }
}
