package org.mesos.launcher.control

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog

/** State of everything the control center shows as a toggle or slider. */
data class SystemStatus(
    val wifiEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val bluetoothAvailable: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val torchAvailable: Boolean = false,
    val torchOn: Boolean = false,
    val doNotDisturb: Boolean = false,
    val autoRotate: Boolean = false,
    val locationOn: Boolean = false,
    val powerSave: Boolean = false,
    /** 0..1 */
    val brightness: Float = 0.5f,
    /** 0..1 */
    val volume: Float = 0.5f,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val canWriteSettings: Boolean = false,
)

/**
 * Reads and changes system settings for the control center, always through public
 * APIs. Where Android does not let apps change a setting (Wi-Fi on Android 10+,
 * Bluetooth, location, battery saver) the matching Android panel opens instead.
 */
class SystemToggles(context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val torchCameraId: String? = findTorchCamera()
    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    private var torchOn = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchOn = enabled
                refresh()
            }
        }
    }

    fun start() {
        if (torchCameraId != null) {
            try {
                cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            } catch (e: RuntimeException) {
                MesOSLog.w(MesOSLog.LAUNCHER, "Torch callback unavailable", e)
            }
        }
        refresh()
    }

    fun stop() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not unregister torch callback", e)
        }
    }

    fun refresh() {
        val wifi = appContext.getSystemService(WifiManager::class.java)
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = try {
            connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        } catch (e: SecurityException) {
            null
        }
        val bluetooth = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        val notifications = appContext.getSystemService(NotificationManager::class.java)
        val power = appContext.getSystemService(PowerManager::class.java)
        val audio = appContext.getSystemService(AudioManager::class.java)
        val battery = appContext.getSystemService(BatteryManager::class.java)
        val resolver = appContext.contentResolver

        val maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.takeIf { it > 0 } ?: 1
        _status.value = SystemStatus(
            wifiEnabled = safe(false) { wifi?.isWifiEnabled == true },
            wifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            bluetoothAvailable = bluetooth != null,
            bluetoothEnabled = safe(false) { bluetooth?.isEnabled == true },
            torchAvailable = torchCameraId != null,
            torchOn = torchOn,
            doNotDisturb = notifications != null &&
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            autoRotate = safe(false) { Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 },
            locationOn = isLocationOn(),
            powerSave = power?.isPowerSaveMode == true,
            brightness = safe(0.5f) { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f },
            volume = (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) / maxVolume.toFloat(),
            batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
            charging = battery?.isCharging == true,
            canWriteSettings = Settings.System.canWrite(appContext),
        )
    }

    /** The intent to open for [toggle] instead of changing it directly, or null when done here. */
    fun toggle(toggle: Toggle): Intent? {
        val current = _status.value
        val intent: Intent? = when (toggle) {
            Toggle.WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(WifiManager::class.java)?.isWifiEnabled = !current.wifiEnabled
                null
            }
            Toggle.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            Toggle.TORCH -> {
                setTorch(!current.torchOn)
                null
            }
            Toggle.DO_NOT_DISTURB -> {
                val manager = appContext.getSystemService(NotificationManager::class.java)
                if (manager != null && manager.isNotificationPolicyAccessGranted) {
                    manager.setInterruptionFilter(
                        if (current.doNotDisturb) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    )
                    null
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }
            }
            Toggle.AUTO_ROTATE -> if (Settings.System.canWrite(appContext)) {
                Settings.System.putInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current.autoRotate) 0 else 1)
                null
            } else {
                writeSettingsIntent()
            }
            Toggle.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            Toggle.POWER_SAVE -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
        refresh()
        return intent
    }

    /** Sets screen brightness (0..1), or returns the permission screen when MesOS may not. */
    fun setBrightness(value: Float): Intent? {
        if (!Settings.System.canWrite(appContext)) return writeSettingsIntent()
        val resolver = appContext.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (value.coerceIn(0.02f, 1f) * 255).toInt())
        _status.value = _status.value.copy(brightness = value)
        return null
    }

    fun setVolume(value: Float) {
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value.coerceIn(0f, 1f) * max).toInt(), 0)
        } catch (e: SecurityException) {
            // Do Not Disturb can block volume changes.
            MesOSLog.w(MesOSLog.LAUNCHER, "Volume change refused", e)
        }
        _status.value = _status.value.copy(volume = value)
    }

    private fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${appContext.packageName}"))

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        try {
            cameraManager?.setTorchMode(id, on)
            torchOn = on
        } catch (e: CameraAccessException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Torch unavailable", e)
        } catch (e: IllegalArgumentException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Torch unavailable", e)
        }
    }

    private fun findTorchCamera(): String? =
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private fun isLocationOn(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(appContext.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    }

    private inline fun <T> safe(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: RuntimeException) {
            fallback
        }
}

enum class Toggle { WIFI, BLUETOOTH, TORCH, DO_NOT_DISTURB, AUTO_ROTATE, LOCATION, POWER_SAVE }
