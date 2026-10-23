package com.filhoaf.quadro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.sin
import kotlin.math.cos

val CLOCKS = listOf(
    "digital" to "Digital", "segundos" to "Com segundos", "gigante" to "Gigante", "bloco" to "Blocos",
    "elastico" to "Elástico", "vertical" to "Empilhado", "texto" to "Por extenso", "binario" to "Binário",
    "terminal" to "Terminal", "runico" to "Rúnico", "fino" to "Fino", "barras" to "Barras do dia",
    "analogico" to "Analógico quadrado",
)

private val UNI = listOf("", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "doze",
    "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove")
private val DEZ = mapOf(2 to "vinte", 3 to "trinta", 4 to "quarenta", 5 to "cinquenta")
private fun minExt(m: Int) = if (m < 20) UNI[m] else DEZ[m / 10] + if (m % 10 != 0) " e " + UNI[m % 10] else ""
private val HORAS = listOf("meia-noite", "uma", "duas", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "doze")

@Composable
fun ClockView(cfg: Config, s: Float, notifCount: Int) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000 - value % 1000) }
    }
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val hh = c.get(Calendar.HOUR_OF_DAY); val mm = c.get(Calendar.MINUTE); val ss = c.get(Calendar.SECOND)
    val h = "%02d".format(hh); val m = "%02d".format(mm)
    val z = cfg.clockScale * s
    val col = if (cfg.clockColor == 0) INK else Color(cfg.clockColor)
    val acc = Color(Accent.current(cfg))
    val face = when (cfg.clockFont) {
        "sans" -> "sans"; "mono" -> "mono"; "serif" -> "serif"; "cursiva" -> "cursive"
        else -> themeFace(cfg.theme)
    }
    val w = cfg.clockWeight
    when (cfg.clockStyle) {
        "segundos" -> Row(verticalAlignment = Alignment.Bottom) {
            T("$h:$m", 64f * z, color = col, weight = w, face = face)
            T(":%02d".format(ss), 26f * z, color = acc, weight = w, face = face, modifier = Modifier.padding(bottom = (10 * z).dp))
        }
        "gigante" -> T("$h\n$m", 118f * z, color = col, weight = w, face = face, lineHeight = 104f * z)
        "bloco" -> Row(horizontalArrangement = Arrangement.spacedBy((4 * z).dp)) {
            (h + m).forEachIndexed { i, ch ->
                if (i == 2) Spacer(Modifier.width((8 * z).dp))
                Box(Modifier.background(col, Store.cfg.rr(10f)).padding(horizontal = (8 * z).dp, vertical = (2 * z).dp)) {
                    T(ch.toString(), 46f * z, color = Color(0xFF0A0A0A), weight = 900, face = face, shadow = false)
                }
            }
        }
        "elastico" -> {
            val big by animateFloatAsState(if (notifCount > 0) 54f else 116f, label = "elastico")
            T("$h:$m", big * z, color = col, weight = if (notifCount > 0) 500 else 200, face = face, letter = -2f)
        }
        "vertical" -> Column {
            T(h, 84f * z, color = col, weight = w.coerceAtLeast(600), face = face, lineHeight = 80f * z)
            T(m, 84f * z, color = col.copy(alpha = 0.55f), weight = w.coerceAtLeast(600), face = face, lineHeight = 80f * z)
        }
        "texto" -> Column {
            val um = hh % 12 == 1 || hh == 0 || hh == 12
            val hw = when (hh) { 0 -> HORAS[0]; 12 -> "meio-dia"; else -> HORAS[hh % 12] }
            T(if (um) "é" else "são", 20f * z, color = DIM, face = face)
            T(hw, 42f * z, color = col, weight = 800, face = face, lineHeight = 44f * z)
            T(if (mm == 0) "em ponto" else "e ${minExt(mm)}", 26f * z, color = col, weight = 300, face = face)
        }
        "binario" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BinRow(hh, 5, col, z); BinRow(mm, 6, col, z)
            T("$h:$m", 12f, color = DIM, face = "mono")
        }
        "terminal" -> Column {
            T("$ date +%H:%M", 14f * z, color = acc, face = "mono")
            T("$h:$m" + if (ss % 2 == 0) "█" else " ", 50f * z, color = col, weight = 800, face = "mono")
        }
        "runico" -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(h, 60f * z, color = col, weight = w.coerceAtLeast(400), face = "serif")
                T(RUNES[ss % RUNES.length].toString(), 44f * z, color = acc, face = "runic", modifier = Modifier.padding(horizontal = 6.dp))
                T(m, 60f * z, color = col, weight = w.coerceAtLeast(400), face = "serif")
            }
            T(
                h.map { RUNES[it - '0'] }.joinToString("") + " · " + m.map { RUNES[it - '0' + 10] }.joinToString(""),
                16f * z, color = DIM, face = "runic", letter = 4f,
            )
        }
        "fino" -> T("$h:$m", 76f * z, color = col, weight = 100, face = face)
        "barras" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("$h:$m", 48f * z, color = col, weight = w, face = face)
            Row(Modifier.width((220 * z).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(24) { i ->
                    Box(
                        Modifier.weight(1f).height((14 * z).dp).background(
                            when { i < hh -> col; i == hh -> acc; else -> col.copy(alpha = 0.18f) },
                        ),
                    )
                }
            }
            T("${(hh * 60 + mm) * 100 / 1440}% do dia", 11f, color = DIM, face = "mono")
        }
        "analogico" -> Canvas(Modifier.size((150 * z).dp)) {
            val sz = size.minDimension; val cx = sz / 2; val st = sz * 0.03f
            drawRect(col, topLeft = Offset(st / 2, st / 2), size = Size(sz - st, sz - st), style = Stroke(st))
            for (i in 0 until 12) {
                val a = i * Math.PI / 6; val r = sz * 0.40f
                drawRect(col, Offset(cx + (r * sin(a)).toFloat() - st, cx - (r * cos(a)).toFloat() - st), Size(st * 2, st * 2))
            }
            fun hand(angle: Double, len: Float, wd: Float, cl: Color) {
                rotate((angle * 180 / Math.PI).toFloat(), Offset(cx, cx)) { drawRect(cl, Offset(cx - wd / 2, cx - len), Size(wd, len)) }
            }
            hand((hh % 12 + mm / 60.0) * Math.PI / 6, sz * 0.24f, st * 2.2f, col)
            hand(mm * Math.PI / 30, sz * 0.34f, st * 1.4f, col)
            hand(ss * Math.PI / 30, sz * 0.38f, st * 0.6f, acc)
            drawRect(col, Offset(cx - st * 1.5f, cx - st * 1.5f), Size(st * 3, st * 3))
        }
        else -> T("$h:$m", 64f * z, color = col, weight = w, face = face, letter = -1f)
    }
}

@Composable
fun BinRow(v: Int, bits: Int, col: Color, z: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        v.toString(2).padStart(bits, '0').forEach { b ->
            Box(Modifier.size((16 * z).dp).border(1.5.dp, col, Store.cfg.rr(6f)).background(if (b == '1') col else Color.Transparent, Store.cfg.rr(6f)))
        }
    }
}
