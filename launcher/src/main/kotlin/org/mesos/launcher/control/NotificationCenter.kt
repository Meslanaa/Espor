package org.mesos.launcher.control

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog

/** One action button of a notification ("Reply", "Mark as read", …). */
data class NotificationAction(
    val title: String,
    val intent: PendingIntent,
    val remoteInput: RemoteInput?,
)

/** A notification as MesOS shows it in the notification center. */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val user: UserHandle,
    val title: String,
    val text: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val autoCancel: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<NotificationAction>,
) {
    val replyAction: NotificationAction? get() = actions.firstOrNull { it.remoteInput?.allowFreeFormInput == true }
}

/** What the media card shows: the most recently active media session. */
data class MediaSnapshot(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
)

/**
 * Live notifications, per-app badge counts and the active media session, fed by
 * [MesOSNotificationService] once the user granted notification access.
 */
object NotificationCenter {

    private val _connected = MutableStateFlow(false)

    /** True while Android has MesOS's notification listener bound (access granted). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Number of unread (non-ongoing) notifications per package. */
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    private val _media = MutableStateFlow<MediaSnapshot?>(null)
    val media: StateFlow<MediaSnapshot?> = _media.asStateFlow()

    private var service: MesOSNotificationService? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindController(controllers?.firstOrNull())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() = bindController(null)
    }

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MesOSNotificationService::class.java)

    /** Whether the user granted MesOS notification access (the listener may not be bound yet). */
    fun isAccessGranted(context: Context): Boolean {
        val component = listenerComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager?.isNotificationListenerAccessGranted(component) == true
        }
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /** Android's screen where the user grants MesOS notification access. */
    fun accessSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    internal fun attach(listener: MesOSNotificationService) {
        service = listener
        _connected.value = true
        refresh()
        val manager = listener.getSystemService(MediaSessionManager::class.java)
        sessionManager = manager
        try {
            val component = listenerComponent(listener)
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            bindController(manager?.getActiveSessions(component)?.firstOrNull())
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Media sessions unavailable", e)
        }
        MesOSLog.i(MesOSLog.LAUNCHER, "Notification listener connected")
    }

    internal fun detach() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        bindController(null)
        service = null
        _connected.value = false
        _notifications.value = emptyList()
        _badges.value = emptyMap()
        MesOSLog.i(MesOSLog.LAUNCHER, "Notification listener disconnected")
    }

    internal fun refresh() {
        val listener = service ?: return
        val active = try {
            listener.activeNotifications.orEmpty()
        } catch (e: RuntimeException) {
            // SecurityException right after access was revoked.
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not read notifications", e)
            return
        }
        val items = active.filter(::isShown).map(::toItem).sortedByDescending { it.postTime }
        _notifications.value = items
        _badges.value = items.filter { !it.isOngoing }.groupingBy { it.packageName }.eachCount()
    }

    fun dismiss(item: NotificationItem) {
        try {
            service?.cancelNotification(item.key)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not dismiss ${item.key}", e)
        }
    }

    fun dismissAll() {
        try {
            service?.cancelAllNotifications()
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Could not clear notifications", e)
        }
    }

    /** Opens the app behind [item], as tapping it in Android's shade would. */
    fun open(context: Context, item: NotificationItem): Boolean {
        val intent = item.contentIntent ?: return false
        val sent = sendPendingIntent(context, intent, null)
        if (sent && item.autoCancel) dismiss(item)
        return sent
    }

    fun perform(context: Context, action: NotificationAction): Boolean =
        sendPendingIntent(context, action.intent, null)

    /** Sends [text] through a notification's reply action (RemoteInput). */
    fun reply(context: Context, action: NotificationAction, text: String): Boolean {
        val input = action.remoteInput ?: return false
        val fill = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), fill, Bundle().apply { putCharSequence(input.resultKey, text) })
        return sendPendingIntent(context, action.intent, fill)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()

    fun previous() = controller?.transportControls?.skipToPrevious()

    /** Opens the app playing media. */
    fun openMediaApp(context: Context): Boolean {
        val c = controller ?: return false
        val intent = c.sessionActivity ?: return false
        return sendPendingIntent(context, intent, null)
    }

    private fun sendPendingIntent(context: Context, intent: PendingIntent, fill: Intent?): Boolean =
        try {
            intent.send(context, 0, fill, null, null, null, backgroundStartOptions())
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }

    /** MesOS Home is visible when the user taps, so it lends its right to start activities. */
    private fun backgroundStartOptions(): Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return options.toBundle()
    }

    private fun bindController(next: MediaController?) {
        if (controller?.sessionToken == next?.sessionToken) return
        controller?.unregisterCallback(controllerCallback)
        controller = next
        next?.registerCallback(controllerCallback, mainHandler)
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val metadata = c?.metadata
        if (c == null || metadata == null) {
            _media.value = null
            return
        }
        val state = c.playbackState
        _media.value = MediaSnapshot(
            packageName = c.packageName,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0L,
            positionAt = state?.lastPositionUpdateTime ?: 0L,
        )
    }

    private fun isShown(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // Media notifications appear as the media card instead.
        if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return false
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)
        return !title.isNullOrBlank() || !text.isNullOrBlank()
    }

    private fun toItem(sbn: StatusBarNotification): NotificationItem {
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val text = bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            user = sbn.user,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = text?.toString().orEmpty(),
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            contentIntent = n.contentIntent,
            actions = n.actions.orEmpty().mapNotNull { action ->
                val intent = action.actionIntent ?: return@mapNotNull null
                NotificationAction(
                    title = action.title?.toString().orEmpty(),
                    intent = intent,
                    remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput },
                )
            }.take(3),
        )
    }
}

/** Android binds this once the user grants MesOS notification access. */
class MesOSNotificationService : NotificationListenerService() {

    override fun onListenerConnected() = NotificationCenter.attach(this)

    override fun onListenerDisconnected() = NotificationCenter.detach()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = NotificationCenter.refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NotificationCenter.refresh()
}
