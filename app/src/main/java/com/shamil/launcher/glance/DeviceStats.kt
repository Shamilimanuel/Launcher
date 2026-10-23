package com.shamil.launcher.glance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/** Battery, memory (RAM) and storage, Leyline-style. None of these need a permission. */
data class DeviceStats(
    val batteryPercent: Int,
    val charging: Boolean,
    val memoryUsedPercent: Int,
    val storageUsedPercent: Int,
    val storageFreeGb: Int,
) {
    companion object {
        fun read(context: Context): DeviceStats {
            // The battery's last status message (a "sticky" broadcast: Android keeps the latest one for anyone to read).
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)

            val disk = StatFs(Environment.getDataDirectory().path)
            val total = disk.totalBytes.coerceAtLeast(1)
            val free = disk.availableBytes

            return DeviceStats(
                batteryPercent = if (level >= 0) level * 100 / scale else 0,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                memoryUsedPercent = ((memory.totalMem - memory.availMem) * 100 / memory.totalMem.coerceAtLeast(1)).toInt(),
                storageUsedPercent = ((total - free) * 100 / total).toInt(),
                storageFreeGb = (free / 1_000_000_000L).toInt(),
            )
        }
    }
}
