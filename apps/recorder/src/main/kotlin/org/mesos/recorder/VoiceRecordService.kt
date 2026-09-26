package org.mesos.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mesos.core.log.MesOSLog
import java.io.IOException
import kotlin.math.ln

/** The voice recorder's state, shared by the service, the notification and the app. */
object VoiceRecorder {

    sealed interface State {
        data object Idle : State

        /** [startedAt] and [pausedAt] are elapsedRealtime; [pausedTotal] is time spent paused. */
        data class Recording(val startedAt: Long, val pausedAt: Long?, val pausedTotal: Long) : State {
            val paused: Boolean get() = pausedAt != null

            fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long =
                ((pausedAt ?: now) - startedAt - pausedTotal).coerceAtLeast(0)
        }
    }

    internal val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Recent input levels, 0..1, oldest first. */
    internal val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    /** Bumped whenever a recording is saved, so lists can reload. */
    internal val _saved = MutableStateFlow(0)
    val saved: StateFlow<Int> = _saved.asStateFlow()

    /** Set when recording could not start or finish. */
    internal val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun clearError() {
        _error.value = false
    }

    const val LEVEL_HISTORY = 72

    /** MediaRecorder amplitude (0..32767) → 0..1 on a log scale that matches hearing. */
    fun level(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val db = 20 * ln(amplitude / 32767.0) / ln(10.0)
        return ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Records from the microphone into Recordings/MesOS as AAC (.m4a). A foreground
 * service with a visible notification, so recording continues while the user
 * does something else, and Android shows the microphone indicator.
 */
class VoiceRecordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var output: Output? = null
    private var levelJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finish(save = true)
            ACTION_DISCARD -> finish(save = false)
        }
        if (recorder == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun start() {
        if (recorder != null) return
        goForeground(paused = false, elapsed = 0)
        val name = RecordingStore.newName(getString(R.string.recorder_file_prefix), MediaKind.AUDIO)
        val target = RecordingStore.create(this, name, MediaKind.AUDIO)
        if (target == null) {
            fail("No place to save the recording")
            return
        }
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(128_000)
            media.setAudioSamplingRate(44_100)
            media.setAudioChannels(1)
            val descriptor = target.descriptor
            if (descriptor != null) media.setOutputFile(descriptor.fileDescriptor) else media.setOutputFile(target.file!!.absolutePath)
            media.prepare()
            media.start()
        } catch (e: IOException) {
            media.release()
            RecordingStore.discard(this, target)
            fail("Recorder could not start", e)
            return
        } catch (e: RuntimeException) {
            media.release()
            RecordingStore.discard(this, target)
            fail("Recorder could not start", e)
            return
        }
        recorder = media
        output = target
        VoiceRecorder._levels.value = emptyList()
        VoiceRecorder._state.value = VoiceRecorder.State.Recording(SystemClock.elapsedRealtime(), null, 0)
        MesOSLog.i(MesOSLog.SYSTEM, "Voice recording started")
        levelJob = scope.launch {
            while (isActive) {
                val current = VoiceRecorder.state.value
                if (current is VoiceRecorder.State.Recording && !current.paused) {
                    val amplitude = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (e: IllegalStateException) {
                        0
                    }
                    VoiceRecorder._levels.value = (VoiceRecorder.levels.value + VoiceRecorder.level(amplitude)).takeLast(VoiceRecorder.LEVEL_HISTORY)
                }
                delay(70)
            }
        }
    }

    private fun pause() {
        val current = VoiceRecorder.state.value as? VoiceRecorder.State.Recording ?: return
        if (current.paused) return
        try {
            recorder?.pause()
        } catch (e: IllegalStateException) {
            return
        }
        val paused = current.copy(pausedAt = SystemClock.elapsedRealtime())
        VoiceRecorder._state.value = paused
        goForeground(paused = true, elapsed = paused.elapsed())
    }

    private fun resume() {
        val current = VoiceRecorder.state.value as? VoiceRecorder.State.Recording ?: return
        val pausedAt = current.pausedAt ?: return
        try {
            recorder?.resume()
        } catch (e: IllegalStateException) {
            return
        }
        val resumed = current.copy(pausedAt = null, pausedTotal = current.pausedTotal + (SystemClock.elapsedRealtime() - pausedAt))
        VoiceRecorder._state.value = resumed
        goForeground(paused = false, elapsed = resumed.elapsed())
    }

    private fun finish(save: Boolean) {
        val media = recorder ?: return
        val target = output
        levelJob?.cancel()
        val stopped = try {
            media.stop()
            true
        } catch (e: RuntimeException) {
            // Stopped right after starting: nothing usable was written.
            false
        } finally {
            media.release()
            recorder = null
            output = null
        }
        if (target != null) {
            if (save && stopped) {
                RecordingStore.publish(this, target)
                VoiceRecorder._saved.value += 1
                MesOSLog.i(MesOSLog.SYSTEM, "Voice recording saved")
            } else {
                RecordingStore.discard(this, target)
            }
        }
        VoiceRecorder._state.value = VoiceRecorder.State.Idle
        VoiceRecorder._levels.value = emptyList()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String, error: Throwable? = null) {
        MesOSLog.w(MesOSLog.SYSTEM, message, error)
        VoiceRecorder._error.value = true
        VoiceRecorder._state.value = VoiceRecorder.State.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) finish(save = true)
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(paused: Boolean, elapsed: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recorder_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = if (paused) ACTION_RESUME else ACTION_PAUSE
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_recorder_notification)
            .setContentTitle(getString(if (paused) R.string.recorder_paused else R.string.recorder_recording))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(!paused)
            .setUsesChronometer(!paused)
            .setWhen(System.currentTimeMillis() - elapsed)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (paused) R.string.recorder_resume else R.string.recorder_pause),
                    PendingIntent.getService(this, 1, intent(this, toggle), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.recorder_stop),
                    PendingIntent.getService(this, 2, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "mesos_recorder"
        private const val NOTIFICATION_ID = 4200
        const val ACTION_START = "org.mesos.recorder.action.START"
        const val ACTION_PAUSE = "org.mesos.recorder.action.PAUSE"
        const val ACTION_RESUME = "org.mesos.recorder.action.RESUME"
        const val ACTION_STOP = "org.mesos.recorder.action.STOP"
        const val ACTION_DISCARD = "org.mesos.recorder.action.DISCARD"

        fun intent(context: Context, action: String): Intent =
            Intent(context, VoiceRecordService::class.java).setAction(action)
    }
}
