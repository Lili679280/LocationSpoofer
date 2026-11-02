package com.vincenthzr.locationspoofer.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.vincenthzr.locationspoofer.data.repository.SettingsRepository
import com.vincenthzr.locationspoofer.data.state.NotificationActions
import com.vincenthzr.locationspoofer.utils.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SpoofingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val configManager: ConfigManager by inject()

    /** 免 Root 模式下持续推送位置的任务，见 [MockLocationPusher] */
    private var mockPusher: MockLocationPusher? = null
    private var mockJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_MOCK_MODE = "EXTRA_MOCK_MODE"
        private const val MOCK_PUSH_INTERVAL_MS = 1_000L
        private const val MOCK_FROZEN_GAP_MS = 5_000L
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "spoofing_status"
        private const val LEGACY_CHANNEL_ID = "SpoofingServiceChannel"

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val mockMode = intent.getBooleanExtra(EXTRA_MOCK_MODE, false)
                startSpoofing(lat, lng)
                when {
                    isRunning && mockMode -> startMockPushing()
                    isRunning -> stopMockPushing()
                    mockMode -> abortMockMode(this, R.string.mock_start_background_failed)
                }
            }

            ACTION_STOP -> stopSpoofing()
        }
        // 被系统回收后重启时重新投递上次的 ACTION_START，通知和停止按钮跟着恢复；
        // START_STICKY 重启时 intent 为空，模拟还在跑却没有通知
        return START_REDELIVER_INTENT
    }

    @SuppressLint("WakelockTimeout")
    private fun startSpoofing(lat: Double, lng: Double) {
        acquireWakeLock()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(NotificationActions.STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.spoofing_service_title))
            .setContentText(
                getString(
                    R.string.spoofing_service_content,
                    String.format(java.util.Locale.US, "%.6f", lat),
                    String.format(java.util.Locale.US, "%.6f", lng)
                )
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.spoofing_service_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            // 进不了前台（例如没有定位权限）就立即结束；不结束的话系统约 10 秒后会以"未按时进入前台"杀掉整个进程
            e.printStackTrace()
            stopSpoofing()
            return
        }
        isRunning = true
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LocationSpoofer:BackgroundSpoofingWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 保持 24 小时唤醒，防止后台系统冻结
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    /** 免 Root 模式：登记测试定位源，每秒按最新配置推送一次；重复调用（换了位置）时沿用已在跑的任务 */
    private fun startMockPushing() {
        if (mockJob?.isActive == true) return
        val pusher = MockLocationPusher(this)
        // 在主线程同步登记：放进后台任务的话，刚开始就停止时撤销可能跑在登记前面，定位源残留会一直挡住真实定位
        try {
            pusher.start()
        } catch (e: SecurityException) {
            android.util.Log.e("SpoofingService", "Mock location not allowed", e)
            abortMockMode(this, R.string.mock_location_not_allowed)
            return
        }
        mockPusher = pusher
        mockJob = serviceScope.launch {
            try {
                var lastPushAt = SystemClock.elapsedRealtime()
                var warned = false
                while (isActive) {
                    configManager.currentConfig()?.let(pusher::push)
                    delay(MOCK_PUSH_INTERVAL_MS)
                    // vivo 等系统会把后台进程冻结，前台服务也一样：推送中断几秒后高德这类持续定位的 App 就回到真实位置
                    val now = SystemClock.elapsedRealtime()
                    if (!warned && now - lastPushAt > MOCK_FROZEN_GAP_MS) {
                        warned = true
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this@SpoofingService, R.string.mock_process_frozen, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    lastPushAt = now
                }
            } catch (e: SecurityException) {
                // 中途在开发者选项里被取消
                android.util.Log.e("SpoofingService", "Mock location not allowed", e)
                abortMockMode(this@SpoofingService, R.string.mock_location_not_allowed)
            }
        }
    }

    private fun stopMockPushing() {
        mockJob?.cancel()
        mockJob = null
        mockPusher?.stop()
        mockPusher = null
    }

    private fun stopSpoofing() {
        isRunning = false
        stopMockPushing()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // 低重要性的通知会被收进"静音"分组并默认折叠，"停止模拟"按钮要手动展开才看得到；
        // 改成默认重要性（不响铃不振动）。已创建的渠道改不了重要性，所以换了渠道 ID 并删掉旧渠道
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.spoofing_service_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.spoofing_bg_notification_title)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户在多任务卡片划掉 Activity 时，如果正在模拟，继续在后台保持运行
        if (isRunning) {
            acquireWakeLock()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        stopMockPushing()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * 免 Root 模式开始不了或中途失去权限：提示后按通知栏"停止"的流程收尾，界面和状态一起恢复。
 * Root 模式不需要：服务只负责通知，Hook 不受影响；而这里没有服务就没人推送位置，不能一直显示"模拟中"。
 * 停止后的"交还真实位置"失败不用处理：这时已经记成没在模拟，它到时间会自己结束，用户也不需要知道。
 */
internal fun abortMockMode(context: Context, message: Int) {
    if (!MockModeAbort.settings.isSpoofingActive) return
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
    context.sendBroadcast(Intent(NotificationActions.STOP).setPackage(context.packageName))
}

private object MockModeAbort : KoinComponent {
    val settings: SettingsRepository by inject()
}
