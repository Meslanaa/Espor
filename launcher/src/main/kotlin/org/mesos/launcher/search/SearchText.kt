package org.mesos.launcher.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text matching for MesOS search. Pure Kotlin, unit tested.
 *
 * Matching ignores case and accents, and treats Turkish letters as their Latin
 * base letters, so "istanbul" finds "İstanbul" and "cagri" finds "Çağrı".
 */
object SearchText {

    private val marks = Regex("\\p{Mn}+")
    private val phoneNumber = Regex("^[+0][0-9 ()-]{9,}$")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('ı', 'i')
            .trim()

    /**
     * Score of [candidate] for [query] (both raw): 0 when it does not match, higher is
     * better. Whole-text prefix > word prefix > anywhere.
     */
    fun score(query: String, candidate: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val c = normalize(candidate)
        return when {
            c == q -> 400
            c.startsWith(q) -> 300
            c.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 200
            c.contains(q) -> 100
            else -> 0
        }
    }

    /**
     * An arithmetic expression the calculator can evaluate, or null.
     * Accepts "12,5 x 4" (comma decimals, x for times) and needs an operator.
     */
    fun expression(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.none(Char::isDigit)) return null
        if (trimmed.none { it in "+-−*×x/÷%^" }) return null
        if (!trimmed.all { it.isDigit() || it in " .,()+-−*×x/÷%" }) return null
        // "0555 123 45 67" and "+90 (555) 123-4567" are phone numbers, not sums.
        if (phoneNumber.matches(trimmed)) return null
        // A lone negative number or percentage is not a calculation.
        val core = trimmed.trimStart('-', '−', '+')
        if (core.none { it in "+-−*×x/÷" } && !core.endsWith("%")) return null
        val decimal = if (trimmed.contains(',') && !trimmed.contains('.')) trimmed.replace(',', '.') else trimmed.replace(",", "")
        return decimal.replace('x', '×')
    }
}
