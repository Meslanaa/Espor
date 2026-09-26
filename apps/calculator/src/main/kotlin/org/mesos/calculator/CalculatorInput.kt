package org.mesos.calculator

/** Keypad rules: how each key changes the expression text. Pure, so it is unit-tested. */
object CalculatorInput {

    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private const val OPERATORS = "+−×÷"

    private fun Char.isOperator() = this in OPERATORS

    /** Trailing number being typed, e.g. "12.5" in "3+12.5". */
    private fun currentNumber(expr: String): String = expr.takeLastWhile { it.isDigit() || it == '.' }

    fun digit(expr: String, digit: Char): String {
        require(digit.isDigit())
        val last = expr.lastOrNull()
        if (last == ')' || last == '%') return expr + TIMES + digit
        // Replace a lone leading zero ("0" -> "7"), but keep "0.".
        if (currentNumber(expr) == "0") return expr.dropLast(1) + digit
        return expr + digit
    }

    fun decimal(expr: String): String {
        val last = expr.lastOrNull()
        return when {
            currentNumber(expr).contains('.') -> expr
            last == null || last.isOperator() || last == '(' -> "${expr}0."
            last == ')' || last == '%' -> "$expr${TIMES}0."
            else -> "$expr."
        }
    }

    fun operator(expr: String, op: Char): String {
        require(op.isOperator())
        val last = expr.lastOrNull() ?: return if (op == MINUS) "$MINUS" else expr
        return when {
            // Allow a negative number after × ÷ or "(" : 5×−3, (−2
            op == MINUS && (last == TIMES || last == DIVIDE || last == '(') -> expr + op
            last == '(' -> expr
            last.isOperator() -> {
                val base = expr.dropLast(1)
                val beforeLast = base.lastOrNull()
                when {
                    // "5×−" + "+" -> "5+": replace the operator pair.
                    beforeLast != null && beforeLast.isOperator() -> base.dropLast(1) + op
                    // A lone leading "−" cannot become another operator.
                    base.isEmpty() -> expr
                    else -> base + op
                }
            }
            else -> expr + op
        }
    }

    fun percent(expr: String): String {
        val last = expr.lastOrNull() ?: return expr
        return if (last.isDigit() || last == ')' || last == '.') expr + '%' else expr
    }

    /** One key for both parentheses: closes an open group after a value, otherwise opens one. */
    fun parenthesis(expr: String): String {
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val last = expr.lastOrNull()
        val afterValue = last != null && (last.isDigit() || last == ')' || last == '%' || last == '.')
        return when {
            afterValue && open > 0 -> "$expr)"
            afterValue -> "$expr$TIMES("
            else -> "$expr("
        }
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    /** True when the expression contains an operation worth previewing (not just a number). */
    fun hasOperation(expr: String): Boolean =
        expr.drop(1).any { it.isOperator() } || expr.contains('%') || expr.contains('(')
}
