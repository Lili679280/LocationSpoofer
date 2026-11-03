package com.vincenthzr.locationspoofer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 直接用系统的弹窗不会报错，但弹窗会悄悄失去界面缩放和软件内语言，所以在这里拦住 */
class DialogUsageTest {

    @Test
    fun `软件里的弹窗都用 ScaledDialogs`() {
        val forbidden = listOf(
            "import androidx.compose.ui.window.Dialog\n",
            "androidx.compose.ui.window.Dialog(",
            "import androidx.compose.material3.AlertDialog\n",
            "androidx.compose.material3.AlertDialog("
        )
        val offenders = listOf("src/main/java", "../app/src/main/java").flatMap { root ->
            File(root).walkTopDown()
                .filter { it.extension == "kt" && it.name != "ScaledDialogs.kt" }
                .filter { file -> file.readText().replace("\r\n", "\n").let { text -> forbidden.any(text::contains) } }
                .map { it.name }
                .toList()
        }
        assertEquals(emptyList<String>(), offenders)
    }
}
