package com.filhoaf.quadro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val INK = Color(0xFFF3F5F4)
val DIM = Color(0xB8F3F5F4)
private val SH = Shadow(Color(0x99000000), Offset(0f, 2f), 8f)
const val RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ"

fun runeOf(s: String): String = RUNES[(s.firstOrNull()?.code ?: 0) % RUNES.length].toString()

/** Fontes embutidas (variáveis, então o peso funciona de verdade). */
object Fonts {
    private val cache = HashMap<String, FontFamily>()

    private fun vf(res: Int, w: Int): FontFamily = cache.getOrPut("$res:$w") {
        FontFamily(Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    }

    val runic: FontFamily by lazy { FontFamily(Font(R.font.noto_runic)) }

    fun get(face: String, weight: Int): FontFamily = when (face) {
        "sans" -> vf(R.font.inter_tight, weight.coerceIn(100, 900))
        "mono" -> vf(R.font.jetbrains_mono, weight.coerceIn(100, 800))
        "serif" -> vf(R.font.cinzel, weight.coerceIn(400, 900))
        "runic" -> runic
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
}

fun themeFace(t: String): String = when (t) {
    "tech" -> "mono"
    "runa" -> "serif"
    else -> "sans"
}

@Composable
fun T(
    text: String, size: Float, modifier: Modifier = Modifier, color: Color = INK, weight: Int = 400,
    face: String = "sans", shadow: Boolean = true, lineHeight: Float = 0f,
    align: TextAlign = TextAlign.Start, letter: Float = 0f, wrap: Boolean = false,
    ellipsis: Boolean = false, maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontWeight = FontWeight(weight.coerceIn(1, 1000)),
            fontFamily = Fonts.get(face, weight), shadow = if (shadow) SH else null,
            lineHeight = if (lineHeight > 0f) lineHeight.sp else TextUnit.Unspecified,
            textAlign = align, letterSpacing = letter.sp,
        ),
        overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
        softWrap = wrap,
        maxLines = maxLines,
    )
}

/* ---------- formato (quadrado / suave / redondo) ---------- */

/** Arredondamento para painéis, botões e cartões. maxDp é o raio quando o formato está em "Redondo". */
fun Config.rr(maxDp: Float): RoundedCornerShape = RoundedCornerShape((maxDp * corner / 50f).dp)

/** Arredondamento dos ícones, em porcentagem (50 = círculo). */
fun Config.iconShape(): RoundedCornerShape = RoundedCornerShape(percent = corner.coerceIn(0, 50))

/* ---------- estilo dos painéis (referências: roxo suave e contorno) ---------- */

class Tok(
    val bg: Color, val card: Color, val card2: Color, val line: Color, val lineW: Dp,
    val text: Color, val muted: Color, val acc: Color, val onAcc: Color,
)

fun tok(cfg: Config): Tok = if (cfg.uiStyle == "contorno") Tok(
    bg = Color(0xFF14132A), card = Color(0xFF1C1B38), card2 = Color(0xFF14132A),
    line = Color(0xFFF2F0FA), lineW = 2.dp, text = Color(0xFFF4F1FF), muted = Color(0xFFA9A5C9),
    acc = Color(0xFFFFC48A), onAcc = Color(0xFF1B1630),
) else Tok(
    bg = Color(0xFF2C2558), card = Color(0xFF3B3470), card2 = Color(0xFF332C63),
    line = Color(0x00000000), lineW = 0.dp, text = Color(0xFFF4EFFA), muted = Color(0xFFB4AAD6),
    acc = Color(0xFFF3C58E), onAcc = Color(0xFF2A2250),
)

val LocalTok = compositionLocalOf { tok(Config()) }
val LocalCfg = compositionLocalOf { Config() }

/** Cartão: sombra suave no estilo "suave", contorno grosso no estilo "contorno". */
fun Modifier.card(t: Tok, shape: Shape, fill: Color = t.card): Modifier {
    var m: Modifier = this
    if (t.lineW == 0.dp) m = m.shadow(8.dp, shape, clip = false)
    m = m.background(fill, shape)
    if (t.lineW > 0.dp) m = m.border(t.lineW, t.line, shape)
    return m
}

@Composable
fun SqBtn(label: String, bg: Color, fg: Color = Color(0xFF0A0A0A), shape: Shape = RoundedCornerShape(0.dp), modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(40.dp, 40.dp).background(bg, shape).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, 15f, color = fg, weight = 800, shadow = false) }
}
