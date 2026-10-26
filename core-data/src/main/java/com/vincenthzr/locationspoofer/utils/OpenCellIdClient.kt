package com.vincenthzr.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class OpenCellIdClient {
    private val client = OkHttpClient()

    private companion object {
        // OpenCellID 接口的错误码：1 找不到基站；2 / 4 Key 不存在或格式不对；7 今日次数已用完
        const val ERROR_NO_CELLS = 1
        val INVALID_KEY_CODES = setOf(2, 4)
        const val ERROR_RATE_LIMITED = 7
    }

    /** 检测 Token：查海上一个没有基站的小区域，Key 有效时回空列表，无效时回 401。BBOX 四个 0 会被当成错误请求（400） */
    suspend fun validateToken(token: String): TokenCheck = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext TokenCheck.BAD_FORMAT
        try {
            val url = "https://opencellid.org/cell/getInArea?key=${token.trim()}&BBOX=0.0,0.0,0.001,0.001&format=json"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) return@withContext TokenCheck.INVALID
                if (!response.isSuccessful) return@withContext TokenCheck.NETWORK_ERROR
                val json = JSONObject(response.body?.string().orEmpty())
                when {
                    !json.has("error") -> TokenCheck.VALID
                    json.optInt("code") == ERROR_NO_CELLS || json.optInt("code") == ERROR_RATE_LIMITED -> TokenCheck.VALID
                    json.optInt("code") in INVALID_KEY_CODES -> TokenCheck.INVALID
                    else -> TokenCheck.NETWORK_ERROR
                }
            }
        } catch (e: Exception) {
            TokenCheck.NETWORK_ERROR
        }
    }

    /** 查询 (lat, lng)（WGS-84）附近的基站，由近到远扩大范围，查到为止；查不到就是查不到，不编造数据 */
    suspend fun fetchCellData(lat: Double, lng: Double, token: String): OnlineLookup =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext OnlineLookup.Unauthorized
            var lastFailure: OnlineLookup? = null
            try {
                for (box in buildSearchBoxes(lat, lng)) {
                    val url = "https://opencellid.org/cell/getInArea?key=${token.trim()}" +
                        "&BBOX=${box.latMin},${box.lonMin},${box.latMax},${box.lonMax}&format=json&limit=20"
                    val result = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        when {
                            response.code == 401 || response.code == 403 -> return@use OnlineLookup.Unauthorized
                            response.code == 429 -> return@use OnlineLookup.Failed(OnlineLookup.FailReason.RATE_LIMITED)
                            !response.isSuccessful -> return@use OnlineLookup.Failed(OnlineLookup.FailReason.SERVER_ERROR, response.code)
                        }
                        val json = JSONObject(response.body?.string().orEmpty())
                        if (json.has("error")) {
                            return@use when (json.optInt("code")) {
                                ERROR_NO_CELLS -> OnlineLookup.NoData
                                in INVALID_KEY_CODES -> OnlineLookup.Unauthorized
                                ERROR_RATE_LIMITED -> OnlineLookup.Failed(OnlineLookup.FailReason.RATE_LIMITED)
                                else -> OnlineLookup.Failed(OnlineLookup.FailReason.SERVER_ERROR)
                            }
                        }
                        val cells = normalizeOpenCellIdCells(json.optJSONArray("cells") ?: JSONArray())
                        if (cells.length() == 0) OnlineLookup.NoData else OnlineLookup.Found(cells.toString(), cells.length())
                    }
                    when (result) {
                        is OnlineLookup.Found, OnlineLookup.Unauthorized -> return@withContext result
                        is OnlineLookup.Failed -> {
                            // 次数用完再试更大范围也没用
                            if (result.reason == OnlineLookup.FailReason.RATE_LIMITED) return@withContext result
                            lastFailure = result
                        }
                        OnlineLookup.NoData -> Unit
                    }
                }
                lastFailure ?: OnlineLookup.NoData
            } catch (e: Exception) {
                OnlineLookup.Failed(OnlineLookup.FailReason.NETWORK)
            }
        }

    private data class BBox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double
    )

    private fun buildSearchBoxes(lat: Double, lng: Double): List<BBox> {
        val initialHalfSpan = 0.005
        val gridHalfSpan = 0.008
        val gridStep = gridHalfSpan * 2
        val boxes = mutableListOf(bbox(lat, lng, initialHalfSpan))

        for (ring in 0..2) {
            for (x in -ring..ring) {
                for (y in -ring..ring) {
                    if (ring > 0 && kotlin.math.abs(x) != ring && kotlin.math.abs(y) != ring) continue
                    val centerLat = lat + y * gridStep
                    val centerLng = lng + x * gridStep
                    val box = bbox(centerLat, centerLng, gridHalfSpan)
                    if (boxes.none { it == box }) boxes.add(box)
                }
            }
        }

        return boxes
    }

    private fun bbox(lat: Double, lng: Double, halfSpan: Double): BBox {
        return BBox(
            latMin = (lat - halfSpan).coerceAtLeast(-90.0),
            lonMin = (lng - halfSpan).coerceAtLeast(-180.0),
            latMax = (lat + halfSpan).coerceAtMost(90.0),
            lonMax = (lng + halfSpan).coerceAtMost(180.0)
        )
    }

    private fun normalizeOpenCellIdCells(cells: JSONArray): JSONArray {
        val normalized = JSONArray()
        for (i in 0 until cells.length()) {
            val source = cells.optJSONObject(i) ?: continue
            val type =
                normalizeRadioType(source.optString("radio", source.optString("type", "LTE")))
            val mcc = positiveInt(source, "mcc", default = 460)
            val mnc = positiveInt(source, "mnc", "net", default = 0)
            val area = positiveInt(source, "tac", "lac", "area", default = 0)
            val cellId = positiveInt(source, "ci", "cid", "cellid", "cell", default = 0)
            if (area <= 0 || cellId <= 0) {
                continue
            }

            val dbm = signalDbm(source, i)
            val pci = positiveInt(source, "pci", default = (cellId % 504).coerceIn(0, 503))
            normalized.put(JSONObject().apply {
                put("type", type)
                put("radio", source.optString("radio", type))
                put("mcc", mcc)
                put("mnc", mnc)
                put("tac", area)
                put("lac", area)
                put("ci", cellId)
                put("cid", cellId)
                put("cellid", cellId)
                put("pci", pci)
                put("dbm", dbm)
                put("isRegistered", i == 0)
                if (source.has("lat")) put("lat", source.optDouble("lat"))
                if (source.has("lon")) put("lon", source.optDouble("lon"))
                if (source.has("range")) put("range", source.optInt("range"))
                if (source.has("samples")) put("samples", source.optInt("samples"))
            })
        }
        return normalized
    }

    private fun normalizeRadioType(radio: String): String {
        return when (radio.uppercase(Locale.US)) {
            "GSM" -> "GSM"
            "UMTS", "WCDMA" -> "WCDMA"
            "NR", "NR5G", "5G" -> "NR"
            "CDMA" -> "CDMA"
            else -> "LTE"
        }
    }

    private fun positiveInt(source: JSONObject, vararg keys: String, default: Int): Int {
        for (key in keys) {
            if (!source.has(key) || source.isNull(key)) continue
            val value = source.optInt(key, Int.MIN_VALUE)
            if (value > 0) return value
            val parsed = source.optString(key).toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        return default
    }

    private fun signalDbm(source: JSONObject, index: Int): Int {
        val direct = source.optInt("dbm", Int.MIN_VALUE)
        if (direct in -140..-40) return direct

        val average = source.optInt("averageSignalStrength", Int.MIN_VALUE)
        if (average in -140..-40) return average

        val signal = source.optInt("signal", Int.MIN_VALUE)
        if (signal in -140..-40) return signal

        return (-70 - index * 3).coerceAtLeast(-110)
    }
}
