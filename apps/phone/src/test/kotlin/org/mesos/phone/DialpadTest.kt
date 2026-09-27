package org.mesos.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialpadTest {

    @Test
    fun lettersMapToKeys() {
        assertEquals('2', Dialpad.digitFor('a'))
        assertEquals('7', Dialpad.digitFor('S'))
        assertEquals('9', Dialpad.digitFor('z'))
        assertEquals('7', Dialpad.digitFor('ş'))
        assertEquals('4', Dialpad.digitFor('ı'))
        assertEquals('4', Dialpad.digitFor('İ'))
        assertEquals('6', Dialpad.digitFor('ö'))
        assertEquals('2', Dialpad.digitFor('ç'))
        assertEquals('5', Dialpad.digitFor('5'))
    }

    @Test
    fun namesMatchByWordStart() {
        assertTrue(Dialpad.matchesName("634", "Mehmet Aslan")) // "Meh"
        assertTrue(Dialpad.matchesName("2752", "Mehmet Aslan")) // "Asla"
        assertTrue(Dialpad.matchesName("7244", "Şahin Çelik")) // "Şahi"
        assertTrue(Dialpad.matchesName("2354", "Çelik"))
        assertFalse(Dialpad.matchesName("999", "Ayşe"))
        assertFalse(Dialpad.matchesName("", "Ayşe"))
        assertFalse(Dialpad.matchesName("2*", "Ayşe"))
    }

    @Test
    fun numbersMatchIgnoringFormatting() {
        assertTrue(Dialpad.matchesNumber("5551", "+90 (555) 123 45 67"))
        assertTrue(Dialpad.matchesNumber("+90555", "+90 555 123"))
        assertFalse(Dialpad.matchesNumber("444", "+90 555 123"))
        assertEquals("+905551112233", Dialpad.sanitize("+90 (555) 111-22-33"))
        assertEquals("*21#", Dialpad.sanitize("*21#"))
    }
}
