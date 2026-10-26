package com.vincenthzr.locationspoofer.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 界面之外发起的"停止模拟"（通知栏按钮）。界面还在时交给 MainViewModel，和点"停止模拟"走同一套逻辑 */
object ExternalStopRequests {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    /** 有界面在监听就交给它处理并返回 true；返回 false 表示界面已关闭，需要调用方自己停止 */
    fun tryDeliverToUi(): Boolean = _requests.subscriptionCount.value > 0 && _requests.tryEmit(Unit)
}
