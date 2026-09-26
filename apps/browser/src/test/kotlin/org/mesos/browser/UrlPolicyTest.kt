package org.mesos.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    private val google = SearchEngine.GOOGLE

    @Test
    fun linksStayLinks() {
        assertEquals("https://mesos.dev/a", UrlPolicy.resolve("https://mesos.dev/a", google))
        assertEquals("http://example.com", UrlPolicy.resolve(" http://example.com ", google))
        assertEquals("https://example.com", UrlPolicy.resolve("example.com", google))
        assertEquals("https://sub.example.com.tr/path?q=1", UrlPolicy.resolve("sub.example.com.tr/path?q=1", google))
        assertEquals("https://example.com:8080/x", UrlPolicy.resolve("example.com:8080/x", google))
        assertEquals("https://192.168.1.1", UrlPolicy.resolve("192.168.1.1", google))
        assertEquals("https://localhost:8080", UrlPolicy.resolve("localhost:8080", google))
        assertEquals("https://türkiye.gov.tr", UrlPolicy.resolve("türkiye.gov.tr", google))
    }

    @Test
    fun everythingElseIsASearch() {
        assertEquals("https://www.google.com/search?q=hava+durumu", UrlPolicy.resolve("hava durumu", google))
        assertEquals("https://duckduckgo.com/?q=mesos", UrlPolicy.resolve("mesos", SearchEngine.DUCKDUCKGO))
        assertEquals("https://www.google.com/search?q=12%3A30", UrlPolicy.resolve("12:30", google))
        assertEquals("https://www.google.com/search?q=a%26b", UrlPolicy.resolve("a&b", google))
        assertEquals("", UrlPolicy.resolve("   ", google))
        // A typed javascript: URL is searched for, never run.
        assertTrue(UrlPolicy.resolve("javascript:alert(1)", google).startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun linkClassification() {
        assertEquals(LinkAction.LOAD, UrlPolicy.classify("https://a.b"))
        assertEquals(LinkAction.LOAD, UrlPolicy.classify("HTTP://a.b"))
        assertEquals(LinkAction.EXTERNAL, UrlPolicy.classify("tel:+905551112233"))
        assertEquals(LinkAction.EXTERNAL, UrlPolicy.classify("mailto:a@b.c"))
        assertEquals(LinkAction.EXTERNAL, UrlPolicy.classify("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(LinkAction.BLOCK, UrlPolicy.classify("javascript:alert(1)"))
        assertEquals(LinkAction.BLOCK, UrlPolicy.classify("file:///sdcard/secret.txt"))
        assertEquals(LinkAction.BLOCK, UrlPolicy.classify("content://com.android.contacts/contacts"))
        assertEquals(LinkAction.BLOCK, UrlPolicy.classify("data:text/html,hi"))
        assertEquals(LinkAction.BLOCK, UrlPolicy.classify("no scheme at all"))
    }

    @Test
    fun hostsAndFallbacks() {
        assertEquals("example.com", UrlPolicy.displayHost("https://www.example.com/path"))
        assertEquals("not a url", UrlPolicy.displayHost("not a url"))
        assertTrue(UrlPolicy.isSecure("https://a.b"))
        assertFalse(UrlPolicy.isSecure("http://a.b"))
        assertEquals("https://play.google.com/x", UrlPolicy.safeFallback("https://play.google.com/x"))
        assertNull(UrlPolicy.safeFallback("javascript:alert(1)"))
        assertNull(UrlPolicy.safeFallback(null))
    }
}
