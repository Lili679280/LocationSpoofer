package com.vincenthzr.locationspoofer.xposed.hooks

import android.os.Bundle
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.SpoofedMotion
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers

/**
 * 位置对象的"按接收方改写"工具。
 *
 * 系统定位服务把同一个 Location / LocationResult 对象交给所有订阅者（还会存成"最后已知位置"），
 * 所以绝不能就地改写：要给目标 App 的那一份换成独立的伪造副本，其余订阅者拿到的仍是原对象。
 *
 * 心跳每秒注入系统 GPS provider 的伪造定位带一个 extras 标记（[INJECTED_FIX_EXTRA]），
 * 非目标 App、"最后已知位置"缓存据此把它过滤掉；给目标 App 的副本会去掉这个标记。
 */
internal object LocationCopies {

    const val INJECTED_FIX_EXTRA = "com.lili679280.locationspoofer.injected"

    private fun isLocation(obj: Any?): Boolean =
        obj != null && LocationHooker.hasTypeByName(obj.javaClass, "android.location.Location")

    private fun isLocationResult(obj: Any?): Boolean =
        obj != null && obj.javaClass.name.endsWith("LocationResult")

    /** 给心跳注入的伪造定位打上标记 */
    fun markInjected(location: Any) {
        runCatching {
            val extras = XposedHelpers.callMethod(location, "getExtras") as? Bundle ?: Bundle()
            extras.putBoolean(INJECTED_FIX_EXTRA, true)
            XposedHelpers.callMethod(location, "setExtras", extras)
        }
    }

    private fun locationIsInjected(location: Any): Boolean = runCatching {
        (XposedHelpers.callMethod(location, "getExtras") as? Bundle)?.getBoolean(INJECTED_FIX_EXTRA, false) == true
    }.getOrDefault(false)

    private fun locationsOf(obj: Any?): List<Any> = when {
        obj == null -> emptyList()
        isLocation(obj) -> listOf(obj)
        isLocationResult(obj) -> runCatching { XposedHelpers.callMethod(obj, "asList") as? List<*> }
            .getOrNull()?.filterNotNull()?.filter(::isLocation).orEmpty()
        obj is List<*> -> obj.filterNotNull().filter(::isLocation)
        else -> emptyList()
    }

    /** 参数（或返回值）里有没有心跳注入的伪造定位 */
    fun containsInjected(values: List<Any?>): Boolean = values.any { v -> locationsOf(v).any(::locationIsInjected) }

    fun isInjected(value: Any?): Boolean = locationsOf(value).any(::locationIsInjected)

    /** Location 的独立副本（Location(Location) 拷贝构造，extras 也会复制） */
    fun copyLocation(location: Any): Any = location.javaClass.getConstructor(location.javaClass).newInstance(location)

    private fun fakeCopy(location: Any, motion: SpoofedMotion, altitude: Double, accuracy: Float): Any {
        val copy = copyLocation(location)
        runCatching { (XposedHelpers.callMethod(copy, "getExtras") as? Bundle)?.remove(INJECTED_FIX_EXTRA) }
        SystemHookUtils.applyFakeLocationFields(copy, motion, altitude, accuracy)
        return copy
    }

    /** LocationResult 的伪造副本：先整体拷贝，再改拷贝里的每个 Location */
    private fun fakeResultCopy(result: Any, motion: SpoofedMotion, altitude: Double, accuracy: Float): Any {
        val deep = runCatching { XposedHelpers.callMethod(result, "deepCopy") }.getOrNull()
        if (deep != null) {
            locationsOf(deep).forEach { loc ->
                runCatching { (XposedHelpers.callMethod(loc, "getExtras") as? Bundle)?.remove(INJECTED_FIX_EXTRA) }
                SystemHookUtils.applyFakeLocationFields(loc, motion, altitude, accuracy)
            }
            return deep
        }
        val copies = locationsOf(result).map { fakeCopy(it, motion, altitude, accuracy) }
        return XposedHelpers.callStaticMethod(result.javaClass, "create", copies) ?: result
    }

    /**
     * 把参数里出现的所有位置（单个 Location、List<Location>、LocationResult）替换成伪造副本，
     * 返回新的参数数组；原对象不动，其他订阅者不受影响。
     */
    fun fakeArgs(args: List<Any?>, motion: SpoofedMotion, altitude: Double, accuracy: Float): Array<Any?> =
        args.map { arg ->
            runCatching {
                when {
                    arg == null -> null
                    isLocation(arg) -> fakeCopy(arg, motion, altitude, accuracy)
                    isLocationResult(arg) -> fakeResultCopy(arg, motion, altitude, accuracy)
                    arg is List<*> && arg.isNotEmpty() && arg.all(::isLocation) ->
                        ArrayList<Any?>(arg.map { fakeCopy(it!!, motion, altitude, accuracy) })
                    else -> arg
                }
            }.getOrDefault(arg)
        }.toTypedArray()
}
