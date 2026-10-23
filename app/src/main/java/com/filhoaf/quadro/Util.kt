package com.filhoaf.quadro

import android.content.Context
import android.content.Intent
import java.util.Locale
import kotlin.math.abs

fun startSafe(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

fun List<String>.swap(a: Int, b: Int): List<String> {
    if (a < 0 || a >= size || b < 0 || b >= size) return this
    val m = toMutableList(); val t = m[a]; m[a] = m[b]; m[b] = t; return m
}

/** Calculadora simples: + - * / e parênteses. */
fun calc(s: String): String? {
    val e = s.replace(",", ".").replace(" ", "").replace("x", "*")
    if (e.isEmpty() || !e.any { it.isDigit() } || !e.any { it in "+-*/" } || !e.all { it.isDigit() || it in "+-*/()." }) return null
    return try {
        val p = Calc(e)
        val v = p.expr()
        if (!p.done() || v.isNaN() || v.isInfinite()) null
        else if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString()
        else "%.6f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    } catch (ex: Exception) {
        null
    }
}

private class Calc(val e: String) {
    var p = 0
    fun done() = p == e.length
    fun expr(): Double {
        var v = term()
        while (p < e.length && e[p] in "+-") {
            val op = e[p++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }
    fun term(): Double {
        var v = num()
        while (p < e.length && e[p] in "*/") {
            val op = e[p++]
            val r = num()
            v = if (op == '*') v * r else v / r
        }
        return v
    }
    fun num(): Double {
        if (p < e.length && e[p] == '(') {
            p++
            val v = expr()
            if (p < e.length && e[p] == ')') p++
            return v
        }
        if (p < e.length && e[p] == '-') { p++; return -num() }
        val st = p
        while (p < e.length && (e[p].isDigit() || e[p] == '.')) p++
        return e.substring(st, p).toDouble()
    }
}
