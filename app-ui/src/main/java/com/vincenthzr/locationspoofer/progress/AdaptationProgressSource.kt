package com.vincenthzr.locationspoofer.progress

import android.content.Context
import java.io.File

/**
 * 适配进度文档的来源：只用安装包内置的一份（构建时从仓库根目录的 ADAPTATION_PROGRESS.md 复制进 assets）。
 *
 * 原来会联网取原作者仓库的最新版并缓存，但原仓库已清空，镜像里残留的是原作者针对小米 / OPPO 的旧文档，
 * 和本修改版的适配情况对不上，所以不再联网，也清掉旧版本留下的缓存。
 */
class AdaptationProgressSource(context: Context) {

    enum class Origin { BUNDLED }

    data class Document(val markdown: String, val origin: Origin)

    private val appContext = context.applicationContext

    init {
        // 旧版本联网取到的缓存（原作者的文档），留着会优先显示，删掉
        runCatching { File(appContext.filesDir, FILE_NAME).delete() }
    }

    fun local(): Document {
        val bundled = runCatching { appContext.assets.open(FILE_NAME).bufferedReader().use { it.readText() } }
        return Document(bundled.getOrDefault(""), Origin.BUNDLED)
    }

    /** 不再联网获取，返回 null 让调用方保留内置文档 */
    @Suppress("RedundantSuspendModifier")
    suspend fun fetchRemote(): Document? = null

    private companion object {
        const val FILE_NAME = "ADAPTATION_PROGRESS.md"
    }
}
