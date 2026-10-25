package com.vincenthzr.locationspoofer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

@android.annotation.SuppressLint("MissingPermission", "NewApi")
class EnvironmentScanner(private val context: Context) {

    // startScan / connectionInfo / ScanResult.SSID 在 minSdk 26 上没有可替代的 API，
    // allNetworks 用来判断"任意一个 Wi-Fi 网络已连接"（蜂窝同时在线时 activeNetwork 不一定是 Wi-Fi）
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun scanWifi(): String = withContext(Dispatchers.IO) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val resultObj = JSONObject()
        try {
            val isWifiConnected = connectivityManager.allNetworks.any { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                // 确保这个 Wi-Fi 网络具备基础的连接能力
                caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                    (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            resultObj.put("isConnected", isWifiConnected)

            val connectionInfo = wifiManager.connectionInfo
            val connectedBssid = if (isWifiConnected) connectionInfo?.bssid else null
            try {
                wifiManager.startScan()
            } catch (e: Throwable) {
            }
            val results = wifiManager.scanResults ?: emptyList()

            // 1. 优先提取当前正在连接的 Wi-Fi 信息
            if (isWifiConnected && connectedBssid != null && connectedBssid != "02:00:00:00:00:00") {
                val connObj = JSONObject()
                connObj.put("bssid", connectedBssid)
                val rawSsid = connectionInfo.ssid
                var cleanSsid =
                    if (rawSsid != null && rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
                        rawSsid.substring(1, rawSsid.length - 1)
                    } else {
                        rawSsid ?: ""
                    }

                val match = results.find { it.BSSID == connectedBssid }
                // 如果是 unknown ssid 或为空，尝试从扫描结果中恢复真实 SSID
                if (cleanSsid == "<unknown ssid>" || cleanSsid.isEmpty()) {
                    if (match != null && !match.SSID.isNullOrEmpty()) {
                        cleanSsid = match.SSID
                    }
                }

                connObj.put("ssid", if (cleanSsid == "<unknown ssid>") "" else cleanSsid)
                connObj.put("vendor", MacVendorHelper.getVendor(connectedBssid))
                connObj.put("level", connectionInfo.rssi)
                connObj.put("frequency", connectionInfo.frequency)
                connObj.put("channel", MacVendorHelper.frequencyToChannel(connectionInfo.frequency))
                connObj.put("capabilities", match?.capabilities ?: "[WPA2-PSK-CCMP][ESS]")
                try {
                    connObj.put("macAddress", connectionInfo.macAddress)
                } catch (e: Throwable) {
                }
                try {
                    connObj.put("linkSpeed", connectionInfo.linkSpeed)
                } catch (e: Throwable) {
                }
                try {
                    connObj.put("networkId", connectionInfo.networkId)
                } catch (e: Throwable) {
                }
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    try {
                        connObj.put("wifiStandard", connectionInfo.wifiStandard)
                    } catch (e: Throwable) {
                    }
                }
                resultObj.put("connectedWifi", connObj)
            } else {
                resultObj.put("connectedWifi", JSONObject.NULL)
            }

            // 2. 提取周围扫描到的其他 Wi-Fi（去重，不包含当前已连接的）
            val nearbyArray = JSONArray()
            val seenBssids = mutableSetOf<String>()
            if (isWifiConnected && connectedBssid != null) {
                seenBssids.add(connectedBssid)
            }
            results.forEach { scanResult ->
                val bssid = scanResult.BSSID
                if (bssid != null && !seenBssids.contains(bssid)) {
                    seenBssids.add(bssid)
                    val obj = JSONObject()
                    obj.put("bssid", bssid)
                    obj.put("ssid", scanResult.SSID ?: "")
                    obj.put("vendor", MacVendorHelper.getVendor(bssid))
                    obj.put("level", scanResult.level)
                    obj.put("capabilities", scanResult.capabilities ?: "")
                    obj.put("frequency", scanResult.frequency)
                    obj.put("channel", MacVendorHelper.frequencyToChannel(scanResult.frequency))
                    nearbyArray.put(obj)
                }
            }
            resultObj.put("nearbyWifi", nearbyArray)
        } catch (e: Exception) {
            e.printStackTrace()
            resultObj.put("isConnected", false)
            resultObj.put("connectedWifi", JSONObject.NULL)
            resultObj.put("nearbyWifi", JSONArray())
        }
        resultObj.toString()
    }

    // CDMA 已被系统标为废弃，但导入 / 导出的数据格式里仍有 CDMA 基站，继续照常采集
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    suspend fun scanCell(): String = withContext(Dispatchers.IO) {
        // 之前这里连了 Wi-Fi 就直接跳过基站扫描，但 Wi-Fi 和基站是同一个物理位置的两个
        // 独立维度，并不互斥，跳过只会导致最常见的采集场景（家里/公司都连着 Wi-Fi）
        // 永远采不到基站数据，纯属早期写错，去掉这个限制。
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val jsonArray = JSONArray()
        try {
            val allCellInfo =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(3000L) {
                            suspendCancellableCoroutine { continuation ->
                                telephonyManager.requestCellInfoUpdate(
                                    context.mainExecutor,
                                    object : android.telephony.TelephonyManager.CellInfoCallback() {
                                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                            if (continuation.isActive) continuation.resume(cellInfo)
                                        }

                                        override fun onError(errorCode: Int, detail: Throwable?) {
                                            if (continuation.isActive) continuation.resume(
                                                telephonyManager.allCellInfo ?: emptyList()
                                            )
                                        }
                                    }
                                )
                            }
                        } ?: (telephonyManager.allCellInfo ?: emptyList())
                    } catch (e: Exception) {
                        telephonyManager.allCellInfo ?: emptyList()
                    }
                } else {
                    telephonyManager.allCellInfo ?: emptyList()
                }

            allCellInfo.forEach { cellInfo ->
                val obj = JSONObject()
                obj.put("isRegistered", cellInfo.isRegistered)

                when (cellInfo) {
                    is CellInfoLte -> {
                        obj.put("type", "LTE")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id.tac)
                        obj.put("ci", id.ci)
                        obj.put("pci", id.pci)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoGsm -> {
                        obj.put("type", "GSM")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoWcdma -> {
                        obj.put("type", "WCDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        obj.put("psc", id.psc)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }

                    is CellInfoNr -> {
                        obj.put("type", "NR")
                        val id = cellInfo.cellIdentity as? android.telephony.CellIdentityNr
                        obj.put("mcc", id?.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id?.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id?.tac ?: Int.MAX_VALUE)
                        obj.put("nci", id?.nci ?: Long.MAX_VALUE)
                        obj.put("pci", id?.pci ?: Int.MAX_VALUE)
                        val signal =
                            cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr
                        obj.put("dbm", signal?.dbm ?: 0)
                    }

                    is CellInfoCdma -> {
                        obj.put("type", "CDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("networkId", id.networkId)
                        obj.put("systemId", id.systemId)
                        obj.put("basestationId", id.basestationId)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                }
                jsonArray.put(obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        jsonArray.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun scanBluetooth(): String = withContext(Dispatchers.IO) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner

        val jsonArray = JSONArray()
        if (scanner == null || !adapter.isEnabled) {
            return@withContext jsonArray.toString()
        }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val resultsList = mutableListOf<ScanResult>()
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        if (result != null) {
                            resultsList.add(result)
                        }
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        if (results != null) {
                            resultsList.addAll(results)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                    }
                }

                // 开始扫描
                scanner.startScan(callback)

                // 2 秒后停止扫描；绑定到外层挂起协程的 context，避免脱离生命周期成为孤儿协程
                kotlinx.coroutines.CoroutineScope(cont.context).launch {
                    delay(2000)
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {
                    }

                    // 去重并转换为 JSON
                    val deduped = resultsList.distinctBy { it.device.address }
                    deduped.forEach { res ->
                        val obj = JSONObject()
                        obj.put("address", res.device.address)
                        obj.put("name", res.device.name ?: "")
                        obj.put("rssi", res.rssi)

                        // 如果可用，解析扫描记录的字节
                        val recordBytes = res.scanRecord?.bytes
                        if (recordBytes != null) {
                            val hexString = recordBytes.joinToString("") { "%02X".format(it) }
                            obj.put("scanRecordHex", hexString)
                        }
                        jsonArray.put(obj)
                    }
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                cont.invokeOnCancellation {
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        jsonArray.toString()
    }
}
