package com.vincenthzr.locationspoofer.progress

import com.vincenthzr.locationspoofer.ui.screen.MarkdownBlock
import com.vincenthzr.locationspoofer.ui.screen.parseMarkdownBlocks
import com.vincenthzr.locationspoofer.vendor.RomFamily
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptationProgressTest {

    /** 直接用仓库里真实的进度文档，文档结构改坏了这里会第一时间发现 */
    private val doc = File("../ADAPTATION_PROGRESS.md").readText()

    @Test
    fun `tables and horizontal rules are parsed instead of shown as raw text`() {
        val blocks = parseMarkdownBlocks(doc)
        assertTrue(blocks.none { it is MarkdownBlock.Paragraph && (it.text.startsWith("|") || it.text == "---") })
        val first = blocks.filterIsInstance<MarkdownBlock.Table>().first()
        assertEquals(listOf("系统", "适配器", "状态", "已验证的系统版本", "备注"), first.header)
        assertTrue(first.rows.none { row -> row.all { it.matches(Regex("-+")) } })
    }

    @Test
    fun `finds the global status of each system family`() {
        val aosp = AdaptationProgress.findGlobalSystem(doc, RomFamily.AOSP.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.PARTIAL, aosp.status)
        assertEquals("Android 17", aosp.verifiedVersions)

        val vivo = AdaptationProgress.findGlobalSystem(doc, RomFamily.ORIGINOS_FUNTOUCH.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.UNVERIFIED, vivo.status)
        assertEquals("", vivo.verifiedVersions)

        val hyperOs = AdaptationProgress.findGlobalSystem(doc, RomFamily.HYPEROS_MIUI.progressKeywords)!!
        assertEquals(AdaptationProgress.Status.UNVERIFIED, hyperOs.status)

        for (family in RomFamily.entries) {
            val entry = AdaptationProgress.findGlobalSystem(doc, family.progressKeywords)
            assertTrue("$family should have a row", entry != null)
        }
    }

    @Test
    fun `unknown structure degrades gracefully`() {
        assertNull(AdaptationProgress.findGlobalSystem("# 适配进度\n\n没有表格", listOf("HyperOS")))
    }
}
