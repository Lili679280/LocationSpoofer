package com.vincenthzr.locationspoofer.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import com.vincenthzr.locationspoofer.utils.LocationJitter
import com.vincenthzr.locationspoofer.utils.RouteEngine
import org.json.JSONObject

/**
 * 免 Root 模式：通过系统开发者选项里的"模拟位置"接口推送位置，需要用户把本 App 选为"模拟位置信息应用"。
 *
 * 参考的开源实现：
 * - 测试定位源的登记、重试与撤销：FakeTraveler 的 MockedLocationProvider（GPL-3.0，mcastillof/FakeTraveler）；
 *   和它一样同时接管 GPS、网络，以及 Android 12 起的融合定位
 * - GPS / 网络定位源的属性取值：GoGoGo 的 ServiceGo（GPL-3.0，ZCShou/GoGoGo），按真实定位源的属性填写
 *
 * 不用谷歌服务的 setMockMode：谷歌地图靠上面的测试定位源就能跟随，而关闭这个模式会清掉谷歌服务记住的位置，
 * 停止后打开谷歌地图一直没有蓝点（实测，室内拿不到新定位时恢复不了）。
 *
 * 位置由 [RouteEngine] 按配置推算，和 Root 模式的 Hook 用同一套路线、摇杆与速度逻辑。
 */
class MockLocationPusher(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val jitter = LocationJitter()
    private val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }

    /** 登记测试定位源；没被选为"模拟位置信息应用"时抛出 [SecurityException] */
    fun start() {
        for (provider in providers) addProvider(provider)
    }

    /** 按当前配置推送一次位置；配置未激活时不推送 */
    fun push(config: JSONObject) {
        if (!config.optBoolean("active", false)) return
        val now = System.currentTimeMillis()
        val motion = RouteEngine.calculateCurrentPosition(config, now)
        // 配置里的坐标是 GCJ-02，系统定位接口统一用 WGS-84
        val wgs = CoordinateUtils.gcj02ToWgs84(motion.lat, motion.lng)
        val (lat, lng) = if (config.optBoolean("enable_jitter", true)) jitter.apply(wgs.lat, wgs.lng) else wgs.lat to wgs.lng
        val altitude = RouteEngine.realisticAltitude(config, now)
        val accuracy = jitter.accuracy()
        val satellites = config.optInt("satellite_count", 20)

        for (provider in providers) {
            val location = buildLocation(provider, lat, lng, altitude, motion.bearing, motion.speed, now, satellites,
                if (provider == LocationManager.NETWORK_PROVIDER) NETWORK_ACCURACY_M else accuracy)
            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: IllegalArgumentException) {
                // 停止时 LocationRepository 先撤销定位源、再停服务，这一瞬间定位源已不存在，下一轮任务就被取消了
            }
        }
    }

    /** 撤销测试定位源，系统恢复真实定位 */
    fun stop() {
        for (provider in providers) removeProvider(provider)
    }

    @SuppressLint("WrongConstant")
    private fun addProvider(provider: String) {
        var lastError: Throwable? = null
        repeat(MAX_RETRY_COUNT) {
            try {
                removeProvider(provider)
                val gps = provider == LocationManager.GPS_PROVIDER
                val network = provider == LocationManager.NETWORK_PROVIDER
                val power = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (gps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW
                } else {
                    @Suppress("DEPRECATION") if (gps) Criteria.POWER_HIGH else Criteria.POWER_LOW
                }
                val accuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (network) ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE
                } else {
                    @Suppress("DEPRECATION") if (network) Criteria.ACCURACY_COARSE else Criteria.ACCURACY_FINE
                }
                locationManager.addTestProvider(
                    provider,
                    network,       // requiresNetwork
                    gps,           // requiresSatellite
                    network,       // requiresCell
                    false,         // hasMonetaryCost
                    true,          // supportsAltitude
                    true,          // supportsSpeed
                    true,          // supportsBearing
                    power,
                    accuracy
                )
                locationManager.setTestProviderEnabled(provider, true)
                return
            } catch (e: SecurityException) {
                // 没有模拟位置权限，重试也没用
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        Log.e(TAG, "Could not add test provider $provider", lastError)
    }

    private fun removeProvider(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
        } catch (_: Throwable) {
        }
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: Throwable) {
        }
    }

    private fun buildLocation(
        provider: String,
        lat: Double,
        lng: Double,
        altitude: Double,
        bearing: Float,
        speed: Float,
        time: Long,
        satellites: Int,
        accuracy: Float
    ) = Location(provider).apply {
        latitude = lat
        longitude = lng
        this.altitude = altitude
        this.bearing = bearing
        this.speed = speed
        this.accuracy = accuracy
        this.time = time
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = accuracy * 1.5f
            speedAccuracyMetersPerSecond = 0.5f
            bearingAccuracyDegrees = if (speed > 0f) 10f else 180f
        }
        extras = Bundle().apply { putInt("satellites", satellites) }
    }

    private companion object {
        const val TAG = "MockLocationPusher"
        const val MAX_RETRY_COUNT = 3

        /** 网络定位本来就不如 GPS 准，给一个常见的室外网络定位精度 */
        const val NETWORK_ACCURACY_M = 12f
    }
}
