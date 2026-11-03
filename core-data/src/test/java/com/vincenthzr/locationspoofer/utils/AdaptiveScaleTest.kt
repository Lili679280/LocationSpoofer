package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveScaleTest {

    @Test
    fun `手机换算到统一宽度`() {
        assertEquals(1f, SettingsManager.computeAdaptiveScale(411), 0.001f)
        assertEquals(360 / 411f, SettingsManager.computeAdaptiveScale(360), 0.001f)
        assertEquals(448 / 411f, SettingsManager.computeAdaptiveScale(448), 0.001f)
    }

    @Test
    fun `极端宽度不会换算过头`() {
        assertEquals(0.8f, SettingsManager.computeAdaptiveScale(240), 0.001f)
        assertEquals(1.2f, SettingsManager.computeAdaptiveScale(599), 0.001f)
    }

    @Test
    fun `平板和无效宽度不换算`() {
        assertEquals(1f, SettingsManager.computeAdaptiveScale(600), 0.001f)
        assertEquals(1f, SettingsManager.computeAdaptiveScale(800), 0.001f)
        assertEquals(1f, SettingsManager.computeAdaptiveScale(0), 0.001f)
    }
}
