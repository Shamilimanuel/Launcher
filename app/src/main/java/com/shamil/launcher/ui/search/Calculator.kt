package com.shamil.launcher.ui.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * The search box's calculator: type "1+1", "12*7", "(3+4)/2", "2^10" or "15% of 80" and get the answer.
 * It reads the sum itself (a tiny "parser"), so nothing you type is ever run as code.
 */
object Calculator {

    /** Characters a sum may contain (after "of" / "van" became ×). Anything else = not a sum, just a search. */
    private const val ALLOWED = "0123456789.,+-*/×÷xX^%() "

    /** An operator right after a number or ")" or "%": what makes "12*7" a sum and a lone "42" or "-5" not. */
    private val HAS_OPERATOR = Regex("[0-9.,)%]\\s*[-+*/×÷xX^]")

    /**
     * The answer if [text] is a plain sum, else null (so typing an app name never shows a calculator).
     * Used for what's typed straight into the search box.
     */
    fun answer(text: String): String? {
        val prepared = prepare(text) ?: return null
        if (!HAS_OPERATOR.containsMatchIn(prepared)) return null
        return evaluate(prepared)?.let { format(it, useComma = ',' in text) }
    }

    /** Like [answer] but a lone number is fine too. Used by "> calc …", where you already said it's a sum. */
    fun answerAnything(text: String): String? {
        val prepared = prepare(text) ?: return null
        return evaluate(prepared)?.let { format(it, useComma = ',' in text) }
    }

    /** "15% of 80" → "15%*80". Null when it can't be a sum (letters, no digits). */
    private fun prepare(text: String): String? {
        val t = text.trim()
            .replace(Regex("\\s+(of|van)\\s+", RegexOption.IGNORE_CASE), "*")
            .replace('−', '-')   // the "real" minus sign some keyboards type
        if (t.isEmpty() || t.none { it.isDigit() }) return null
        if (t.any { it !in ALLOWED }) return null
        return t
    }

    /** Works out the sum, or null if it isn't one (yet), like "3+" while you're still typing. */
    private fun evaluate(sum: String): Double? = try {
        Parser(sum.replace(',', '.')).readAll().takeIf { it.isFinite() }
    } catch (e: IllegalArgumentException) {
        null
    }

    /** 0.1+0.2 shows "0.3", not "0.30000000000000004". Big or tiny answers get an E (1.5E+20). */
    private fun format(value: Double, useComma: Boolean): String {
        val rounded = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val big = abs(value) >= 1e15 || (value != 0.0 && abs(value) < 1e-9)
        val text = if (big) rounded.toString() else rounded.toPlainString()
        // Typed "1,5+1"? Then answer the Dutch way too: "2,5".
        return if (useComma) text.replace('.', ',') else text
    }

    /**
     * Reads a sum left to right, one level per rule (this is called "recursive descent"):
     *  sum    = term   (+ or -  term)…
     *  term   = signed (× or ÷  signed)…
     *  signed = - signed | power
     *  power  = number-or-brackets [%] (^ signed)
     * So × goes before +, and ^ before ×, just like at school.
     */
    private class Parser(private val s: String) {
        private var i = 0

        fun readAll(): Double {
            val value = sum()
            skipSpaces()
            if (i != s.length) fail()
            return value
        }

        private fun sum(): Double {
            var value = term().value
            while (true) {
                skipSpaces()
                val op = peek()
                if (op != '+' && op != '-') return value
                i++
                val right = term()
                // "80 + 10%" means 80 plus 10% of 80, like a pocket calculator.
                val amount = if (right.isPercent) value * right.value else right.value
                value = if (op == '+') value + amount else value - amount
            }
        }

        private fun term(): Part {
            var part = signed()
            while (true) {
                skipSpaces()
                val op = peek()
                if (op == null || op !in "*/×÷xX") return part
                i++
                val right = signed().value
                val value = if (op == '/' || op == '÷') {
                    if (right == 0.0) fail()
                    part.value / right
                } else {
                    part.value * right
                }
                part = Part(value, isPercent = false)
            }
        }

        private fun signed(): Part {
            skipSpaces()
            return when (peek()) {
                '-' -> { i++; signed().let { Part(-it.value, it.isPercent) } }
                '+' -> { i++; signed() }
                else -> power()
            }
        }

        private fun power(): Part {
            var part = number()
            skipSpaces()
            if (peek() == '%') {
                i++
                part = Part(part.value / 100, isPercent = true)
                skipSpaces()
            }
            if (peek() == '^') {
                i++
                val exponent = signed().value
                part = Part(part.value.pow(exponent), isPercent = false)
            }
            return part
        }

        private fun number(): Part {
            skipSpaces()
            if (peek() == '(') {
                i++
                val inside = sum()
                skipSpaces()
                // A missing ")" at the very end is fine: you're probably still typing it.
                if (peek() == ')') i++ else if (i != s.length) fail()
                return Part(inside, isPercent = false)
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            val digits = s.substring(start, i)
            val value = digits.toDoubleOrNull() ?: fail()
            return Part(value, isPercent = false)
        }

        private fun peek(): Char? = s.getOrNull(i)
        private fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }
        private fun fail(): Nothing = throw IllegalArgumentException("not a sum")
    }

    /** A number on its way through the parser; [isPercent] remembers it was written like "10%". */
    private class Part(val value: Double, val isPercent: Boolean)
}
