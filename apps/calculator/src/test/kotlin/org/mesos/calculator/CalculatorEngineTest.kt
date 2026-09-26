package org.mesos.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {

    private fun eval(expr: String): String = when (val r = CalculatorEngine.evaluate(expr)) {
        is CalcResult.Value -> CalculatorEngine.format(r.value)
        is CalcResult.Error -> r.error.name
    }

    @Test
    fun respectsOperatorPrecedence() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("2.5", eval("10÷4"))
        assertEquals("−7", eval("3−10"))
    }

    @Test
    fun usesExactDecimalArithmetic() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333", eval("1÷3"))
        assertEquals("1", eval("1÷3×3"))
    }

    @Test
    fun handlesUnaryMinusAndPercent() {
        assertEquals("2", eval("−3+5"))
        assertEquals("−15", eval("5×−3"))
        assertEquals("0.5", eval("50%"))
        assertEquals("200.1", eval("200+10%"))
    }

    @Test
    fun closesOpenParentheses() {
        assertEquals("14", eval("2×(3+4"))
    }

    @Test
    fun acceptsAsciiOperatorsAndTrailingDot() {
        assertEquals("6", eval("2*3"))
        assertEquals("5", eval("5."))
        assertEquals("1", eval("4-3"))
    }

    @Test
    fun reportsErrors() {
        assertEquals("DIVIDE_BY_ZERO", eval("5÷0"))
        assertEquals("DIVIDE_BY_ZERO", eval("1÷(2−2)"))
        assertEquals("SYNTAX", eval(""))
        assertEquals("SYNTAX", eval("5+"))
        assertEquals("SYNTAX", eval("1..2"))
        assertEquals("SYNTAX", eval("2)"))
        assertEquals("OVERFLOW", eval("1" + "0".repeat(60) + "×1" + "0".repeat(60)))
    }

    @Test
    fun formatsLargeAndSmallNumbersScientifically() {
        assertEquals("1e15", eval("1000000×1000000000"))
        assertEquals("1e−7", eval("1÷10000000"))
        assertEquals("123456789012", eval("123456789012"))
    }

    @Test
    fun resultCanBeReusedAsExpression() {
        val value = (CalculatorEngine.evaluate("0−2.5") as CalcResult.Value).value
        val expr = CalculatorEngine.toExpression(value)
        assertEquals("−2.5", expr)
        assertEquals("−5", eval("$expr×2"))
    }
}
