package com.kakeibo.android.core.util

/**
 * Arithmetic expression evaluator for the transaction amount calculator, ported from the web
 * `web/src/utils/calc.ts`. Supports `+ - * /` with standard operator precedence; division rounds
 * to the nearest integer (matching JS `Math.round`); returns null for empty/invalid input or
 * division by zero. Amounts are yen integers, so the value type is [Long].
 */
object Calculator {

    private sealed interface Token {
        data class Num(val value: Long) : Token
        data class Op(val value: Char) : Token
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c in '0'..'9' -> {
                    val sb = StringBuilder()
                    while (i < expr.length && expr[i] in '0'..'9') {
                        sb.append(expr[i]); i++
                    }
                    val value = sb.toString().toLongOrNull() ?: return null
                    tokens.add(Token.Num(value))
                }
                c == '+' || c == '-' || c == '*' || c == '/' -> {
                    tokens.add(Token.Op(c)); i++
                }
                else -> return null
            }
        }
        return tokens.ifEmpty { null }
    }

    private data class Parsed(val value: Long, val pos: Int)

    private fun parseFactor(tokens: List<Token>, pos: Int): Parsed? {
        if (pos >= tokens.size) return null
        val t = tokens[pos]
        return if (t is Token.Num) Parsed(t.value, pos + 1) else null
    }

    private fun parseTerm(tokens: List<Token>, pos: Int): Parsed? {
        var result = parseFactor(tokens, pos) ?: return null
        while (result.pos < tokens.size) {
            val t = tokens[result.pos]
            if (t !is Token.Op || (t.value != '*' && t.value != '/')) break
            val right = parseFactor(tokens, result.pos + 1) ?: return null
            result = if (t.value == '*') {
                val product = runCatching { Math.multiplyExact(result.value, right.value) }.getOrNull()
                    ?: return null // overflow → invalid, like an unparseable expression
                Parsed(product, right.pos)
            } else {
                if (right.value == 0L) return null
                Parsed(Math.round(result.value.toDouble() / right.value), right.pos)
            }
        }
        return result
    }

    private fun parseExpr(tokens: List<Token>, pos: Int): Parsed? {
        var result = parseTerm(tokens, pos) ?: return null
        while (result.pos < tokens.size) {
            val t = tokens[result.pos]
            if (t !is Token.Op || (t.value != '+' && t.value != '-')) break
            val right = parseTerm(tokens, result.pos + 1) ?: return null
            val combined = runCatching {
                if (t.value == '+') Math.addExact(result.value, right.value)
                else Math.subtractExact(result.value, right.value)
            }.getOrNull() ?: return null // overflow → invalid
            result = Parsed(combined, right.pos)
        }
        return result
    }

    /** Evaluates [expr], or returns null when it is empty/invalid or divides by zero. */
    fun evaluateExpression(expr: String): Long? {
        val cleaned = expr.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        val tokens = tokenize(cleaned) ?: return null
        val result = parseExpr(tokens, 0) ?: return null
        return if (result.pos == tokens.size) result.value else null
    }

    /** True when [expr] contains any arithmetic operator. */
    fun hasOperator(expr: String): Boolean =
        expr.any { it == '+' || it == '-' || it == '*' || it == '/' }
}
