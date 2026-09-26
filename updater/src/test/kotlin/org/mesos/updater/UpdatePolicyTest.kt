package org.mesos.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePolicyTest {

    private val installed = InstalledRelease(packageName = "org.mesos.shell", versionCode = 1, channel = "developer")

    private fun manifest(
        schemaVersion: Int = 1,
        packageName: String = "org.mesos.shell",
        channel: String = "developer",
        versionCode: Int = 2,
        minimumSupportedVersion: Int = 1,
        packageUrl: String = "https://github.com/Meslanaa/Espor/releases/download/mesos-v0.1.1/mesos-shell-0.1.1.apk",
        sha256: String = "0".repeat(64),
        packageSize: Long = 10_000_000,
    ) = UpdateManifest(
        schemaVersion = schemaVersion,
        packageName = packageName,
        channel = channel,
        versionName = "0.1.1",
        versionCode = versionCode,
        minimumSupportedVersion = minimumSupportedVersion,
        releaseNotes = "",
        packageUrl = packageUrl,
        sha256 = sha256,
        packageSize = packageSize,
    )

    private fun rejected(reason: FailureReason) = UpdateDecision.Rejected(reason)

    @Test
    fun newerVersionIsAvailable() {
        assertEquals(UpdateDecision.Available, UpdatePolicy.evaluate(manifest(), installed))
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() {
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.evaluate(manifest(versionCode = 1), installed))
        assertEquals(UpdateDecision.UpToDate, UpdatePolicy.evaluate(manifest(versionCode = 0), installed))
    }

    @Test
    fun otherPackageIsRejected() {
        val devBuild = installed.copy(packageName = "org.mesos.shell.dev")
        assertEquals(rejected(FailureReason.WRONG_PACKAGE), UpdatePolicy.evaluate(manifest(), devBuild))
    }

    @Test
    fun otherChannelIsRejected() {
        assertEquals(rejected(FailureReason.WRONG_CHANNEL), UpdatePolicy.evaluate(manifest(channel = "stable"), installed))
    }

    @Test
    fun unknownSchemaIsRejected() {
        assertEquals(rejected(FailureReason.UNSUPPORTED_MANIFEST), UpdatePolicy.evaluate(manifest(schemaVersion = 2), installed))
    }

    @Test
    fun plainHttpIsRejected() {
        val http = manifest(packageUrl = "http://example.com/mesos.apk")
        assertEquals(rejected(FailureReason.INSECURE_URL), UpdatePolicy.evaluate(http, installed))
    }

    @Test
    fun malformedChecksumIsRejected() {
        assertEquals(rejected(FailureReason.INVALID_MANIFEST), UpdatePolicy.evaluate(manifest(sha256 = "abc"), installed))
    }

    @Test
    fun implausibleSizeIsRejected() {
        assertEquals(rejected(FailureReason.INVALID_MANIFEST), UpdatePolicy.evaluate(manifest(packageSize = 0), installed))
        val huge = manifest(packageSize = UpdatePolicy.MAX_PACKAGE_BYTES + 1)
        assertEquals(rejected(FailureReason.INVALID_MANIFEST), UpdatePolicy.evaluate(huge, installed))
    }

    @Test
    fun tooOldInstallationIsRejected() {
        val needsNewer = manifest(versionCode = 5, minimumSupportedVersion = 3)
        assertEquals(rejected(FailureReason.NOT_SUPPORTED_FROM_INSTALLED), UpdatePolicy.evaluate(needsNewer, installed))
    }
}
