package com.shamil.launcher.glance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

/** Battery, memory (RAM), storage and a few extras for the stats card, Leyline-style. None of these need a permission question. */
data class DeviceStats(
    val batteryPercent: Int,
    val charging: Boolean,
    val memoryUsedPercent: Int,
    val storageUsedPercent: Int,
    val storageFreeGb: Int,
    /** The battery's temperature in °C (phones get warm while gaming or charging). */
    val batteryTempC: Float = 0f,
    /** How long since the phone was last restarted. */
    val uptimeHours: Int = 0,
    /** Wi-Fi signal 0–100%, or null when not on Wi-Fi. */
    val wifiPercent: Int? = null,
) {
    companion object {
        @Suppress("DEPRECATION")   // connectionInfo: still the simplest way to read the signal strength
        fun read(context: Context): DeviceStats {
            // The battery's last status message (a "sticky" broadcast: Android keeps the latest one for anyone to read).
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            // Tenths of a degree: 312 = 31.2 °C.
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)

            val disk = StatFs(Environment.getDataDirectory().path)
            val total = disk.totalBytes.coerceAtLeast(1)
            val free = disk.availableBytes

            // Wi-Fi strength as a 0–100% level (needs only the normal "read Wi-Fi state" permission).
            val wifi = runCatching {
                val manager = context.applicationContext.getSystemService(WifiManager::class.java)
                val rssi = manager.connectionInfo?.rssi ?: return@runCatching null
                if (rssi <= -127 || !manager.isWifiEnabled) return@runCatching null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    manager.calculateSignalLevel(rssi) * 100 / manager.maxSignalLevel.coerceAtLeast(1)
                } else {
                    WifiManager.calculateSignalLevel(rssi, 5) * 25
                }
            }.getOrNull()

            return DeviceStats(
                batteryPercent = if (level >= 0) level * 100 / scale else 0,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                memoryUsedPercent = ((memory.totalMem - memory.availMem) * 100 / memory.totalMem.coerceAtLeast(1)).toInt(),
                storageUsedPercent = ((total - free) * 100 / total).toInt(),
                storageFreeGb = (free / 1_000_000_000L).toInt(),
                batteryTempC = temp / 10f,
                uptimeHours = (SystemClock.elapsedRealtime() / 3_600_000L).toInt(),
                wifiPercent = wifi,
            )
        }
    }
}
