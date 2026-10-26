package com.vincenthzr.locationspoofer.data.repository

import com.vincenthzr.locationspoofer.utils.OnlineLookup
import com.vincenthzr.locationspoofer.utils.TokenCheck
import com.vincenthzr.locationspoofer.utils.WigleClient

class WifiRepository(private val wigleClient: WigleClient) {

    suspend fun validateToken(token: String): TokenCheck = wigleClient.validateToken(token)

    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): OnlineLookup =
        wigleClient.fetchWifiData(lat, lng, token)
}
