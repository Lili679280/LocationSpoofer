package com.vincenthzr.locationspoofer.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import com.vincenthzr.locationspoofer.data.repository.LocationRepository
import com.vincenthzr.locationspoofer.data.repository.SettingsRepository
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 停止模拟后立即回到真实位置，在 locationRepository.stopSpoofing() 之后调用。效果相当于停止后马上点一次"当前定位"：
 * 1. 立刻重新定位：有百度 Key 时用百度网络定位；否则（或失败时）等一次停止之后新产生的系统定位
 *    （模拟期间 Hook 也改写了系统各 provider 的缓存位置，所以不能用 getLastKnownLocation）；
 * 2. 通过 [realLocation] 通知界面把地图移到这个位置；
 * 3. 在这个位置上再模拟几秒（关掉 Wi-Fi / 基站伪造，与真实环境一致），然后真正停止。
 *    谷歌定位服务（GMS）也会收到模拟数据，并把模拟期间的位置记成"当前位置"（重启 GMS 也清不掉）。
 *    室内只有误差大的网络定位时，它会把真实位置判成"移动过快"丢弃，谷歌地图一直停在模拟的地方；
 *    而高精度的 GNSS 定位不管跳多远都会被立即接受，借此把它的记忆纠正回真实位置。
 *
 * 不放在 MainViewModel 里：通知栏的"停止模拟"在界面已关闭时也要走同样的流程，所以用进程级单例和自己的协程作用域。
 */
class RealLocationHandoff(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _realLocation = MutableSharedFlow<Pair<Double, Double>>(extraBufferCapacity = 1)

    /** 交还时拿到的真实位置（GCJ-02），界面据此把地图移过去 */
    val realLocation: SharedFlow<Pair<Double, Double>> = _realLocation.asSharedFlow()

    fun start() {
        job?.cancel()
        val stoppedAt = SystemClock.elapsedRealtimeNanos()
        job = scope.launch {
            // 优先用百度网络定位（室内也能很快拿到，且不经过被模拟改写过的系统定位源），拿不到再等系统新定位
            val real = fetchBaiduLocationGcj() ?: awaitFreshRealLocationGcj(stoppedAt) ?: return@launch
            // 定位期间又开始了新的模拟（界面、Tasker 都会先记下"正在模拟"），交还作废，不能盖掉新配置
            if (settingsRepository.isSpoofingActive) return@launch
            _realLocation.tryEmit(real)
            locationRepository.startSpoofing(
                context, real.first, real.second,
                "STILL", 0f, System.currentTimeMillis(),
                emptyList(), false,
                settingsRepository.getAppCoordinateSystems(),
                mockWifi = false,
                mockCell = false,
                mockBluetooth = false,
                enableJitter = false
            )
            delay(REAL_LOCATION_HANDOFF_MS)
            if (!settingsRepository.isSpoofingActive) locationRepository.stopSpoofing(context)
        }
    }

    /** 交还进行中时立即结束（通知栏又点了一次停止）：不再等那几秒，直接停掉 */
    fun finishNow() {
        val running = job?.isActive == true
        cancel()
        if (running && !settingsRepository.isSpoofingActive) scope.launch { locationRepository.stopSpoofing(context) }
    }

    /** 新的模拟开始时取消还没走完的交还过程，免得它稍后把新模拟停掉 */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * 用百度定位 SDK 取一次真实位置（GCJ-02），没填百度 Key 或定位失败返回 null。
     *
     * 默认只走网络定位（Wi-Fi / 基站 → 百度服务器），不开 GPS：全局模拟时系统 GPS provider 会被注入模拟定位，
     * 而本软件自身进程拿到的 Wi-Fi / 基站数据始终是真实的。也不依赖谷歌网络定位——它判定手机"静止"时
     * 可能长时间不出新位置，室内就既没有 GPS 也没有网络定位可用。
     * 确定没在模拟时（例如采集环境数据）传 [withGps] = true，室外用 GPS，比网络定位准得多。
     */
    suspend fun fetchBaiduLocationGcj(withGps: Boolean = false): Pair<Double, Double>? {
        val key = settingsRepository.getBaiduApiKey()
        if (key.isBlank()) return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(BAIDU_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val client = try {
                        com.baidu.location.LocationClient.setAgreePrivacy(true)
                        com.baidu.location.LocationClient.setKey(key)
                        com.baidu.location.LocationClient(context.applicationContext)
                    } catch (e: Exception) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    client.locOption = com.baidu.location.LocationClientOption().apply {
                        setCoorType("gcj02")
                        setLocationMode(
                            if (withGps) com.baidu.location.LocationClientOption.LocationMode.Hight_Accuracy
                            else com.baidu.location.LocationClientOption.LocationMode.Battery_Saving
                        )
                        // 高精度模式也要单独打开 GNSS，否则百度照样只用网络定位
                        setOpenGnss(withGps)
                        setOnceLocation(true)
                        setIsNeedAddress(false)
                    }
                    val listener = object : com.baidu.location.BDAbstractLocationListener() {
                        override fun onReceiveLocation(location: com.baidu.location.BDLocation?) {
                            runCatching { client.stop() }
                            val ok = location != null && location.locType in setOf(
                                com.baidu.location.BDLocation.TypeGpsLocation,
                                com.baidu.location.BDLocation.TypeNetWorkLocation,
                                com.baidu.location.BDLocation.TypeOffLineLocation
                            )
                            if (cont.isActive) cont.resume(if (ok) location.latitude to location.longitude else null)
                        }
                    }
                    cont.invokeOnCancellation { runCatching { client.stop() } }
                    client.registerLocationListener(listener)
                    client.start()
                }
            }
        }
    }

    /** 等一次 [sinceElapsedNanos] 之后产生的系统定位（GPS 或网络），转成 GCJ-02；拿不到返回 null */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFreshRealLocationGcj(sinceElapsedNanos: Long): Pair<Double, Double>? =
        withTimeoutOrNull(REAL_LOCATION_WAIT_MS) {
            suspendCancellableCoroutine { cont ->
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (location.elapsedRealtimeNanos <= sinceElapsedNanos || !cont.isActive) return
                        lm.removeUpdates(this)
                        val gcj = CoordinateUtils.wgs84ToGcj02(location.latitude, location.longitude)
                        cont.resume(gcj.lat to gcj.lng)
                    }

                    // Android 10 及以下这三个回调还没有默认实现，必须写出来
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                try {
                    if (providers.isEmpty()) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    providers.forEach { lm.requestLocationUpdates(it, 1_000L, 0f, listener, android.os.Looper.getMainLooper()) }
                } catch (e: SecurityException) {
                    runCatching { lm.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private companion object {
        /**
         * 在真实位置上继续模拟的时长。GMS 按 App 分别记住上次给出的位置，只有在这段时间里正在接收定位的
         * App（例如前台的谷歌地图）才会收到并采用这个真实位置，所以不能太短
         */
        const val REAL_LOCATION_HANDOFF_MS = 5_000L

        /** 停止后等待一次新的真实定位的最长时间，超时就放弃交还 */
        const val REAL_LOCATION_WAIT_MS = 20_000L

        /** 百度网络定位最长等待时间 */
        const val BAIDU_LOCATION_TIMEOUT_MS = 8_000L
    }
}
