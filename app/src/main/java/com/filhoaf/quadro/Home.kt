package com.filhoaf.quadro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val INK = Color(0xFFF3F5F4)
val DIM = Color(0xB8F3F5F4)
private val SH = Shadow(Color(0x99000000), Offset(0f, 2f), 8f)
const val RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ"
val AZ: List<String> = listOf("◎") + ('A'..'Z').map { it.toString() } + listOf("#", "⚙")

fun themeFont(t: String): FontFamily = when (t) {
    "tech" -> FontFamily.Monospace
    "runa" -> FontFamily.Serif
    else -> FontFamily.SansSerif
}

fun runeOf(s: String): String = RUNES[(s.firstOrNull()?.code ?: 0) % RUNES.length].toString()

@Composable
fun T(
    text: String, size: Float, modifier: Modifier = Modifier, color: Color = INK, weight: Int = 400,
    family: FontFamily = FontFamily.SansSerif, shadow: Boolean = true, lineHeight: Float = 0f,
    align: TextAlign = TextAlign.Start, letter: Float = 0f,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontWeight = FontWeight(weight.coerceIn(1, 1000)),
            fontFamily = family, shadow = if (shadow) SH else null,
            lineHeight = if (lineHeight > 0f) lineHeight.sp else TextUnit.Unspecified,
            textAlign = align, letterSpacing = letter.sp,
        ),
    )
}

fun startSafe(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/* =================== APP PRINCIPAL =================== */

@Composable
fun QuadroApp(homeTick: Int) {
    val ctx = LocalContext.current
    val cfg = Store.cfg
    val apps = Apps.list
    val notifs by NotifCenter.items.collectAsState()
    val notifPkgs = remember(notifs) { notifs.map { it.pkg }.toSet() }
    val haptic = LocalHapticFeedback.current

    var editing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var panelLetter by remember { mutableStateOf<String?>(null) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    fun closeAll() { editing = false; settings = false; search = false; menuApp = null; panelLetter = null }
    LaunchedEffect(homeTick) { closeAll() }
    BackHandler {
        when {
            menuApp != null -> menuApp = null
            settings -> settings = false
            search -> search = false
            panelLetter != null -> panelLetter = null
            editing -> editing = false
        }
    }

    // bateria pro amiguinho
    var batt by remember { mutableIntStateOf(100) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                batt = l * 100 / s
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        onDispose { try { ctx.unregisterReceiver(r) } catch (e: Exception) { } }
    }

    // primeira vez: escolhe favoritos com os apps que você mais usa
    LaunchedEffect(apps) {
        if (Store.cfg.firstRun && apps.isNotEmpty()) {
            val pref = listOf(
                "com.whatsapp", "com.google.android.youtube", "com.anthropic.claude",
                "com.openai.chatgpt", "com.instagram.android", "com.spotify.music",
            )
            val chosen = pref.mapNotNull { p -> apps.firstOrNull { it.pkg == p }?.key }
                .ifEmpty { apps.take(6).map { it.key } }
            Store.update { it.copy(favs = chosen, firstRun = false) }
        }
    }

    val visible = apps.filter { it.key !in cfg.hidden }
    val onApp: (AppInfo) -> Unit = { a -> Apps.launch(ctx, a); panelLetter = null; search = false }
    val onLong: (AppInfo) -> Unit = { a -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuApp = a }
    val canEdit by rememberUpdatedState(dragIdx < 0 && panelLetter == null && !settings && !search)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                if (canEdit) { editing = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            })
        },
    ) {
        // leve escurecimento só nas bordas, o papel de parede continua aparecendo
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x59000000), Color(0x00000000), Color(0x00000000), Color(0x66000000)))))

        HomeScreen(cfg, apps, notifPkgs, notifs, batt, charging, editing, onApp, onLong)

        if (panelLetter != null || dragIdx >= 0) {
            LetterPanel(cfg, visible, panelLetter, dragIdx, fingerY, notifPkgs, onApp, onLong) { panelLetter = null }
        }

        AlphaBar(
            cfg, dragIdx,
            onTouch = { i, y ->
                if (i != dragIdx) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                dragIdx = i; fingerY = y
                val l = AZ[i]
                if (l != "◎" && l != "⚙") panelLetter = l
            },
            onRelease = { i ->
                dragIdx = -1
                when (AZ[i]) {
                    "◎" -> { panelLetter = null; search = true }
                    "⚙" -> { panelLetter = null; settings = true }
                }
            },
        )

        if (search) SearchOverlay(cfg, apps, notifPkgs, onApp, onLong) { search = false }
        if (editing) EditBar(cfg) { editing = false }
        menuApp?.let { a -> AppMenu(a, cfg) { menuApp = null } }
        if (settings) SettingsScreen(onClose = { settings = false }, onEdit = { settings = false; editing = true })
    }
}

/* =================== TELA INICIAL =================== */

@Composable
fun HomeScreen(
    cfg: Config, apps: List<AppInfo>, notifPkgs: Set<String>, notifs: List<Notif>,
    batt: Int, charging: Boolean, editing: Boolean, onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit,
) {
    val left = cfg.azSide == "left"
    Column(
        Modifier.fillMaxSize().systemBarsPadding()
            .padding(start = if (left) 54.dp else 26.dp, end = if (left) 26.dp else 54.dp, top = if (editing) 70.dp else 30.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val shown = cfg.order.withIndex().filter { (_, k) ->
            val skip = k == "buddy" && (!cfg.buddyOn || cfg.buddyInline)
            !skip && (k !in cfg.hiddenBlocks || editing)
        }
        for ((i, k) in shown) {
            val hid = k in cfg.hiddenBlocks
            val s = cfg.scale[k] ?: 1f
            val al = when (cfg.align[k]) {
                "center" -> Alignment.CenterHorizontally
                "right" -> Alignment.End
                else -> Alignment.Start
            }
            val base = if (k == "spacer") Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            Column(
                base.alpha(if (hid) 0.35f else 1f)
                    .then(if (editing) Modifier.border(1.dp, Color(0x88FFFFFF)).padding(8.dp) else Modifier),
                horizontalAlignment = al,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editing) EditTools(k, i, cfg)
                when (k) {
                    "clock" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClockView(cfg, s, notifs.size)
                        if (cfg.buddyOn && cfg.buddyInline) BuddyView(cfg, s, notifs, batt, charging)
                    }
                    "buddy" -> BuddyView(cfg, s, notifs, batt, charging)
                    "date" -> DateView(cfg, s)
                    "treino" -> TreinoView(cfg, s)
                    "favs" -> FavsView(cfg, apps, notifPkgs, s, al, onApp, onLong)
                    "spacer" -> if (editing) T("espaço livre", 12f, color = DIM)
                }
            }
        }
    }
}

val BLOCK_NAMES = mapOf(
    "clock" to "Relógio", "buddy" to "Amiguinho", "date" to "Data e semana",
    "treino" to "Treino do dia", "spacer" to "Espaço livre", "favs" to "Apps favoritos",
)

@Composable
fun SqBtn(label: String, bg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(36.dp, 36.dp).background(bg).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, 14f, color = Color(0xFF0A0A0A), weight = 800, shadow = false) }
}

@Composable
fun EditTools(k: String, i: Int, cfg: Config) {
    val acc = Color(Accent.current(cfg))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(BLOCK_NAMES[k] ?: k, 11f, color = acc, weight = 700, letter = 1f)
        Spacer(Modifier.width(4.dp))
        SqBtn("↑", acc) { Store.update { c -> c.copy(order = c.order.swap(i, i - 1)) } }
        SqBtn("↓", acc) { Store.update { c -> c.copy(order = c.order.swap(i, i + 1)) } }
        SqBtn("⇔", acc) {
            val o = listOf("left", "center", "right")
            Store.update { c -> c.copy(align = c.align + (k to o[(o.indexOf(c.align[k] ?: "left") + 1) % 3])) }
        }
        if (k != "spacer") {
            SqBtn("−", acc) { Store.update { c -> c.copy(scale = c.scale + (k to ((c.scale[k] ?: 1f) - 0.1f).coerceAtLeast(0.5f))) } }
            SqBtn("+", acc) { Store.update { c -> c.copy(scale = c.scale + (k to ((c.scale[k] ?: 1f) + 0.1f).coerceAtMost(2.2f))) } }
        }
        SqBtn(if (k in cfg.hiddenBlocks) "◻" else "◼", acc) {
            Store.update { c -> c.copy(hiddenBlocks = if (k in c.hiddenBlocks) c.hiddenBlocks - k else c.hiddenBlocks + k) }
        }
    }
}

fun List<String>.swap(a: Int, b: Int): List<String> {
    if (b < 0 || b >= size) return this
    val m = toMutableList(); val t = m[a]; m[a] = m[b]; m[b] = t; return m
}

@Composable
fun BoxScope.EditBar(cfg: Config, onDone: () -> Unit) {
    val acc = Color(Accent.current(cfg))
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xE6000000)).statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("MODO EDIÇÃO", 13f, color = acc, weight = 800, letter = 2f)
            T("↑↓ move · ⇔ alinha · −+ tamanho · ◼ esconde", 11f, color = DIM)
        }
        SqBtn("Concluir", acc) { onDone() }
    }
}

/* =================== BLOCOS =================== */

@Composable
fun FavsView(
    cfg: Config, apps: List<AppInfo>, notifPkgs: Set<String>, s: Float, al: Alignment.Horizontal,
    onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit,
) {
    val fav = cfg.favs.mapNotNull { k -> apps.firstOrNull { it.key == k } }.filter { it.key !in cfg.hidden }
    Column(horizontalAlignment = al, verticalArrangement = Arrangement.spacedBy((11 * s).dp)) {
        fav.forEach { AppRow(it, cfg, s, it.pkg in notifPkgs, onApp, onLong) }
        if (fav.isEmpty() && apps.isNotEmpty()) T("deslize na barra de letras, segure um app → Favoritar", 13f, color = DIM)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(app: AppInfo, cfg: Config, s: Float, dot: Boolean, onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit) {
    val fam = themeFont(cfg.theme)
    var label = if (cfg.lowercase) app.label.lowercase() else app.label
    if (cfg.theme == "tech") label = "> $label"
    val mode = cfg.labelMode
    val acc = Color(Accent.current(cfg))
    val ic = cfg.iconSize * s
    Row(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { onApp(app) }, onLongClick = { onLong(app) },
        ).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mode == "icon_name" || mode == "icon") AppIcon(app, ic, cfg)
        if (mode != "icon") T(label, cfg.fontSize * s, weight = cfg.fontWeight, family = fam)
        if (cfg.theme == "runa" && mode != "icon") T(runeOf(app.label), cfg.fontSize * s * 0.8f, color = acc, family = FontFamily.Serif)
        if (mode == "name_icon") AppIcon(app, ic, cfg)
        if (dot) Box(Modifier.size(7.dp).background(acc))
    }
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Float, cfg: Config) {
    val ctx = LocalContext.current
    val mode = cfg.perApp[app.key] ?: cfg.iconMode
    val accent = Accent.current(cfg)
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceIn(48, 192)
    val img by produceState<ImageBitmap?>(null, app.key, mode, accent, px) {
        value = IconCache.get(ctx, app, mode, accent, px)
    }
    Box(Modifier.size(sizeDp.dp).background(Color(0x33000000))) {
        img?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
fun DateView(cfg: Config, s: Float) {
    val c = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4 }
    val d = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(c.time)
    val pre = if (cfg.theme == "runa") "ᛞ " else if (cfg.theme == "tech") "// " else ""
    T("$pre$d · semana ${c.get(Calendar.WEEK_OF_YEAR)}", 14f * s, color = DIM, family = themeFont(cfg.theme), letter = 0.3f)
}

@Composable
fun TreinoView(cfg: Config, s: Float) {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    val t = cfg.treino.getOrElse(day) { "" }.ifBlank { "Descanso" }
    Column {
        T(if (cfg.theme == "runa") "ᛏ TREINO DE HOJE" else "TREINO DE HOJE", 10f * s, color = DIM, weight = 600, letter = 2f)
        T(t, 17f * s, family = themeFont(cfg.theme), weight = 500)
    }
}

/* =================== AMIGUINHO =================== */

val HAPPY = listOf("(^‿^)", "(＾▽＾)", "(•̀ᴗ•́)و", "(¬‿¬)", "ヽ(•‿•)ノ")
val FACES = listOf("(♥‿♥)", "(^o^)/", "(⊙_⊙)", "(ง •̀_•́)ง", "(｡•́︿•̀｡)", "(⌐■_■)", "(☆▽☆)")

@Composable
fun BuddyView(cfg: Config, s: Float, notifs: List<Notif>, batt: Int, charging: Boolean) {
    var temp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(temp) { if (temp != null) { delay(2500); temp = null } }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hit = notifs.firstNotNullOfOrNull { n ->
        cfg.rules.firstOrNull { r -> r.who.isNotBlank() && (n.title.contains(r.who, true) || n.text.contains(r.who, true)) }
    }
    val t = temp
    val (face, msg) = when {
        t != null -> t to "oi! ${cfg.buddyName} aqui"
        hit != null -> hit.face to hit.msg.replace("{q}", hit.who)
        charging -> "(^‿^)ϟ" to "carregando $batt%"
        batt <= 15 -> "(×_×)" to "bateria em $batt%!"
        batt <= 35 -> "(-_-)" to ""
        hour < 6 -> "(-‿-) z" to "hora de dormir"
        notifs.isNotEmpty() -> "(•o•)!" to (if (notifs.size == 1) "1 notificação" else "${notifs.size} notificações")
        else -> "(•‿•)" to ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.border(1.5.dp, INK)
                .clickable(remember { MutableInteractionSource() }, null) { temp = HAPPY.random() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) { T(face, 19f * s, family = FontFamily.Monospace) }
        if (msg.isNotEmpty()) T(msg, 12f * s, color = DIM, modifier = Modifier.widthIn(max = 170.dp))
    }
}

/* =================== RELÓGIOS =================== */

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
private val HORAS = listOf("meia-noite", "uma", "duas", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "meio-dia")

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
    val fam = when (cfg.clockFont) {
        "sans" -> FontFamily.SansSerif; "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif; "cursiva" -> FontFamily.Cursive
        else -> themeFont(cfg.theme)
    }
    val w = cfg.clockWeight
    when (cfg.clockStyle) {
        "segundos" -> Row(verticalAlignment = Alignment.Bottom) {
            T("$h:$m", 64f * z, color = col, weight = w, family = fam)
            T(":%02d".format(ss), 26f * z, color = acc, weight = w, family = fam, modifier = Modifier.padding(bottom = (10 * z).dp))
        }
        "gigante" -> T("$h\n$m", 118f * z, color = col, weight = w, family = fam, lineHeight = 104f * z)
        "bloco" -> Row(horizontalArrangement = Arrangement.spacedBy((4 * z).dp)) {
            (h + m).forEachIndexed { i, ch ->
                if (i == 2) Spacer(Modifier.width((8 * z).dp))
                Box(Modifier.background(col).padding(horizontal = (8 * z).dp, vertical = (2 * z).dp)) {
                    T(ch.toString(), 46f * z, color = Color(0xFF0A0A0A), weight = 900, family = fam, shadow = false)
                }
            }
        }
        "elastico" -> {
            val big by animateFloatAsState(if (notifCount > 0) 54f else 116f, label = "elastico")
            T("$h:$m", big * z, color = col, weight = if (notifCount > 0) 500 else 200, family = fam, letter = -2f)
        }
        "vertical" -> Column {
            T(h, 84f * z, color = col, weight = w.coerceAtLeast(700), family = fam, lineHeight = 80f * z)
            T(m, 84f * z, color = col.copy(alpha = 0.55f), weight = w.coerceAtLeast(700), family = fam, lineHeight = 80f * z)
        }
        "texto" -> Column {
            val um = hh % 12 == 1 || hh == 0 || hh == 12
            val hw = when (hh) { 0 -> HORAS[0]; 12 -> HORAS[12]; else -> HORAS[hh % 12] }
            T(if (um) "é" else "são", 20f * z, color = DIM, family = fam)
            T(hw, 42f * z, color = col, weight = 800, family = fam, lineHeight = 44f * z)
            T(if (mm == 0) "em ponto" else "e ${minExt(mm)}", 26f * z, color = col, weight = 300, family = fam)
        }
        "binario" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BinRow(hh, 5, col, z); BinRow(mm, 6, col, z)
            T("$h:$m", 12f, color = DIM, family = FontFamily.Monospace)
        }
        "terminal" -> Column {
            T("$ date +%H:%M", 14f * z, color = acc, family = FontFamily.Monospace)
            T("$h:$m" + if (ss % 2 == 0) "█" else " ", 50f * z, color = col, weight = 800, family = FontFamily.Monospace)
        }
        "runico" -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(h, 60f * z, color = col, weight = w, family = FontFamily.Serif)
                T(RUNES[ss % RUNES.length].toString(), 44f * z, color = acc, family = FontFamily.Serif, modifier = Modifier.padding(horizontal = 6.dp))
                T(m, 60f * z, color = col, weight = w, family = FontFamily.Serif)
            }
            T(h.map { RUNES[it - '0'] }.joinToString("") + " · " + m.map { RUNES[it - '0' + 10] }.joinToString(""), 16f * z, color = DIM, letter = 4f)
        }
        "fino" -> T("$h:$m", 76f * z, color = col, weight = 100, family = fam)
        "barras" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("$h:$m", 48f * z, color = col, weight = w, family = fam)
            Row(Modifier.width((220 * z).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(24) { i ->
                    Box(Modifier.weight(1f).height((14 * z).dp).background(when {
                        i < hh -> col; i == hh -> acc; else -> col.copy(alpha = 0.18f)
                    }))
                }
            }
            T("${(hh * 60 + mm) * 100 / 1440}% do dia", 11f, color = DIM, family = FontFamily.Monospace)
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
        else -> T("$h:$m", 64f * z, color = col, weight = w, family = fam, letter = -1f)
    }
}

@Composable
fun BinRow(v: Int, bits: Int, col: Color, z: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        v.toString(2).padStart(bits, '0').forEach { b ->
            Box(Modifier.size((16 * z).dp).border(1.5.dp, col).background(if (b == '1') col else Color.Transparent))
        }
    }
}

/* =================== BARRA DE LETRAS (estilo Niagara) =================== */

@Composable
fun BoxScope.AlphaBar(cfg: Config, activeIdx: Int, onTouch: (Int, Float) -> Unit, onRelease: (Int) -> Unit) {
    val left = cfg.azSide == "left"
    var topY by remember { mutableFloatStateOf(0f) }
    val touch by rememberUpdatedState(onTouch)
    val release by rememberUpdatedState(onRelease)
    val acc = Color(Accent.current(cfg))
    Column(
        Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .systemBarsPadding()
            .fillMaxHeight(0.80f)
            .width(36.dp)
            .onGloballyPositioned { topY = it.positionInRoot().y }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun idxOf(y: Float) = ((y / size.height) * AZ.size).toInt().coerceIn(0, AZ.lastIndex)
                    var idx = idxOf(down.position.y)
                    touch(idx, topY + down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        idx = idxOf(ch.position.y)
                        touch(idx, topY + ch.position.y)
                        ch.consume()
                    }
                    release(idx)
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AZ.forEachIndexed { i, l ->
            val d = if (activeIdx < 0) 99 else abs(i - activeIdx)
            val pull by animateFloatAsState(if (d < 5) (5 - d) * 10f else 0f, label = "onda")
            val sc = 1f + pull / 40f
            T(
                l, if (l.length == 1 && l[0] in 'A'..'Z') 10f else 13f,
                Modifier.offset(x = (if (left) pull else -pull).dp).graphicsLayer { scaleX = sc; scaleY = sc },
                color = if (d == 0) acc else DIM, weight = if (d == 0) 800 else 500,
                family = if (cfg.theme == "runa" && l[0] in 'A'..'Z') FontFamily.Serif else FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun BoxScope.LetterPanel(
    cfg: Config, apps: List<AppInfo>, letter: String?, dragIdx: Int, fingerY: Float, notifPkgs: Set<String>,
    onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit, onDismiss: () -> Unit,
) {
    val acc = Color(Accent.current(cfg))
    val left = cfg.azSide == "left"
    // toque no vazio volta pra tela inicial
    Box(Modifier.fillMaxSize().background(Color(0x47000000)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() })

    if (dragIdx >= 0) {
        val l = AZ[dragIdx]
        Box(
            Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .padding(start = if (left) 48.dp else 0.dp, end = if (left) 0.dp else 48.dp)
                .offset { IntOffset(0, (fingerY - 32.dp.toPx()).roundToInt()) }
                .size(64.dp).background(acc),
            contentAlignment = Alignment.Center,
        ) {
            T(if (cfg.theme == "runa" && l[0] in 'A'..'Z') runeOf(l) else l, 30f, color = Color(0xFF0A0A0A), weight = 900, shadow = false)
        }
    }

    if (letter != null) {
        val list = apps.filter { it.letter == letter }
        var panelH by remember { mutableIntStateOf(0) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPx = constraints.maxHeight
            val maxH = maxHeight
            Column(
                Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart)
                    .padding(start = if (left) 0.dp else 26.dp, end = if (left) 26.dp else 0.dp)
                    .widthIn(max = 260.dp)
                    .heightIn(max = maxH * 0.8f)
                    .onSizeChanged { panelH = it.height }
                    .offset {
                        val top = 56.dp.toPx()
                        val y = (fingerY - panelH / 2f).coerceIn(top, (maxPx - panelH - top).coerceAtLeast(top))
                        IntOffset(0, y.roundToInt())
                    }
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = if (left) Alignment.End else Alignment.Start,
            ) {
                T(if (cfg.theme == "runa") "${runeOf(letter)} $letter" else letter, 13f, color = acc, weight = 800, family = FontFamily.Monospace, letter = 3f)
                if (list.isEmpty()) T("nenhum app", 15f, color = DIM)
                else list.forEach { AppRow(it, cfg, 1f, it.pkg in notifPkgs, onApp, onLong) }
            }
        }
    }
}

/* =================== BUSCA =================== */

@Composable
fun SearchOverlay(
    cfg: Config, apps: List<AppInfo>, notifPkgs: Set<String>,
    onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit, onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val acc = Color(Accent.current(cfg))
    LaunchedEffect(Unit) { delay(80); try { fr.requestFocus() } catch (e: Exception) { }; kb?.show() }
    val qq = q.trim().lowercase()
    val showHidden = qq == "oculto" || qq == "ᛟ"
    val res = when {
        showHidden -> apps.filter { it.key in cfg.hidden }
        qq.isEmpty() -> emptyList()
        else -> apps.filter { it.key !in cfg.hidden && norm(it.label).contains(norm(qq)) }
    }.take(14)
    val conta = calc(qq)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() }) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicTextField(
                q, { q = it },
                Modifier.fillMaxWidth().focusRequester(fr).border(1.dp, acc).background(Color(0x66000000)).padding(14.dp),
                textStyle = TextStyle(color = INK, fontSize = 20.sp, fontFamily = themeFont(cfg.theme)),
                cursorBrush = SolidColor(acc), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { res.firstOrNull()?.let(onApp) }),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) T("buscar app ou fazer conta…", 20f, color = DIM, shadow = false)
                        inner()
                    }
                },
            )
            if (conta != null) T("= $conta", 34f, color = acc, weight = 700)
            if (showHidden) T("ᛟ APPS OCULTOS", 11f, color = DIM, letter = 2f)
            res.forEach { AppRow(it, cfg, 1f, it.pkg in notifPkgs, onApp, onLong) }
            if (qq.isNotEmpty() && res.isEmpty() && conta == null) T("nada encontrado", 15f, color = DIM)
        }
    }
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

/* =================== MENU DO APP =================== */

@Composable
fun MBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().border(1.dp, Color(0xFF2C322F)).background(Color(0xFF181C1A))
            .clickable(onClick = onClick).padding(14.dp),
    ) { T(label, 15f, shadow = false) }
}

fun nextMode(cur: String?): String? {
    val order: List<String?> = listOf<String?>(null) + ICON_MODES.map { it.first }
    return order[(order.indexOf(cur) + 1) % order.size]
}
fun modeName(m: String?): String = if (m == null) "padrão" else ICON_MODES.firstOrNull { it.first == m }?.second ?: m

fun setAppMode(key: String, mode: String?) = Store.update { c ->
    c.copy(perApp = if (mode == null) c.perApp - key else c.perApp + (key to mode))
}

@Composable
fun BoxScope.AppMenu(app: AppInfo, cfg: Config, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val fav = app.key in cfg.favs
    val hid = app.key in cfg.hidden
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() })
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF101312))
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app, 44f, cfg)
            T(app.label, 20f, weight = 700, shadow = false)
        }
        MBtn(if (fav) "Tirar dos favoritos" else "Pôr nos favoritos") {
            Store.update { c -> c.copy(favs = if (fav) c.favs - app.key else c.favs + app.key) }; onClose()
        }
        if (fav) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val i = cfg.favs.indexOf(app.key)
            MBtn("↑ Subir", Modifier.weight(1f)) { Store.update { c -> c.copy(favs = c.favs.swap(i, i - 1)) } }
            MBtn("↓ Descer", Modifier.weight(1f)) { Store.update { c -> c.copy(favs = c.favs.swap(i, i + 1)) } }
        }
        MBtn(if (hid) "Mostrar de novo" else "Ocultar app ᛟ") {
            Store.update { c -> c.copy(hidden = if (hid) c.hidden - app.key else c.hidden + app.key) }; onClose()
        }
        MBtn("Ícone só deste app: ${modeName(cfg.perApp[app.key])}  (toque pra trocar)") { setAppMode(app.key, nextMode(cfg.perApp[app.key])) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MBtn("Informações", Modifier.weight(1f)) {
                startSafe(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))); onClose()
            }
            MBtn("Desinstalar", Modifier.weight(1f)) {
                startSafe(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))); onClose()
            }
        }
    }
}
