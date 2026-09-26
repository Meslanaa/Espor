package org.mesos.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.mesos.core.log.MesOSLog
import java.io.File

/**
 * Hands a verified APK to Android's [PackageInstaller]. Android shows its own
 * confirmation dialog; MesOS never installs silently.
 */
internal object ApkInstaller {

    const val ACTION_INSTALL_STATUS = "org.mesos.updater.action.INSTALL_STATUS"

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("mesos-update.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            // Mutable so Android can attach the status extras; explicit component keeps it private.
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pendingIntent.intentSender)
        }
        MesOSLog.i(MesOSLog.UPDATER, "Install session $sessionId committed")
    }
}

/** Receives [PackageInstaller] results and forwards them to [UpdateController]. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        MesOSLog.i(MesOSLog.UPDATER, "Install status $status ${message.orEmpty()}")

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmationIntent(intent)
            if (confirm == null) {
                UpdateController.onInstallResult(PackageInstaller.STATUS_FAILURE, "missing confirmation intent")
                return
            }
            // Android's own confirmation UI; MesOS is in the foreground at this point.
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        UpdateController.onInstallResult(status, message)
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
