package org.mesos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MesOSReleaseTest {

    private fun release(versionName: String = "0.1", label: String = "Developer Preview") =
        MesOSRelease(
            versionName = versionName,
            versionCode = 1,
            label = label,
            channel = ReleaseChannel.DEVELOPER,
            buildNumber = "local",
        )

    @Test
    fun displayVersionAppendsLabel() {
        assertEquals("0.1 Developer Preview", release().displayVersion)
        assertEquals("MesOS 0.1 Developer Preview", release().displayName)
    }

    @Test
    fun displayVersionOmitsBlankLabel() {
        assertEquals("1.0", release(versionName = "1.0", label = "").displayVersion)
    }

    @Test
    fun channelIdsRoundTrip() {
        ReleaseChannel.entries.forEach { assertEquals(it, ReleaseChannel.fromId(it.id)) }
    }

    @Test
    fun unknownChannelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReleaseChannel.fromId("nightly") }
    }

    @Test
    fun currentReleaseIsBuiltFromMesOSProperties() {
        val current = MesOSRelease.current
        assertTrue(current.versionName.isNotBlank())
        assertTrue(current.versionCode > 0)
        assertTrue(current.buildNumber.isNotBlank())
    }
}
