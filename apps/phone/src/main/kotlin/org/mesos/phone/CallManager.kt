package org.mesos.phone

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.contacts.ContactSummary
import org.mesos.contacts.ContactsRepository

enum class CallStatus { RINGING, DIALING, CONNECTING, ACTIVE, HOLDING, DISCONNECTING, ENDED }

/** A call as MesOS shows it. */
data class CallInfo(
    val id: Int,
    val number: String,
    val status: CallStatus,
    /** elapsedRealtime when the call was answered, for the call timer. */
    val connectedAt: Long?,
    val contact: ContactSummary?,
    val canHold: Boolean,
) {
    val displayName: String get() = contact?.name?.takeIf { it.isNotBlank() } ?: number
}

data class AudioInfo(val muted: Boolean = false, val speaker: Boolean = false, val bluetooth: Boolean = false)

/**
 * Calls handed to MesOS by Android's Telecom (while MesOS is the default phone
 * app), shared by the in-call service, the call screen and notifications.
 */
object CallManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val calls = mutableListOf<Call>()
    private val connectedAt = mutableMapOf<Call, Long>()
    private val contacts = mutableMapOf<String, ContactSummary?>()
    internal var service: InCallService? = null

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val state: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioInfo())
    val audio: StateFlow<AudioInfo> = _audio.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE && call !in connectedAt) connectedAt[call] = SystemClock.elapsedRealtime()
            publish()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    internal fun add(context: Context, call: Call) {
        calls += call
        call.registerCallback(callback)
        if (stateOf(call) == Call.STATE_ACTIVE) connectedAt[call] = SystemClock.elapsedRealtime()
        publish()
        val number = numberOf(call)
        if (number.isNotEmpty() && number !in contacts) {
            scope.launch {
                contacts[number] = withContext(Dispatchers.IO) { ContactsRepository.lookupByNumber(context, number) }
                publish()
            }
        }
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls -= call
        connectedAt -= call
        publish()
    }

    internal fun audioChanged(state: CallAudioState?) {
        state ?: return
        _audio.value = AudioInfo(
            muted = state.isMuted,
            speaker = state.route == CallAudioState.ROUTE_SPEAKER,
            bluetooth = state.route == CallAudioState.ROUTE_BLUETOOTH,
        )
    }

    fun answer(id: Int) {
        find(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun decline(id: Int) {
        find(id)?.reject(false, null)
    }

    fun hangUp(id: Int) {
        val call = find(id) ?: return
        if (stateOf(call) == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun hold(id: Int, hold: Boolean) {
        val call = find(id) ?: return
        if (hold) call.hold() else call.unhold()
    }

    fun tone(id: Int, digit: Char) {
        val call = find(id) ?: return
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }

    private fun find(id: Int): Call? = calls.firstOrNull { System.identityHashCode(it) == id }

    private fun publish() {
        _calls.value = calls.map { call ->
            val number = numberOf(call)
            CallInfo(
                id = System.identityHashCode(call),
                number = number,
                status = statusOf(stateOf(call)),
                connectedAt = connectedAt[call],
                contact = contacts[number],
                canHold = call.details.can(Call.Details.CAPABILITY_HOLD),
            )
        }
    }

    private fun numberOf(call: Call): String {
        val handle: Uri? = call.details.handle
        return handle?.schemeSpecificPart.orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

    private fun statusOf(state: Int): CallStatus = when (state) {
        Call.STATE_RINGING -> CallStatus.RINGING
        Call.STATE_DIALING -> CallStatus.DIALING
        Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_PULLING_CALL -> CallStatus.CONNECTING
        Call.STATE_ACTIVE -> CallStatus.ACTIVE
        Call.STATE_HOLDING -> CallStatus.HOLDING
        Call.STATE_DISCONNECTING -> CallStatus.DISCONNECTING
        else -> CallStatus.ENDED
    }
}
