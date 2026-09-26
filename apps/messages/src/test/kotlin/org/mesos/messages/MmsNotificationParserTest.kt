package org.mesos.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsNotificationParserTest {

    private fun text(s: String) = s.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF } + 0
    private fun pdu(vararg parts: List<Int>) = parts.flatMap { it }.map { it.toByte() }.toByteArray()

    private val from = text("+905551112233/TYPE=PLMN")

    @Test
    fun parsesATypicalNotification() {
        val bytes = pdu(
            listOf(0x8C, 0x82),
            listOf(0x98) + text("T123"),
            listOf(0x8D, 0x92),
            listOf(0x89, 1 + from.size, 0x80) + from,
            listOf(0x96) + text("Hello"),
            listOf(0x86, 0x81), // Delivery report: skipped.
            listOf(0x8A, 0x80),
            listOf(0x8E, 0x02, 0x10, 0x00),
            listOf(0x88, 0x05, 0x81, 0x03, 0x01, 0x51, 0x80),
            listOf(0x83) + text("http://mmsc.example/abc"),
        )
        val n = MmsNotificationParser.parse(bytes)!!
        assertEquals("T123", n.transactionId)
        assertEquals("http://mmsc.example/abc", n.contentLocation)
        assertEquals("+905551112233", n.from)
        assertEquals("Hello", n.subject)
        assertEquals(4096L, n.messageSize)
        assertEquals(86_400L, n.expiry)
        assertTrue(n.expiryRelative)
        assertEquals(0, n.messageClass)
        assertEquals(0x12, n.version)
    }

    @Test
    fun readsEncodedSubjectsAndLongLengths() {
        val subject = text("Merhaba dünya")
        val location = text("http://mmsc.example/" + "x".repeat(40))
        val bytes = pdu(
            listOf(0x8C, 0x82),
            listOf(0x98) + text("T9"),
            listOf(0x96, 1 + subject.size, 0xEA) + subject,
            listOf(0x83, 0x7F) + location, // Quoted text.
        )
        val n = MmsNotificationParser.parse(bytes)!!
        assertEquals("Merhaba dünya", n.subject)
        assertEquals("http://mmsc.example/" + "x".repeat(40), n.contentLocation)
        assertNull(n.from)
    }

    @Test
    fun rejectsOtherMessagesAndGarbage() {
        val retrieveConf = pdu(listOf(0x8C, 0x84), listOf(0x98) + text("T1"), listOf(0x83) + text("http://a"))
        assertNull(MmsNotificationParser.parse(retrieveConf))
        assertNull(MmsNotificationParser.parse(byteArrayOf(0x8C.toByte())))
        assertNull(MmsNotificationParser.parse(byteArrayOf(0x41, 0x42)))
        assertNull(MmsNotificationParser.parse(pdu(listOf(0x8C, 0x82), listOf(0x98) + text("T1"))))
    }
}
