package org.mesos.phone

import java.text.Normalizer

/** The phone keypad: digits, their letters, and T9 matching of contact names. Pure Kotlin. */
object Dialpad {

    val keypad: List<Pair<Char, String>> = listOf(
        '1' to "",
        '2' to "ABC",
        '3' to "DEF",
        '4' to "GHI",
        '5' to "JKL",
        '6' to "MNO",
        '7' to "PQRS",
        '8' to "TUV",
        '9' to "WXYZ",
        '*' to "",
        '0' to "+",
        '#' to "",
    )

    private val digitOf: Map<Char, Char> = buildMap {
        keypad.forEach { (digit, letters) -> letters.forEach { put(it.lowercaseChar(), digit) } }
    }

    /** The key a letter is on; Turkish letters sit on their Latin base letter's key. */
    fun digitFor(c: Char): Char? {
        if (c.isDigit()) return c
        val base = when (c.lowercaseChar()) {
            'ı' -> 'i'
            else -> Normalizer.normalize(c.lowercaseChar().toString(), Normalizer.Form.NFD).first()
        }
        return digitOf[base]
    }

    /** Whether typing [digits] on the keypad spells the start of a word in [name]. */
    fun matchesName(digits: String, name: String): Boolean {
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return false
        val words = name.split(' ', '-', '.').filter { it.isNotEmpty() }
        return words.any { word -> startsWith(word, digits) } || startsWith(name.filter { it.isLetterOrDigit() }, digits)
    }

    private fun startsWith(word: String, digits: String): Boolean {
        if (word.length < digits.length) return false
        return digits.indices.all { i -> digitFor(word[i]) == digits[i] }
    }

    /** Whether [number] contains the typed [digits] (ignoring spaces, dashes and brackets). */
    fun matchesNumber(digits: String, number: String): Boolean {
        if (digits.isEmpty()) return false
        val plain = number.filter { it.isDigit() || it == '+' }
        return plain.contains(digits.filter { it.isDigit() || it == '+' })
    }

    /** Characters that may be dialled. */
    fun sanitize(input: String): String = input.filter { it.isDigit() || it in "+*#,;" }
}
