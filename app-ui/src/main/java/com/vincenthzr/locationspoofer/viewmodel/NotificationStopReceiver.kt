package com.vincenthzr.locationspoofer.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vincenthzr.locationspoofer.data.motion.MotionController
import com.vincenthzr.locationspoofer.data.repository.LocationRepository
import com.vincenthzr.locationspoofer.data.repository.SettingsRepository
import com.vincenthzr.locationspoofer.data.state.NotificationActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 通知栏"停止模拟"按钮。界面还在就交给 MainViewModel（连路线一起停、更新界面）；
 * 界面已关闭时在这里直接停止，同样把位置交还给真实定位。
 */
class NotificationStopReceiver : BroadcastReceiver(), KoinComponent {
    private val locationRepository: LocationRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val motionController: MotionController by inject()
    private val realLocationHandoff: RealLocationHandoff by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationActions.STOP) return
        if (ExternalStopRequests.tryDeliverToUi()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                settingsRepository.isSpoofingActive = false
                motionController.onStopped()
                locationRepository.stopSpoofing(context)
                realLocationHandoff.start()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
