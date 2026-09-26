package org.mesos.updater

/** What the MesOS Update screen shows. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    data class Verifying(val manifest: UpdateManifest) : UpdateState
    data class NeedsInstallPermission(val manifest: UpdateManifest) : UpdateState
    data class AwaitingConfirmation(val manifest: UpdateManifest) : UpdateState
    data class Installed(val manifest: UpdateManifest) : UpdateState
    data class Failed(val reason: FailureReason, val detail: String?) : UpdateState

    val isBusy: Boolean
        get() = this is Checking || this is Downloading || this is Verifying
}
