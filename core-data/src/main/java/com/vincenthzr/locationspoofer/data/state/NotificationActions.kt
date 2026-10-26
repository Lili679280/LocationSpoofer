package com.vincenthzr.locationspoofer.data.state

/** 模拟服务通知栏按钮发出的广播（只发给本应用），由 app-ui 的 NotificationStopReceiver 接收 */
object NotificationActions {
    const val STOP = "com.vincenthzr.locationspoofer.NOTIFICATION_STOP"
}
