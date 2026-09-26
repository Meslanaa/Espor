package org.mesos.updater

/** Why an update check or installation did not succeed. */
enum class FailureReason {
    NETWORK,
    NO_RELEASE,
    INVALID_MANIFEST,
    UNSUPPORTED_MANIFEST,
    WRONG_PACKAGE,
    WRONG_CHANNEL,
    INSECURE_URL,
    NOT_SUPPORTED_FROM_INSTALLED,
    TOO_LARGE,
    CHECKSUM_MISMATCH,
    PACKAGE_MISMATCH,
    SIGNATURE_MISMATCH,
    INSTALL_FAILED,
    INSTALL_CANCELLED,
    STORAGE,
}

class UpdateException(val reason: FailureReason, val detail: String? = null) :
    Exception("$reason${detail?.let { ": $it" } ?: ""}")

/** The MesOS build currently installed on the device. */
data class InstalledRelease(
    val packageName: String,
    val versionCode: Int,
    val channel: String,
)

sealed interface UpdateDecision {
    data object Available : UpdateDecision
    data object UpToDate : UpdateDecision
    data class Rejected(val reason: FailureReason) : UpdateDecision
}

/** Decides whether a published manifest may be offered to this device. Never trusts the manifest blindly. */
object UpdatePolicy {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_PACKAGE_BYTES = 512L * 1024 * 1024

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    fun evaluate(manifest: UpdateManifest, installed: InstalledRelease): UpdateDecision {
        val rejection = when {
            manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION -> FailureReason.UNSUPPORTED_MANIFEST
            manifest.packageName != installed.packageName -> FailureReason.WRONG_PACKAGE
            manifest.channel != installed.channel -> FailureReason.WRONG_CHANNEL
            !manifest.packageUrl.startsWith("https://") -> FailureReason.INSECURE_URL
            !SHA256_HEX.matches(manifest.sha256) -> FailureReason.INVALID_MANIFEST
            manifest.packageSize <= 0 || manifest.packageSize > MAX_PACKAGE_BYTES -> FailureReason.INVALID_MANIFEST
            else -> null
        }
        if (rejection != null) return UpdateDecision.Rejected(rejection)
        if (manifest.versionCode <= installed.versionCode) return UpdateDecision.UpToDate
        if (installed.versionCode < manifest.minimumSupportedVersion) {
            return UpdateDecision.Rejected(FailureReason.NOT_SUPPORTED_FROM_INSTALLED)
        }
        return UpdateDecision.Available
    }
}
