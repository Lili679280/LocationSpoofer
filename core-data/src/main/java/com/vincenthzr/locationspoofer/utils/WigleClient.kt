package com.vincenthzr.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class WigleClient {
    private val client = OkHttpClient()

    companion object {
        private val NAME_AND_TOKEN = Regex("^[^:\\s]+:[^:\\s]+$")

        /**
         * WiGLE 的 Basic 认证值：只接受账户页的 "Encoded for use"，即 "API 名:API Token" 的 Base64。
         * 填了别的（只有 32 位的 API Token、没编码的 "名:Token" 等）返回 null。
         */
        fun authToken(input: String): String? {
            val value = input.trim()
            if (value.isEmpty()) return null
            val decoded = runCatching { String(java.util.Base64.getDecoder().decode(value), Charsets.UTF_8) }.getOrNull()
            return value.takeIf { decoded != null && NAME_AND_TOKEN.matches(decoded) }
        }
    }

    /** 检测 Token（调用 /profile/user 接口） */
    suspend fun validateToken(token: String): TokenCheck = withContext(Dispatchers.IO) {
        val auth = authToken(token) ?: return@withContext TokenCheck.BAD_FORMAT
        try {
            val request = Request.Builder()
                .url("https://api.wigle.net/api/v2/profile/user")
                .addHeader("Authorization", "Basic $auth")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> TokenCheck.INVALID
                    !response.isSuccessful -> TokenCheck.NETWORK_ERROR
                    JSONObject(response.body?.string().orEmpty()).optBoolean("success") -> TokenCheck.VALID
                    else -> TokenCheck.INVALID
                }
            }
        } catch (e: Exception) {
            TokenCheck.NETWORK_ERROR
        }
    }

    /** 查询 (lat, lng) 附近约 ±200 米内的 Wi-Fi（WGS-84），最多 10 个 */
    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): OnlineLookup =
        withContext(Dispatchers.IO) {
            val auth = authToken(token) ?: return@withContext OnlineLookup.Unauthorized

            val url = "https://api.wigle.net/api/v2/network/search?" +
                "latrange1=${lat - 0.002}&latrange2=${lat + 0.002}&longrange1=${lng - 0.002}&longrange2=${lng + 0.002}"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic $auth")
                .addHeader("Accept", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 || response.code == 403 -> OnlineLookup.Unauthorized
                        response.code == 429 -> OnlineLookup.Failed(OnlineLookup.FailReason.RATE_LIMITED)
                        !response.isSuccessful -> OnlineLookup.Failed(OnlineLookup.FailReason.SERVER_ERROR, response.code)
                        else -> {
                            val results = JSONObject(response.body?.string().orEmpty()).optJSONArray("results")
                            val wifiList = JSONArray()
                            for (i in 0 until (results?.length() ?: 0)) {
                                if (wifiList.length() >= 10) break
                                val item = results!!.getJSONObject(i)
                                val bssid = item.optString("netid")
                                if (bssid.isBlank()) continue
                                wifiList.put(JSONObject().put("ssid", item.optString("ssid")).put("bssid", bssid))
                            }
                            if (wifiList.length() == 0) OnlineLookup.NoData
                            else OnlineLookup.Found(wifiList.toString(), wifiList.length())
                        }
                    }
                }
            } catch (e: Exception) {
                OnlineLookup.Failed(OnlineLookup.FailReason.NETWORK)
            }
        }
}
