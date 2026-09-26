package org.mesos.recorder

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSIntents
import org.mesos.core.log.MesOSLog
import org.mesos.core.system.ScreenRecording
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Starts or stops screen recording (control center tile, MesOS Recorder).
 * Android shows its own consent dialog every time; MesOS cannot skip it.
 */
class ScreenRecordActivity : ComponentActivity() {

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            startForegroundService(ScreenRecordService.start(this, result.resultCode, data, captureSize()))
        } else {
            MesOSLog.i(MesOSLog.SYSTEM, "Screen recording not allowed by the user")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // Waiting for the consent result.
        if (ScreenRecording.active.value) {
            startService(ScreenRecordService.stop(this))
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finish()
            return
        }
        consent.launch(manager.createScreenCaptureIntent())
    }

    /** Screen size scaled to at most 1920 px on the long side, in multiples of 16 for the encoder. */
    private fun captureSize(): IntArray {
        val dpi = resources.displayMetrics.densityDpi
        var width: Int
        var height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        val scale = minOf(1f, 1920f / max(width, height))
        width = ((width * scale).roundToInt() / 16 * 16).coerceAtLeast(16)
        height = ((height * scale).roundToInt() / 16 * 16).coerceAtLeast(16)
        return intArrayOf(width, height, dpi)
    }
}

/** Records the screen (and the microphone, when allowed) into Movies/MesOS. */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var output: Output? = null
    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The user stopped sharing from Android's own UI.
            finish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (recorder == null) {
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                start(intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED), data, intent.getIntArrayExtra(EXTRA_SIZE))
            }
            ACTION_STOP -> finish()
            else -> if (recorder == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent?, size: IntArray?) {
        val withAudio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // Foreground first: Android requires it before the capture token is used.
        goForeground(withAudio)
        if (data == null || size == null || size.size < 3) {
            stopEverything()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val media = try {
            manager?.getMediaProjection(code, data)
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Screen capture token rejected", e)
            null
        } catch (e: IllegalStateException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Screen capture token already used", e)
            null
        }
        if (media == null) {
            stopEverything()
            return
        }
        media.registerCallback(projectionCallback, handler)
        projection = media

        val width = size[0]
        val height = size[1]
        val dpi = size[2]
        val name = RecordingStore.newName(getString(R.string.screen_file_prefix), MediaKind.VIDEO)
        val target = RecordingStore.create(this, name, MediaKind.VIDEO)
        if (target == null) {
            stopEverything()
            return
        }
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            if (withAudio) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
            }
            rec.setVideoSize(width, height)
            rec.setVideoFrameRate(30)
            rec.setVideoEncodingBitRate(max(2_000_000, width * height * 4))
            val descriptor = target.descriptor
            if (descriptor != null) rec.setOutputFile(descriptor.fileDescriptor) else rec.setOutputFile(target.file!!.absolutePath)
            rec.prepare()
            display = media.createVirtualDisplay(
                "MesOS screen recording",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface,
                null,
                null,
            )
            rec.start()
        } catch (e: IOException) {
            rec.release()
            RecordingStore.discard(this, target)
            MesOSLog.w(MesOSLog.SYSTEM, "Screen recorder could not start", e)
            stopEverything()
            return
        } catch (e: RuntimeException) {
            rec.release()
            RecordingStore.discard(this, target)
            MesOSLog.w(MesOSLog.SYSTEM, "Screen recorder could not start", e)
            stopEverything()
            return
        }
        recorder = rec
        output = target
        ScreenRecording.setActive(true)
        MesOSLog.i(MesOSLog.SYSTEM, "Screen recording started ($width×$height, audio=$withAudio)")
    }

    private fun finish() {
        val rec = recorder
        val target = output
        recorder = null
        output = null
        var saved = false
        if (rec != null) {
            saved = try {
                rec.stop()
                true
            } catch (e: RuntimeException) {
                false
            }
            rec.release()
        }
        if (target != null) {
            if (saved) RecordingStore.publish(this, target) else RecordingStore.discard(this, target)
        }
        stopEverything()
        if (saved && target != null) {
            MesOSLog.i(MesOSLog.SYSTEM, "Screen recording saved")
            notifySaved(target)
        }
    }

    private fun stopEverything() {
        display?.release()
        display = null
        projection?.let { media ->
            media.unregisterCallback(projectionCallback)
            media.stop()
        }
        projection = null
        ScreenRecording.setActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) finish()
        super.onDestroy()
    }

    private fun channel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.screen_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun goForeground(withAudio: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.let(::channel)
        val stop = PendingIntent.getService(this, 3, stop(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_screen_record_notification)
            .setContentTitle(getString(R.string.screen_recording))
            .setContentText(getString(R.string.screen_recording_text))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setContentIntent(stop)
            .addAction(Notification.Action.Builder(null, getString(R.string.recorder_stop), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (withAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifySaved(target: Output) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        channel(manager)
        val uri = target.viewUri(this) ?: return
        val view = PendingIntent.getActivity(
            this,
            4,
            MesOSApps.viewInPhotos(this, uri, MediaKind.VIDEO.mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            SAVED_ID,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_screen_record_notification)
                .setContentTitle(getString(R.string.screen_saved))
                .setContentText(getString(R.string.screen_saved_text))
                .setContentIntent(view)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL = "mesos_screen_recorder"
        private const val NOTIFICATION_ID = 4300
        private const val SAVED_ID = 4301
        private const val ACTION_START = "org.mesos.recorder.screen.START"
        private const val ACTION_STOP = "org.mesos.recorder.screen.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_SIZE = "size"

        fun start(context: Context, code: Int, data: Intent, size: IntArray): Intent =
            Intent(context, ScreenRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_SIZE, size)

        fun stop(context: Context): Intent =
            Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP)

        /** Starts (or stops) screen recording through [ScreenRecordActivity]. */
        fun toggleIntent(context: Context): Intent =
            Intent(MesOSIntents.ACTION_TOGGLE_SCREEN_RECORDING)
                .setClassName(context.packageName, MesOSApps.SCREEN_RECORDER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
