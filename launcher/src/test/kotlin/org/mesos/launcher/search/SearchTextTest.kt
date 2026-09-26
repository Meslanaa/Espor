package org.mesos.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun normalizesTurkishAndAccents() {
        assertEquals("istanbul", SearchText.normalize("İstanbul"))
        assertEquals("cagri", SearchText.normalize("Çağrı"))
        assertEquals("sifre", SearchText.normalize("ŞİFRE"))
        assertEquals("ozgur", SearchText.normalize("Özgür"))
        assertEquals("cafe", SearchText.normalize("Café"))
    }

    @Test
    fun ranksPrefixAboveWordAboveContains() {
        val prefix = SearchText.score("ka", "Kamera")
        val word = SearchText.score("ka", "Google Kalender")
        val inside = SearchText.score("me", "Kamera")
        assertTrue(prefix > word)
        assertTrue(word > inside)
        assertTrue(inside > 0)
        assertEquals(0, SearchText.score("xyz", "Kamera"))
        assertEquals(0, SearchText.score("  ", "Kamera"))
    }

    @Test
    fun matchesAcrossTurkishLetters() {
        assertTrue(SearchText.score("fotograf", "Fotoğraflar") > 0)
        assertTrue(SearchText.score("I", "ısı") > 0)
        assertTrue(SearchText.score("i", "Instagram") > 0)
    }

    @Test
    fun recognisesExpressions() {
        assertEquals("12×4", SearchText.expression("12x4"))
        assertEquals("12.5 + 3", SearchText.expression("12,5 + 3"))
        assertEquals("50%", SearchText.expression("50%"))
        assertEquals("(2+3)*4", SearchText.expression("(2+3)*4"))
    }

    @Test
    fun rejectsNonExpressions() {
        assertNull(SearchText.expression("kamera"))
        assertNull(SearchText.expression("123"))
        assertNull(SearchText.expression("-5"))
        assertNull(SearchText.expression("+90 555 123"))
        assertNull(SearchText.expression("x"))
        assertNull(SearchText.expression("0555-123-45-67"))
        assertNull(SearchText.expression("+90 (555) 123-4567"))
    }
}
