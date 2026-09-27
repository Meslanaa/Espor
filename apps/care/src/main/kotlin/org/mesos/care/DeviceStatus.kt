package org.mesos.care

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File

/** Everything Device Care shows, read from Android without special permissions. */
data class DeviceState(
    val snapshot: CareSnapshot,
    val plugged: Plug,
    val batteryHealth: BatteryHealth,
    val voltageMv: Int?,
    /** Positive while charging, in mA; null when the device does not report it. */
    val currentMa: Int?,
    val chargeTimeRemainingMs: Long?,
    val technology: String?,
    val lowMemory: Boolean,
    val cores: Int,
    val abi: String,
    val uptimeMs: Long,
    val mesosCacheBytes: Long,
)

enum class Plug { NONE, AC, USB, WIRELESS, DOCK }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, FAILURE, UNKNOWN }

internal object DeviceStatus {

    fun read(context: Context): DeviceState {
        // The last battery broadcast (sticky); no receiver is registered.
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
        val level = battery?.let {
            val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw >= 0 && scale > 0) raw * 100 / scale else -1
        } ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = when (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> Plug.AC
            BatteryManager.BATTERY_PLUGGED_USB -> Plug.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Plug.WIRELESS
            8 -> Plug.DOCK // BATTERY_PLUGGED_DOCK, Android 14.
            else -> Plug.NONE
        }
        val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            else -> BatteryHealth.UNKNOWN
        }
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

        val manager = context.getSystemService(BatteryManager::class.java)
        // Reported in microamperes on most devices; some report milliamperes.
        val currentMa = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
            ?.let { if (kotlin.math.abs(it) > 20_000) it / 1000 else it }
        val remaining = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && charging) {
            manager?.computeChargeTimeRemaining()?.takeIf { it > 0 }
        } else {
            null
        }

        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)

        val stat = try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: IllegalArgumentException) {
            null
        }

        return DeviceState(
            snapshot = CareSnapshot(
                batteryLevel = level,
                charging = charging,
                batteryHealthy = when (health) {
                    BatteryHealth.UNKNOWN -> null
                    BatteryHealth.GOOD -> true
                    else -> false
                },
                batteryTemperatureC = temperature,
                storageFreeBytes = stat?.availableBytes ?: 0L,
                storageTotalBytes = stat?.totalBytes ?: 0L,
                memoryAvailableBytes = memory.availMem,
                memoryTotalBytes = memory.totalMem,
            ),
            plugged = plugged,
            batteryHealth = health,
            voltageMv = voltage,
            currentMa = currentMa,
            chargeTimeRemainingMs = remaining,
            technology = battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            lowMemory = memory.lowMemory,
            cores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            uptimeMs = SystemClock.elapsedRealtime(),
            mesosCacheBytes = cacheDirs(context).sumOf(::sizeOf),
        )
    }

    /** Deletes MesOS's own cached files (thumbnails, downloads in progress are elsewhere). */
    fun clearMesOSCache(context: Context): Long {
        var freed = 0L
        cacheDirs(context).forEach { dir ->
            dir.listFiles()?.forEach { child ->
                val size = sizeOf(child)
                if (child.deleteRecursively()) freed += size
            }
        }
        return freed
    }

    private fun cacheDirs(context: Context): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.listFiles()?.sumOf(::sizeOf) ?: 0L else file.length()
}
