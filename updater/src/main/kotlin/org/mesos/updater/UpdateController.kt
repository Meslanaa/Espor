package org.mesos.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSRelease
import org.mesos.core.log.MesOSLog
import java.io.File
import java.io.IOException

/**
 * MesOS component updater (prototype, see docs/UPDATES.md).
 *
 * check → download → SHA-256 → APK/signature check → Android installer (user confirms).
 * This updates the MesOS Shell APK only; it is not an operating-system OTA.
 */
object UpdateController {

    private const val MAX_MANIFEST_BYTES = 64 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = UpdateClient()
    private lateinit var appContext: Context
    private lateinit var preferences: UpdatePreferences

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _lastCheckedAt = MutableStateFlow<Long?>(null)
    val lastCheckedAt: StateFlow<Long?> = _lastCheckedAt.asStateFlow()

    /** Version name MesOS was updated from, if this run is the first after an update. */
    var updatedFrom: String? = null
        private set

    /** Release notes of the installed version, when it was installed by this updater. */
    var installedReleaseNotes: String? = null
        private set

    val manifestUrl: String = BuildConfig.MESOS_UPDATE_MANIFEST_URL

    @Volatile
    private var pendingManifest: UpdateManifest? = null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        preferences = UpdatePreferences(appContext)
        _lastCheckedAt.value = preferences.lastCheckedAt

        val current = MesOSRelease.current
        installedReleaseNotes = preferences.pendingNotesFor(current.versionCode)
        val lastSeenCode = preferences.lastSeenVersionCode
        if (lastSeenCode in 1 until current.versionCode) {
            updatedFrom = preferences.lastSeenVersionName
            MesOSLog.i(MesOSLog.UPDATER, "MesOS updated from $updatedFrom to ${current.versionName}")
        }
        preferences.recordSeen(current.versionCode, current.versionName)
    }

    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Android's screen where the user allows MesOS to install its updates. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun check() {
        if (_state.value.isBusy) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val manifest = withContext(Dispatchers.IO) {
                    UpdateManifestParser.parse(client.fetchText(manifestUrl, MAX_MANIFEST_BYTES))
                }
                val now = System.currentTimeMillis()
                preferences.lastCheckedAt = now
                _lastCheckedAt.value = now
                when (val decision = UpdatePolicy.evaluate(manifest, installedRelease())) {
                    UpdateDecision.Available -> UpdateState.Available(manifest)
                    UpdateDecision.UpToDate -> UpdateState.UpToDate(now)
                    is UpdateDecision.Rejected -> UpdateState.Failed(decision.reason, null)
                }
            } catch (e: UpdateException) {
                UpdateState.Failed(e.reason, e.detail)
            } catch (e: IOException) {
                UpdateState.Failed(FailureReason.NETWORK, e.message)
            }
            MesOSLog.i(MesOSLog.UPDATER, "Update check finished: ${_state.value}")
        }
    }

    fun downloadAndInstall() {
        val manifest = when (val current = _state.value) {
            is UpdateState.Available -> current.manifest
            is UpdateState.NeedsInstallPermission -> current.manifest
            else -> return
        }
        if (!canInstallPackages(appContext)) {
            _state.value = UpdateState.NeedsInstallPermission(manifest)
            return
        }
        _state.value = UpdateState.Downloading(manifest, 0f)
        scope.launch {
            val apk = File(File(appContext.cacheDir, "updates"), "mesos-update.apk")
            try {
                withContext(Dispatchers.IO) {
                    apk.parentFile?.mkdirs()
                    apk.delete()
                    var lastPercent = -1
                    val sha256 = client.download(manifest.packageUrl, apk, manifest.packageSize) { progress ->
                        val percent = (progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateState.Downloading(manifest, progress)
                        }
                    }
                    _state.value = UpdateState.Verifying(manifest)
                    if (sha256 != manifest.sha256) throw UpdateException(FailureReason.CHECKSUM_MISMATCH)
                    ApkVerifier.verify(appContext, apk, manifest)
                    MesOSLog.i(MesOSLog.UPDATER, "Update ${manifest.versionName} verified (sha256 $sha256)")

                    pendingManifest = manifest
                    preferences.savePendingNotes(manifest.versionCode, manifest.releaseNotes)
                    _state.value = UpdateState.AwaitingConfirmation(manifest)
                    ApkInstaller.install(appContext, apk)
                }
            } catch (e: UpdateException) {
                MesOSLog.w(MesOSLog.UPDATER, "Update failed", e)
                _state.value = UpdateState.Failed(e.reason, e.detail)
            } catch (e: IOException) {
                // Network and disk errors are classified inside UpdateClient; what is left
                // comes from handing the APK to the installer.
                MesOSLog.w(MesOSLog.UPDATER, "Update failed", e)
                _state.value = UpdateState.Failed(FailureReason.INSTALL_FAILED, e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                MesOSLog.w(MesOSLog.UPDATER, "Update failed", e)
                _state.value = UpdateState.Failed(FailureReason.INSTALL_FAILED, e.message)
            } finally {
                // The install session holds its own copy of the APK.
                withContext(Dispatchers.IO) { apk.delete() }
            }
        }
    }

    /** Called by [InstallResultReceiver] with a final [PackageInstaller] status. */
    internal fun onInstallResult(status: Int, message: String?) {
        val manifest = pendingManifest
        _state.value = when {
            status == PackageInstaller.STATUS_SUCCESS && manifest != null -> UpdateState.Installed(manifest)
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed(FailureReason.INSTALL_CANCELLED, message)
            else -> UpdateState.Failed(FailureReason.INSTALL_FAILED, message)
        }
        pendingManifest = null
    }

    /**
     * Background check (see [UpdateCheckJob]): posts a notification once per new
     * version. Never downloads or installs anything by itself.
     */
    internal suspend fun backgroundCheck(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val prefs = UpdatePreferences(app)
        val manifest = try {
            UpdateManifestParser.parse(client.fetchText(manifestUrl, MAX_MANIFEST_BYTES))
        } catch (e: UpdateException) {
            MesOSLog.w(MesOSLog.UPDATER, "Background update check failed", e)
            return@withContext
        } catch (e: IOException) {
            MesOSLog.w(MesOSLog.UPDATER, "Background update check failed", e)
            return@withContext
        }
        val now = System.currentTimeMillis()
        prefs.lastCheckedAt = now
        _lastCheckedAt.value = now
        val decision = UpdatePolicy.evaluate(manifest, installedRelease(app))
        MesOSLog.i(MesOSLog.UPDATER, "Background update check: $decision")
        if (decision == UpdateDecision.Available && manifest.versionCode > prefs.lastNotifiedVersionCode) {
            UpdateNotifier.notifyAvailable(app, manifest.versionName)
            prefs.lastNotifiedVersionCode = manifest.versionCode
        }
    }

    private fun installedRelease(): InstalledRelease = installedRelease(appContext)

    private fun installedRelease(context: Context): InstalledRelease {
        val current = MesOSRelease.current
        return InstalledRelease(
            packageName = context.packageName,
            versionCode = current.versionCode,
            channel = current.channel.id,
        )
    }
}
