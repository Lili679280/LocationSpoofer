@file:Suppress("SpellCheckingInspection")

package com.vincenthzr.locationspoofer.xposed.hooks.vendor

import com.vincenthzr.locationspoofer.vendor.RomFamily
import com.vincenthzr.locationspoofer.vendor.VendorProfile

/**
 * 单个机型 / 系统的适配器契约。
 *
 * 每种系统各写一个实现类，放在 `vendor/profiles/` 下，只声明"自己这套系统和 AOSP 基线的差异"，
 * 而不用把所有 OEM 的判断堆进同一个 Hook 函数里。共享 Hook 代码通过 [VendorRegistry] 间接使用这些
 * 适配器，运行时按 [priority] 选出唯一命中的适配器，选不到时落到 AOSP 基线。
 *
 * 最常见的差异是系统服务实现类名不同：覆写 [classCandidates] 给出该机型上的候选类名；
 * 需要调整豁免名单时覆写 [removedBaseExemptPackages]。
 *
 * 实现要求：**无状态、可安全并发调用**。适配器实例是单例（`object`），不要在其中持有可变状态。
 */
interface SystemHookVendor {

    /** 适配器标识，例如 `"aosp"`，只用于日志。 */
    val id: String

    /**
     * 该适配器归属的 ROM 家族。家族由 core-geo 的 `RomRules` 统一识别（App 显示"本机系统"也用这套规则），
     * 厂商级适配器的 [matches] 通常就是 `profile.family == family`。
     */
    val family: RomFamily

    /**
     * 选择优先级：多个适配器同时 [matches] 时，数字最大的胜出。
     *
     * 约定：具体机型 / ROM 适配器用正数（越精确越大）；AOSP 基线固定为 [Int.MIN_VALUE]，
     * 保证它永远是最后兜底、绝不会盖过任何真正命中的 OEM 适配器。
     */
    val priority: Int

    /**
     * 判断当前设备是否应由本适配器接管。
     *
     * 判定应尽量依据稳定信号（厂商标识属性、`manufacturer`/`brand`），必要时再叠加 API Level、
     * 具体机型等条件做更精确的匹配。AOSP 基线恒返回 `true`。
     */
    fun matches(profile: VendorProfile): Boolean

    /**
     * 给出该机型上某个系统组件的实现类候选包名（按优先级从高到低）。
     *
     * 只需返回"与基线不同或基线没有"的候选即可——[VendorRegistry] 会自动在其后追加 AOSP 基线候选，
     * 所以 OEM 适配器即使只覆盖了个别组件，其它组件仍能自动享受基线兜底。默认返回空列表（完全走基线）。
     */
    fun classCandidates(component: SystemComponent): List<String> = emptyList()

    /**
     * 通用基线豁免名单里，本机型要**取消豁免**、改为照常模拟的包名。
     *
     * 典型例子是原生 Android（Pixel 等）上的 GMS：这类机型没有厂商自有的定位融合服务，
     * 谷歌地图等 App 的定位就是 GMS 用 GNSS + Wi-Fi + 基站融合出来的。GMS 一旦被豁免，它看到的是
     * 真实的 Wi-Fi 和基站，会算出真实位置，再把模拟的 GNSS 判成"移动过快"丢弃，地图随即跳回真实位置。
     * 默认返回空集合（保持基线豁免不变）。
     */
    fun removedBaseExemptPackages(): Set<String> = emptySet()
}
