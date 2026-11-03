package com.vincenthzr.locationspoofer.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class SubScreenStackTest {

    @Test
    fun `打开页面入栈，已打开的不重复`() {
        val stack = emptyList<MainSubScreen>().pushSubScreen(MainSubScreen.EnvData).pushSubScreen(MainSubScreen.EnvTokens)
        assertEquals(listOf(MainSubScreen.EnvData, MainSubScreen.EnvTokens), stack)
        assertEquals(stack, stack.pushSubScreen(MainSubScreen.EnvData))
    }

    @Test
    fun `只有最上面的页面能关`() {
        val stack = listOf(MainSubScreen.EnvData, MainSubScreen.EnvTokens)
        assertEquals(listOf(MainSubScreen.EnvData), stack.popSubScreen(MainSubScreen.EnvTokens))
        // 上一层已经退掉的页面滑出途中又点了返回，不能多退一层
        assertEquals(stack, stack.popSubScreen(MainSubScreen.EnvData))
    }
}
