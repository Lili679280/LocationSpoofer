package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.sqrt

/**
 * 静止时的自然定位漂移与精度起伏。坐标偏移用 Ornstein-Uhlenbeck 过程（均值回归的随机游走），
 * 硬性限制在约 4 米内；精度在 1.5~3.5 米间微弱起伏，接近室外良好信号下的真实 GPS。
 * Hook 层和免 Root 模式共用；状态会被多个线程同时推进，内部加锁。
 */
class LocationJitter {
    private val lock = Any()
    private val rng = Random()
    private var driftLat = 0.0
    private var driftLng = 0.0
    private var accuracyDrift = 0.0
    private var lastCallTime = 0L

    fun apply(baseLat: Double, baseLng: Double): Pair<Double, Double> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val dt = if (lastCallTime > 0) ((now - lastCallTime) / 1000.0).coerceIn(0.01, 5.0) else 1.0
        lastCallTime = now

        driftLat = (driftLat + SIGMA * sqrt(dt) * rng.nextGaussian() - ALPHA * driftLat * dt).coerceIn(-MAX_DRIFT, MAX_DRIFT)
        driftLng = (driftLng + SIGMA * sqrt(dt) * rng.nextGaussian() - ALPHA * driftLng * dt).coerceIn(-MAX_DRIFT, MAX_DRIFT)
        Pair(baseLat + driftLat, baseLng + driftLng)
    }

    fun accuracy(): Float = synchronized(lock) {
        accuracyDrift += 0.1 * rng.nextGaussian() - 0.05 * accuracyDrift
        (2.2 + accuracyDrift).coerceIn(1.5, 3.5).toFloat()
    }

    private companion object {
        /** 约 0.2 米步长 */
        const val SIGMA = 0.000002

        /** 均值回归强度 */
        const val ALPHA = 0.05

        /** 约 4 米 */
        const val MAX_DRIFT = 0.00004
    }
}
