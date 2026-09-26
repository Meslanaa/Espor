package org.mesos.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcError { SYNTAX, DIVIDE_BY_ZERO, OVERFLOW }

sealed interface CalcResult {
    data class Value(val value: BigDecimal) : CalcResult
    data class Error(val error: CalcError) : CalcResult
}

/**
 * Evaluates calculator expressions with exact decimal arithmetic.
 *
 * Grammar: expr = term (("+"|"−") term)* ; term = unary (("×"|"÷") unary)* ;
 * unary = ("−"|"+") unary | postfix ; postfix = primary "%"* ; primary = number | "(" expr ")".
 * `x%` means x/100. Unclosed parentheses are closed automatically.
 */
object CalculatorEngine {

    private val CONTEXT = MathContext.DECIMAL128
    private val LIMIT = BigDecimal("1e100")
    private val HUNDRED = BigDecimal(100)

    fun evaluate(expression: String): CalcResult {
        val tokens = tokenize(expression) ?: return CalcResult.Error(CalcError.SYNTAX)
        if (tokens.isEmpty()) return CalcResult.Error(CalcError.SYNTAX)
        val open = tokens.count { it == Token.Open } - tokens.count { it == Token.Close }
        val closed = if (open > 0) tokens + List(open) { Token.Close } else tokens
        return try {
            val parser = Parser(closed)
            val value = parser.expression()
            if (!parser.atEnd()) CalcResult.Error(CalcError.SYNTAX) else CalcResult.Value(value)
        } catch (e: CalcException) {
            CalcResult.Error(e.error)
        }
    }

    /** Result for display: at most 12 significant digits, scientific notation when very large/small. */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        val abs = rounded.abs()
        val text = if (abs.signum() != 0 && (abs >= BigDecimal("1e12") || abs < BigDecimal("1e-6"))) {
            rounded.toString().replace("E+", "e").replace("E", "e")
        } else {
            rounded.toPlainString()
        }
        return text.replace('-', '−')
    }

    /** Result as an expression the user can keep calculating with. */
    fun toExpression(value: BigDecimal): String =
        value.round(MathContext(15, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString().replace('-', '−')

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data object Plus : Token
        data object Minus : Token
        data object Times : Token
        data object Divide : Token
        data object Percent : Token
        data object Open : Token
        data object Close : Token
    }

    private class CalcException(val error: CalcError) : Exception()

    private fun tokenize(input: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    val text = input.substring(start, i)
                    if (text.count { it == '.' } > 1 || text == ".") return null
                    tokens += Token.Number(BigDecimal(if (text.endsWith('.')) text.dropLast(1) else text))
                }
                else -> {
                    tokens += when (c) {
                        '+' -> Token.Plus
                        '-', '−' -> Token.Minus
                        '*', '×' -> Token.Times
                        '/', '÷' -> Token.Divide
                        '%' -> Token.Percent
                        '(' -> Token.Open
                        ')' -> Token.Close
                        else -> return null
                    }
                    i++
                }
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun atEnd() = pos == tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)

        fun expression(): BigDecimal {
            var value = term()
            while (true) {
                value = when (peek()) {
                    Token.Plus -> { pos++; checked(value.add(term(), CONTEXT)) }
                    Token.Minus -> { pos++; checked(value.subtract(term(), CONTEXT)) }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = unary()
            while (true) {
                value = when (peek()) {
                    Token.Times -> { pos++; checked(value.multiply(unary(), CONTEXT)) }
                    Token.Divide -> {
                        pos++
                        val divisor = unary()
                        if (divisor.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO)
                        checked(value.divide(divisor, CONTEXT))
                    }
                    else -> return value
                }
            }
        }

        private fun unary(): BigDecimal = when (peek()) {
            Token.Minus -> { pos++; unary().negate() }
            Token.Plus -> { pos++; unary() }
            else -> postfix()
        }

        private fun postfix(): BigDecimal {
            var value = primary()
            while (peek() == Token.Percent) {
                pos++
                value = value.divide(HUNDRED, CONTEXT)
            }
            return value
        }

        private fun primary(): BigDecimal = when (val token = peek()) {
            is Token.Number -> { pos++; token.value }
            Token.Open -> {
                pos++
                val value = expression()
                if (peek() != Token.Close) throw CalcException(CalcError.SYNTAX)
                pos++
                value
            }
            else -> throw CalcException(CalcError.SYNTAX)
        }

        private fun checked(value: BigDecimal): BigDecimal {
            if (value.abs() > LIMIT) throw CalcException(CalcError.OVERFLOW)
            return value
        }
    }
}
