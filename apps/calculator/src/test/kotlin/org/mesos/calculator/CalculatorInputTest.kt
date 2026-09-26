package org.mesos.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorInputTest {

    private fun type(vararg keys: String): String = keys.fold("") { expr, key ->
        when (key) {
            "." -> CalculatorInput.decimal(expr)
            "%" -> CalculatorInput.percent(expr)
            "()" -> CalculatorInput.parenthesis(expr)
            "<" -> CalculatorInput.backspace(expr)
            "+", "−", "×", "÷" -> CalculatorInput.operator(expr, key[0])
            else -> CalculatorInput.digit(expr, key[0])
        }
    }

    @Test
    fun digitsAndLeadingZero() {
        assertEquals("12", type("1", "2"))
        assertEquals("7", type("0", "7"))
        assertEquals("0.7", type("0", ".", "7"))
        assertEquals("3+7", type("3", "+", "0", "7"))
    }

    @Test
    fun decimalPointOncePerNumber() {
        assertEquals("1.5", type("1", ".", "5", "."))
        assertEquals("0.", type("."))
        assertEquals("2+0.", type("2", "+", "."))
    }

    @Test
    fun operatorsReplaceEachOther() {
        assertEquals("5×", type("5", "+", "×"))
        assertEquals("5×−", type("5", "×", "−"))
        assertEquals("5+", type("5", "×", "−", "+"))
        assertEquals("−", type("−"))
        assertEquals("", type("×"))
        assertEquals("−", type("−", "+"))
    }

    @Test
    fun smartParenthesis() {
        assertEquals("(", type("()"))
        assertEquals("(2+3)", type("()", "2", "+", "3", "()"))
        assertEquals("4×(", type("4", "()"))
        assertEquals("(2)×5", type("()", "2", "()", "5"))
    }

    @Test
    fun percentOnlyAfterValue() {
        assertEquals("50%", type("5", "0", "%"))
        assertEquals("5+", type("5", "+", "%"))
    }

    @Test
    fun backspaceAndPreview() {
        assertEquals("12", type("1", "2", "3", "<"))
        assertFalse(CalculatorInput.hasOperation("−5"))
        assertFalse(CalculatorInput.hasOperation("12"))
        assertTrue(CalculatorInput.hasOperation("1+2"))
        assertTrue(CalculatorInput.hasOperation("50%"))
    }
}
