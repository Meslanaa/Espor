package org.mesos.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestParserTest {

    private val sha = "a".repeat(64)

    private fun json(extra: String = "") = """
        {
          "schemaVersion": 1,
          "packageName": "org.mesos.shell",
          "channel": "developer",
          "versionName": "0.1.1",
          "versionCode": 2,
          "minimumSupportedVersion": 1,
          "releaseNotes": "Update system verified.",
          "packageUrl": "https://github.com/Meslanaa/Espor/releases/download/mesos-v0.1.1/mesos-shell-0.1.1.apk",
          "sha256": "${sha.uppercase()}",
          "packageSize": 1234$extra
        }
    """.trimIndent()

    @Test
    fun parsesValidManifest() {
        val manifest = UpdateManifestParser.parse(json())
        assertEquals(1, manifest.schemaVersion)
        assertEquals("org.mesos.shell", manifest.packageName)
        assertEquals("developer", manifest.channel)
        assertEquals("0.1.1", manifest.versionName)
        assertEquals(2, manifest.versionCode)
        assertEquals(1, manifest.minimumSupportedVersion)
        assertEquals("Update system verified.", manifest.releaseNotes)
        assertEquals(sha, manifest.sha256) // normalised to lowercase
        assertEquals(1234L, manifest.packageSize)
    }

    @Test
    fun ignoresUnknownFields() {
        UpdateManifestParser.parse(json(extra = ", \"futureField\": true"))
    }

    @Test
    fun rejectsMalformedJson() {
        val error = assertThrows(UpdateException::class.java) { UpdateManifestParser.parse("{not json") }
        assertEquals(FailureReason.INVALID_MANIFEST, error.reason)
    }

    @Test
    fun rejectsMissingField() {
        val withoutSha = json().replace(Regex("\"sha256\": \"[^\"]*\",\\s*"), "")
        val error = assertThrows(UpdateException::class.java) { UpdateManifestParser.parse(withoutSha) }
        assertEquals(FailureReason.INVALID_MANIFEST, error.reason)
    }
}
