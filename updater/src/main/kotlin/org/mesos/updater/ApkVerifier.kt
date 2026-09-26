package org.mesos.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.mesos.core.log.MesOSLog
import java.io.File
import java.security.MessageDigest

/** Checks a downloaded APK against the manifest and the installed MesOS before installation. */
internal object ApkVerifier {

    fun verify(context: Context, apk: File, manifest: UpdateManifest) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk)
            ?: throw UpdateException(FailureReason.PACKAGE_MISMATCH, "not a readable APK")

        if (archive.packageName != context.packageName) {
            throw UpdateException(FailureReason.PACKAGE_MISMATCH, "package ${archive.packageName}")
        }
        val archiveVersion = versionCodeOf(archive)
        if (archiveVersion != manifest.versionCode.toLong()) {
            throw UpdateException(FailureReason.PACKAGE_MISMATCH, "versionCode $archiveVersion != ${manifest.versionCode}")
        }

        val installedSigners = signerDigests(installedInfo(pm, context.packageName))
        val archiveSigners = signerDigests(archive)
        if (archiveSigners.isEmpty()) {
            // Some Android versions do not report signers for archives. Android itself still
            // refuses an update signed with a different key, so this only loses the early check.
            MesOSLog.w(MesOSLog.UPDATER, "APK signers unavailable before install; relying on Android's signature check")
            return
        }
        if (archiveSigners != installedSigners) {
            throw UpdateException(FailureReason.SIGNATURE_MISMATCH)
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        pm.getPackageArchiveInfo(apk.absolutePath, signatureFlag())

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo =
        pm.getPackageInfo(packageName, signatureFlag())

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
