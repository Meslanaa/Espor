package org.mesos.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanParserTest {

    @Test
    fun links() {
        val secure = ScanParser.parse(" https://mesos.example/a?b=c ") as ScanContent.Url
        assertEquals("https://mesos.example/a?b=c", secure.url)
        assertTrue(secure.secure)
        val plain = ScanParser.parse("HTTP://example.com") as ScanContent.Url
        assertFalse(plain.secure)
        // Text that merely starts like a link is not opened as one.
        assertTrue(ScanParser.parse("http://a b") is ScanContent.Text)
        assertTrue(ScanParser.parse("javascript:alert(1)") is ScanContent.Text)
    }

    @Test
    fun wifi() {
        val wifi = ScanParser.parse("WIFI:T:WPA;S:Home\\;Net;P:pa\\:ss\\\\word;H:true;;") as ScanContent.Wifi
        assertEquals("Home;Net", wifi.ssid)
        assertEquals("pa:ss\\word", wifi.password)
        assertEquals(WifiSecurity.WPA, wifi.security)
        assertTrue(wifi.hidden)

        val open = ScanParser.parse("WIFI:S:Cafe;T:nopass;P:;;") as ScanContent.Wifi
        assertEquals(WifiSecurity.OPEN, open.security)
        assertNull(open.password)

        val sae = ScanParser.parse("WIFI:S:Lab;T:SAE;P:secret;;") as ScanContent.Wifi
        assertEquals(WifiSecurity.WPA3, sae.security)

        assertTrue(ScanParser.parse("WIFI:T:WPA;P:x;;") is ScanContent.Text)
    }

    @Test
    fun phoneMailAndSms() {
        assertEquals("+905551112233", (ScanParser.parse("tel:+905551112233") as ScanContent.Phone).number)

        val mail = ScanParser.parse("mailto:hi@mesos.dev?subject=Hello%20there&body=Line") as ScanContent.Email
        assertEquals("hi@mesos.dev", mail.address)
        assertEquals("Hello there", mail.subject)
        assertEquals("Line", mail.body)

        val matmsg = ScanParser.parse("MATMSG:TO:a@b.c;SUB:Hi;BODY:Text;;") as ScanContent.Email
        assertEquals("a@b.c", matmsg.address)
        assertEquals("Hi", matmsg.subject)

        val sms = ScanParser.parse("SMSTO:+90555:See you: soon") as ScanContent.Sms
        assertEquals("+90555", sms.number)
        assertEquals("See you: soon", sms.body)
        val sms2 = ScanParser.parse("sms:12345?body=Hi") as ScanContent.Sms
        assertEquals("12345", sms2.number)
        assertEquals("Hi", sms2.body)
    }

    @Test
    fun geo() {
        val geo = ScanParser.parse("geo:41.0082,28.9784?q=41.0082,28.9784(Istanbul)") as ScanContent.Geo
        assertEquals(41.0082, geo.latitude, 1e-9)
        assertEquals(28.9784, geo.longitude, 1e-9)
        assertEquals("Istanbul", geo.label)
        assertTrue(ScanParser.parse("geo:200,10") is ScanContent.Text)
    }

    @Test
    fun contacts() {
        val mecard = ScanParser.parse("MECARD:N:Doe,John;TEL:+1 555;TEL:+1 556;EMAIL:j@d.com;ORG:MesOS;;") as ScanContent.Contact
        assertEquals("John Doe", mecard.name)
        assertEquals(listOf("+1 555", "+1 556"), mecard.phones)
        assertEquals(listOf("j@d.com"), mecard.emails)
        assertEquals("MesOS", mecard.organization)

        val vcard = ScanParser.parse(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Yılmaz;Ayşe;;;\r\nFN:Ayşe Yılmaz\r\nTEL;TYPE=CELL:+90 555 000\r\n" +
                "EMAIL;TYPE=INTERNET:ayse@example.com\r\nEND:VCARD",
        ) as ScanContent.Contact
        assertEquals("Ayşe Yılmaz", vcard.name)
        assertEquals(listOf("+90 555 000"), vcard.phones)
        assertEquals(listOf("ayse@example.com"), vcard.emails)
    }

    @Test
    fun plainText() {
        val text = ScanParser.parse("4006381333931") as ScanContent.Text
        assertEquals("4006381333931", text.raw)
    }
}
