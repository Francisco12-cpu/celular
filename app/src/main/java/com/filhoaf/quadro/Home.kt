package com.filhoaf.quadro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/* =================== LISTA ÚNICA: favoritos + todos os apps =================== */

sealed class LI(val key: String) {
    object Gap : LI("gap")
    class Fav(val app: AppInfo) : LI("f:" + app.key)
    class Label(val text: String, val letter: String?, id: Int) : LI("l:$id")
    class App(val app: AppInfo) : LI("a:" + app.key)
}

class Built(val items: List<LI>, val letterIdx: Map<String, Int>, val favCount: Int)

fun buildItems(apps: List<AppInfo>, cfg: Config): Built {
    val hidden = cfg.hidden.toSet()
    val vis = apps.filter { it.key !in hidden }
        .sortedWith(compareBy({ if (it.letter == "#") "~" else it.letter }, { norm(it.label) }))
    val favs = cfg.favs.mapNotNull { k -> vis.firstOrNull { it.key == k } }
    val out = ArrayList<LI>()
    val idx = HashMap<String, Int>()
    out.add(LI.Gap)
    favs.forEach { out.add(LI.Fav(it)) }
    out.add(LI.Label("todos os apps", null, out.size))
    var last = ""
    vis.forEach { a ->
        if (a.letter != last) {
            last = a.letter
            if (!idx.containsKey(last)) idx[last] = out.size
            out.add(LI.Label(last, last, out.size))
        }
        out.add(LI.App(a))
    }
    return Built(out, idx, favs.size)
}

fun rowHeight(cfg: Config): Float {
    val icon = if (cfg.labelMode == "name") 0f else cfg.iconSize + 12f
    return maxOf(icon, cfg.fontSize * 1.75f, 44f)
}

val AZ: List<String> = listOf("◎") + ('A'..'Z').map { it.toString() } + listOf("#", "⚙")

val BLOCK_NAMES = mapOf(
    "clock" to "Relógio", "buddy" to "Amiguinho", "date" to "Data e semana", "treino" to "Treino do dia",
)

/* =================== APP PRINCIPAL =================== */

@Composable
fun QuadroApp(homeTick: Int) {
    val ctx = LocalContext.current
    val cfg = Store.cfg
    val apps = Apps.list
    val notifs by NotifCenter.items.collectAsState()
    val notifPkgs = remember(notifs) { notifs.map { it.pkg }.toSet() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var editing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var barIdx by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var barShown by remember { mutableStateOf(false) }
    var topH by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var holdAt by remember { mutableStateOf(Offset.Zero) }
    var gapOrigin by remember { mutableStateOf(Offset.Zero) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        if (holding) { ring.snapTo(0f); ring.animateTo(1f, tween(1000, easing = LinearEasing)) } else ring.snapTo(0f)
    }

    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    val scrolling = listState.isScrollInProgress

    fun closeAll() { editing = false; settings = false; search = false; picking = false; menuApp = null; barIdx = -1 }
    LaunchedEffect(homeTick) {
        closeAll()
        listState.animateScrollToItem(0)
    }
    BackHandler(enabled = menuApp != null || settings || search || picking || editing || !atTop) {
        when {
            picking -> picking = false
            menuApp != null -> menuApp = null
            settings -> settings = false
            search -> search = false
            editing -> editing = false
            else -> scope.launch { listState.animateScrollToItem(0) }
        }
    }

    // A barra de letras só aparece enquanto você rola ou toca na borda
    LaunchedEffect(scrolling, barIdx) {
        if (scrolling || barIdx >= 0) barShown = true else { delay(1300); barShown = false }
    }
    val barVisible = barShown && (!atTop || barIdx >= 0)

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

    val built = remember(apps, cfg.favs, cfg.hidden) { buildItems(apps, cfg) }

    val onApp: (AppInfo) -> Unit = { a ->
        Apps.launch(ctx, a)
        search = false
        scope.launch { listState.scrollToItem(0) }
    }
    val onLong: (AppInfo) -> Unit = { a ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuApp = a
    }

    fun targetFor(i: Int): Int? {
        for (j in i..27) {
            val l = AZ[j]
            built.letterIdx[l]?.let { return it }
        }
        return null
    }

    val onBarTouch: (Int, Float) -> Unit = { i, y ->
        if (i != barIdx) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        barIdx = i
        fingerY = y
        if (i in 1..27) {
            val target = targetFor(i) ?: built.items.lastIndex
            scope.launch { listState.scrollToItem(target) }
        }
    }
    val onBarRelease: (Int) -> Unit = { i ->
        barIdx = -1
        when (AZ[i]) {
            "◎" -> search = true
            "⚙" -> settings = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val rh = rowHeight(cfg).dp
        val favsH = rh * built.favCount
        val topDp = with(density) { topH.toDp() }
        // espaço vazio no começo da lista: empurra os favoritos pra baixo, sem nunca encostar no relógio
        val minGap = topDp - statusTop + cfg.favGap.dp
        val gapDp: Dp = if (cfg.favAnchor == "top") minGap
        else maxOf(screenH - cfg.favBottom.dp - favsH - statusTop, minGap)
        val gapPx = with(density) { gapDp.toPx() }.coerceAtLeast(1f)
        val progress = remember(gapPx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / gapPx).coerceIn(0f, 1f)
            }
        }
        val left = cfg.azSide == "left"
        val al = cfg.align["favs"] ?: "left"
        val acc = Color(Accent.current(cfg))

        // escurece o fundo conforme a lista sobe, o papel de parede continua aparecendo
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color(0x00000000), Color(0x00000000), Color(0x59000000))),
            ),
        )
        // efeito ao rolar: nenhum, escuro, colorido ou borrado (o borrado depende do celular, Android 12+)
        val scrimCol = if (cfg.scrimMode == "color") Color(cfg.scrimColor) else Color(0xFF0C0B18)
        val scrimMax = when (cfg.scrimMode) { "none" -> 0f; "blur" -> cfg.scrimAmt * 0.35f; "color" -> cfg.scrimAmt * 0.7f; else -> cfg.scrimAmt }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value * scrimMax }.background(scrimCol))
        val activity = ctx as? android.app.Activity
        LaunchedEffect(cfg.scrimMode, cfg.scrimAmt, progress) {
            if (android.os.Build.VERSION.SDK_INT >= 31 && activity != null) {
                if (cfg.scrimMode != "blur") {
                    try { activity.window.setBackgroundBlurRadius(0) } catch (e: Exception) { }
                } else {
                    snapshotFlow { (progress.value * 10).roundToInt() }.collect { st ->
                        try { activity.window.setBackgroundBlurRadius((st / 10f * 80f * cfg.scrimAmt).roundToInt()) } catch (e: Exception) { }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !editing,
            contentPadding = PaddingValues(
                start = if (left) 62.dp else 22.dp, end = if (left) 22.dp else 62.dp,
                top = statusTop, bottom = navBottom + 96.dp,
            ),
        ) {
            items(built.items, key = { it.key }) { item ->
                when (item) {
                    LI.Gap -> Spacer(
                        Modifier.fillMaxWidth().height(gapDp)
                            .onGloballyPositioned { gapOrigin = it.positionInRoot() }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    holdAt = gapOrigin + down.position
                                    holding = true
                                    val timedOut = withTimeoutOrNull(1000L) {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val c = ev.changes.firstOrNull() ?: break
                                            if (!c.pressed || c.isConsumed) break
                                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                        }
                                        false
                                    } == null
                                    holding = false
                                    if (timedOut) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editing = true
                                    }
                                }
                            },
                    )
                    is LI.Fav -> AppRowItem(
                        item.app, cfg, item.app.pkg in notifPkgs, onApp, onLong,
                        Modifier.graphicsLayer { alpha = if (editing) 0f else 1f },
                    )
                    is LI.App -> AppRowItem(
                        item.app, cfg, item.app.pkg in notifPkgs, onApp, onLong,
                        Modifier.graphicsLayer { alpha = (progress.value * 2.5f).coerceIn(0f, 1f) },
                    )
                    is LI.Label -> {
                        val lt: String? = item.letter
                        val txt: String = if (lt == null) item.text.uppercase()
                        else if (cfg.theme == "runa" && lt != "#") runeOf(lt) + "  " + lt
                        else lt
                        Box(
                            Modifier.fillMaxWidth().graphicsLayer { alpha = (progress.value * 2.5f).coerceIn(0f, 1f) }
                                .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 4.dp),
                            contentAlignment = when (al) { "center" -> Alignment.Center; "right" -> Alignment.CenterEnd; else -> Alignment.CenterStart },
                        ) {
                            T(txt, 12f, color = if (lt == null) DIM else acc, weight = 700, face = "mono", letter = 3f)
                        }
                    }
                }
            }
        }

        // relógio e blocos do topo: somem conforme a lista sobe
        // anel que enche enquanto você segura no relógio (1 segundo = edição)
        if (holding && !editing) Canvas(Modifier.fillMaxSize()) {
            val v = ring.value
            if (v > 0.12f) {
                val r = 30.dp.toPx(); val w = 4.dp.toPx()
                drawCircle(Color(0x40FFFFFF), r, holdAt, style = Stroke(w))
                drawArc(acc, -90f, 360f * v, false, Offset(holdAt.x - r, holdAt.y - r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
            }
        }

        TopBlocks(
            cfg, notifs, batt, charging, editing, apps, { picking = true },
            Modifier.onSizeChanged { topH = it.height }
                .graphicsLayer {
                    val p = if (editing) 0f else progress.value
                    val e = 1f - (1f - p) * (1f - p) // desacelera suave
                    alpha = (1f - e * 1.6f).coerceIn(0f, 1f)
                    translationY = -e * 120f
                    val sc = 1f - e * 0.06f
                    scaleX = sc; scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                },
        )

        AlphaBar(cfg, barVisible, barIdx, built.letterIdx.keys, onBarTouch, onBarRelease)
        if (barIdx >= 0) LetterBubble(cfg, AZ[barIdx], fingerY, screenH, statusTop)

        if (search) SearchOverlay(cfg, apps, notifPkgs, onApp, onLong) { search = false }
        if (picking) SearchOverlay(
            cfg, apps.filter { it.key !in cfg.favs }, notifPkgs,
            { a -> Store.update { c -> c.copy(favs = c.favs + a.key) }; picking = false }, { },
            title = "adicionar aos favoritos",
        ) { picking = false }
        if (editing) EditBar(cfg) { editing = false }
        menuApp?.let { a -> AppMenu(a, cfg, onEdit = { menuApp = null; editing = true; scope.launch { listState.scrollToItem(0) } }) { menuApp = null } }
        if (settings) SettingsScreen(
            onClose = { settings = false },
            onEdit = { settings = false; editing = true; scope.launch { listState.scrollToItem(0) } },
        )
    }
}

/* =================== BLOCOS DO TOPO (arrastáveis no modo edição) =================== */

@Composable
fun TopBlocks(
    cfg: Config, notifs: List<Notif>, batt: Int, charging: Boolean, editing: Boolean,
    apps: List<AppInfo>, onAddFav: () -> Unit, modifier: Modifier,
) {
    val left = cfg.azSide == "left"
    val density = LocalDensity.current
    val shown = cfg.order.filter { k ->
        val skip = k == "buddy" && (!cfg.buddyOn || cfg.buddyInline)
        !skip && (k !in cfg.hiddenBlocks || editing)
    }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOff by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { cfg.blockGap.dp.toPx() }
    val acc = Color(Accent.current(cfg))
    Column(
        modifier.fillMaxWidth().statusBarsPadding()
            .then(if (editing) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(start = if (left) 66.dp else 32.dp, end = 26.dp, top = if (editing) 84.dp else 26.dp, bottom = if (editing) 120.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(cfg.blockGap.dp),
    ) {
        for (k in shown) key(k) {
            val hid = k in cfg.hiddenBlocks
            val s = cfg.scale[k] ?: 1f
            val al = when (cfg.align[k]) {
                "center" -> Alignment.CenterHorizontally
                "right" -> Alignment.End
                else -> Alignment.Start
            }
            val dragging = dragKey == k
            Column(
                Modifier.fillMaxWidth()
                    .onSizeChanged { heights[k] = it.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOff else 0f
                        alpha = if (hid) 0.4f else 1f
                        val sc = if (dragging) 1.03f else 1f
                        scaleX = sc; scaleY = sc
                    }
                    .then(
                        if (editing) Modifier
                            .background(if (dragging) Color(0x55000000) else Color(0x33000000), cfg.rr(22f))
                            .border(1.dp, if (dragging) acc else Color(0x66FFFFFF), cfg.rr(22f))
                            .pointerInput(k) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragKey = k; dragOff = 0f },
                                    onDragEnd = { dragKey = null; dragOff = 0f },
                                    onDragCancel = { dragKey = null; dragOff = 0f },
                                    onDrag = { ch, d ->
                                        ch.consume()
                                        dragOff += d.y
                                        val vis = Store.cfg.order.filter { it in shown }
                                        val i = vis.indexOf(k)
                                        if (dragOff > 0 && i < vis.lastIndex) {
                                            val nh = heights[vis[i + 1]] ?: 0
                                            if (dragOff > nh / 2f + gapPx) {
                                                val o = Store.cfg.order
                                                Store.update { c -> c.copy(order = c.order.swap(o.indexOf(k), o.indexOf(vis[i + 1]))) }
                                                dragOff -= nh + gapPx
                                            }
                                        } else if (dragOff < 0 && i > 0) {
                                            val ph = heights[vis[i - 1]] ?: 0
                                            if (-dragOff > ph / 2f + gapPx) {
                                                val o = Store.cfg.order
                                                Store.update { c -> c.copy(order = c.order.swap(o.indexOf(k), o.indexOf(vis[i - 1]))) }
                                                dragOff += ph + gapPx
                                            }
                                        }
                                    },
                                )
                            }
                            .padding(12.dp)
                        else Modifier,
                    ),
                horizontalAlignment = al,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editing) EditTools(k, cfg)
                when (k) {
                    "clock" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.weight(1f, fill = false)) { ClockView(cfg, s, notifs.size) }
                        if (cfg.buddyOn && cfg.buddyInline) BuddyView(cfg, s, notifs, batt, charging, stacked = true)
                    }
                    "buddy" -> BuddyView(cfg, s, notifs, batt, charging, stacked = false)
                    "date" -> DateView(cfg, s)
                    "treino" -> TreinoView(cfg, s)
                }
            }
        }
        if (editing) FavEditor(cfg, apps, onAddFav)
    }
}

@Composable
fun EditTools(k: String, cfg: Config) {
    val acc = Color(Accent.current(cfg))
    val sh = cfg.rr(12f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        T("≡ " + (BLOCK_NAMES[k] ?: k), 12f, color = acc, weight = 700, face = "mono")
        Spacer(Modifier.width(6.dp))
        SqBtn("⇔", Color(0x33FFFFFF), INK, sh) {
            val o = listOf("left", "center", "right")
            Store.update { c -> c.copy(align = c.align + (k to o[(o.indexOf(c.align[k] ?: "left") + 1) % 3])) }
        }
        SqBtn("−", Color(0x33FFFFFF), INK, sh) { Store.update { c -> c.copy(scale = c.scale + (k to ((c.scale[k] ?: 1f) - 0.1f).coerceAtLeast(0.5f))) } }
        SqBtn("+", Color(0x33FFFFFF), INK, sh) { Store.update { c -> c.copy(scale = c.scale + (k to ((c.scale[k] ?: 1f) + 0.1f).coerceAtMost(2.2f))) } }
        SqBtn(if (k in cfg.hiddenBlocks) "◻" else "◼", Color(0x33FFFFFF), INK, sh) {
            Store.update { c -> c.copy(hiddenBlocks = if (k in c.hiddenBlocks) c.hiddenBlocks - k else c.hiddenBlocks + k) }
        }
    }
}

/** Favoritos no modo edição: segure e arraste pra reordenar, − tira, + adiciona. */
@Composable
fun FavEditor(cfg: Config, apps: List<AppInfo>, onAdd: () -> Unit) {
    val acc = Color(Accent.current(cfg))
    val density = LocalDensity.current
    val favs = cfg.favs.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    val rowPx = with(density) { (rowHeight(cfg) + 6f).dp.toPx() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOff by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().background(Color(0x33000000), cfg.rr(22f)).border(1.dp, Color(0x66FFFFFF), cfg.rr(22f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        T("≡ FAVORITOS", 12f, color = acc, weight = 700, face = "mono")
        T("segure um app e arraste pra mudar a ordem", 11f, color = DIM, wrap = true)
        for (a in favs) key(a.key) {
            val dragging = dragKey == a.key
            Row(
                Modifier.fillMaxWidth().height(rowHeight(cfg).dp)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOff else 0f }
                    .background(if (dragging) Color(0x55FFFFFF) else Color(0x14FFFFFF), cfg.rr(16f))
                    .pointerInput(a.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = a.key; dragOff = 0f },
                            onDragEnd = { dragKey = null; dragOff = 0f },
                            onDragCancel = { dragKey = null; dragOff = 0f },
                            onDrag = { ch, d ->
                                ch.consume()
                                dragOff += d.y
                                val f = Store.cfg.favs
                                val i = f.indexOf(a.key)
                                if (dragOff > rowPx / 2 && i < f.lastIndex) {
                                    Store.update { c -> c.copy(favs = c.favs.swap(i, i + 1)) }; dragOff -= rowPx
                                } else if (dragOff < -rowPx / 2 && i > 0) {
                                    Store.update { c -> c.copy(favs = c.favs.swap(i, i - 1)) }; dragOff += rowPx
                                }
                            },
                        )
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                T("≡", 18f, color = DIM)
                if (cfg.labelMode != "name") AppIcon(a, cfg.iconSize, cfg)
                T(if (cfg.lowercase) a.label.lowercase() else a.label, cfg.fontSize, Modifier.weight(1f), weight = cfg.fontWeight, face = themeFace(cfg.theme), ellipsis = true, maxLines = 1)
                SqBtn("−", Color(0x33FFFFFF), INK, cfg.rr(12f)) { Store.update { c -> c.copy(favs = c.favs - a.key) } }
            }
        }
        SqBtn("+ adicionar favorito", acc, Color(0xFF0A0A0A), cfg.rr(16f), Modifier.fillMaxWidth()) { onAdd() }
    }
}

@Composable
fun BoxScope.EditBar(cfg: Config, onDone: () -> Unit) {
    val t = tok(cfg)
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp)
            .card(t, cfg.rr(26f), t.bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T("Editando a tela", 16f, color = t.text, weight = 700, shadow = false)
            T("segure um cartão e arraste · ⇔ alinha · − + tamanho · ◼ esconde", 11f, color = t.muted, wrap = true, shadow = false)
        }
        SqBtn("Pronto", t.acc, t.onAcc, cfg.rr(16f)) { onDone() }
    }
}

@Composable
fun DateView(cfg: Config, s: Float) {
    val c = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4 }
    val d = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(c.time)
    val pre = if (cfg.theme == "runa") "ᛞ " else if (cfg.theme == "tech") "// " else ""
    T("$pre$d · semana ${c.get(Calendar.WEEK_OF_YEAR)}", 14f * s, color = DIM, face = themeFace(cfg.theme), letter = 0.3f)
}

@Composable
fun TreinoView(cfg: Config, s: Float) {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    val t = cfg.treino.getOrElse(day) { "" }.ifBlank { "Descanso" }
    Column {
        T(if (cfg.theme == "runa") "ᛏ TREINO DE HOJE" else "TREINO DE HOJE", 10f * s, color = DIM, weight = 600, letter = 2f)
        T(t, 17f * s, face = themeFace(cfg.theme), weight = 500)
    }
}

/* =================== AMIGUINHO =================== */

val HAPPY = listOf("(^‿^)", "(＾▽＾)", "(•̀ᴗ•́)و", "(¬‿¬)", "ヽ(•‿•)ノ")
val FACES = listOf("(♥‿♥)", "(^o^)/", "(⊙_⊙)", "(ง •̀_•́)ง", "(｡•́︿•̀｡)", "(⌐■_■)", "(☆▽☆)")

@Composable
fun BuddyView(cfg: Config, s: Float, notifs: List<Notif>, batt: Int, charging: Boolean, stacked: Boolean) {
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
    val faceBox: @Composable () -> Unit = {
        Box(
            Modifier.border(1.5.dp, INK, cfg.rr(12f))
                .clip(cfg.rr(12f))
                .clickable(remember { MutableInteractionSource() }, null) { temp = HAPPY.random() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) { T(face, 19f * s, face = "mono") }
    }
    if (stacked) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            faceBox()
            if (msg.isNotEmpty()) T(msg, 11f * s, color = DIM, wrap = true, maxLines = 2, align = TextAlign.Center, modifier = Modifier.widthIn(max = 110.dp))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            faceBox()
            if (msg.isNotEmpty()) T(msg, 13f * s, color = DIM, wrap = true, modifier = Modifier.widthIn(max = 200.dp))
        }
    }
}

/* =================== LINHAS DE APP (a linha inteira é clicável) =================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRowItem(
    app: AppInfo, cfg: Config, dot: Boolean, onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val al = cfg.align["favs"] ?: "left"
    val arr = when (al) { "center" -> Alignment.CenterHorizontally; "right" -> Alignment.End; else -> Alignment.Start }
    var label = if (cfg.lowercase) app.label.lowercase() else app.label
    if (cfg.theme == "tech") label = "> $label"
    val mode = cfg.labelMode
    val acc = Color(Accent.current(cfg))
    val shape = cfg.rr(20f)
    Row(
        modifier.fillMaxWidth().heightIn(min = rowHeight(cfg).dp).clip(shape)
            .background(if (pressed) Color(0x33FFFFFF) else Color.Transparent)
            .combinedClickable(interactionSource = src, indication = null, onClick = { onApp(app) }, onLongClick = { onLong(app) })
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, arr),
    ) {
        if (mode == "icon_name" || mode == "icon") AppIcon(app, cfg.iconSize, cfg)
        if (mode != "icon") T(
            label, cfg.fontSize, Modifier.weight(1f, fill = false), weight = cfg.fontWeight,
            face = themeFace(cfg.theme), ellipsis = true, maxLines = 1,
        )
        if (cfg.theme == "runa" && mode != "icon") T(runeOf(app.label), cfg.fontSize * 0.75f, color = acc, face = "runic")
        if (mode == "name_icon") AppIcon(app, cfg.iconSize, cfg)
        if (dot) Box(Modifier.size(8.dp).clip(cfg.iconShape()).background(acc))
    }
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Float, cfg: Config) {
    val ctx = LocalContext.current
    val mode = cfg.perApp[app.key] ?: cfg.iconMode
    val accent = Accent.current(cfg)
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceIn(48, 192)
    val w2 = Accent.wall2
    val img by produceState<ImageBitmap?>(null, app.key, mode, accent, px, w2) {
        value = IconCache.get(ctx, app, mode, accent, px)
    }
    Box(Modifier.size(sizeDp.dp).clip(cfg.iconShape()).background(Color(0x22FFFFFF))) {
        img?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/* =================== BARRA DE LETRAS =================== */

@Composable
fun BoxScope.AlphaBar(
    cfg: Config, visible: Boolean, activeIdx: Int, present: Set<String>,
    onTouch: (Int, Float) -> Unit, onRelease: (Int) -> Unit,
) {
    val left = cfg.azSide == "left"
    var topY by remember { mutableFloatStateOf(0f) }
    val touch by rememberUpdatedState(onTouch)
    val release by rememberUpdatedState(onRelease)
    val acc = Color(Accent.current(cfg))
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "barra")
    Column(
        Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .systemBarsPadding()
            .fillMaxHeight(0.86f)
            .width(44.dp)
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
            }
            .graphicsLayer { alpha = a },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AZ.forEachIndexed { i, l ->
            val d = if (activeIdx < 0) 99 else abs(i - activeIdx)
            val pull by animateFloatAsState(if (d < 4) (4 - d) * 7f else 0f, label = "onda")
            val sc = 1f + pull / 70f
            val isLetter = l.length == 1 && (l[0] in 'A'..'Z' || l == "#")
            val has = !isLetter || l in present
            T(
                l, 12f,
                Modifier.graphicsLayer {
                    translationX = (if (left) pull else -pull) * density
                    scaleX = sc; scaleY = sc
                    alpha = if (has) 1f else 0.35f
                },
                color = if (d == 0) acc else DIM, weight = if (d == 0) 800 else 500, face = "mono",
            )
        }
    }
}

@Composable
fun BoxScope.LetterBubble(cfg: Config, letter: String, fingerY: Float, screenH: Dp, statusTop: Dp) {
    val acc = Color(Accent.current(cfg))
    val left = cfg.azSide == "left"
    val d = LocalDensity.current
    val size = 50.dp
    val minY = with(d) { (statusTop + 8.dp).toPx() }
    val maxY = with(d) { (screenH - size - 40.dp).toPx() }
    val y = (fingerY - with(d) { (size / 2).toPx() }).coerceIn(minY, maxY.coerceAtLeast(minY))
    Box(
        Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd)
            .padding(start = if (left) 84.dp else 0.dp, end = if (left) 0.dp else 84.dp)
            .offset { IntOffset(0, y.roundToInt()) }
            .size(size).clip(cfg.rr(18f)).background(acc),
        contentAlignment = Alignment.Center,
    ) {
        val isLetter = letter.length == 1 && letter[0] in 'A'..'Z'
        if (cfg.theme == "runa" && isLetter) T(runeOf(letter), 24f, color = Color(0xFF0A0A0A), weight = 900, face = "runic", shadow = false)
        else T(letter, 24f, color = Color(0xFF0A0A0A), weight = 800, face = "mono", shadow = false)
    }
}

/* =================== BUSCA =================== */

@Composable
fun SearchOverlay(
    cfg: Config, apps: List<AppInfo>, notifPkgs: Set<String>,
    onApp: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit, title: String = "", onClose: () -> Unit,
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
        qq.isEmpty() && title.isNotEmpty() -> apps.filter { it.key !in cfg.hidden }
        qq.isEmpty() -> emptyList()
        else -> apps.filter { it.key !in cfg.hidden && norm(it.label).contains(norm(qq)) }
    }.take(if (title.isNotEmpty()) 400 else 12)
    val conta = calc(qq)
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() }) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title.isNotEmpty()) T(title.uppercase(), 11f, color = acc, weight = 700, letter = 2f, face = "mono")
            BasicTextField(
                q, { q = it },
                Modifier.fillMaxWidth().focusRequester(fr).clip(cfg.rr(24f)).border(1.5.dp, acc, cfg.rr(24f))
                    .background(Color(0x66000000)).padding(horizontal = 18.dp, vertical = 16.dp),
                textStyle = TextStyle(color = INK, fontSize = 20.sp),
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
            if (showHidden) T("ᛟ APPS OCULTOS", 11f, color = DIM, letter = 2f, face = "mono")
            Column(Modifier.verticalScroll(rememberScrollState())) {
                res.forEach { AppRowItem(it, cfg, it.pkg in notifPkgs, onApp, onLong) }
            }
            if (qq.isNotEmpty() && res.isEmpty() && conta == null) T("nada encontrado", 15f, color = DIM)
        }
    }
}

/* =================== MENU DO APP =================== */

@Composable
fun MBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cfg = Store.cfg
    val t = tok(cfg)
    val sh = cfg.rr(18f)
    Box(
        modifier.fillMaxWidth().card(t, sh, t.card2).clip(sh).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) { T(label, 15f, color = t.text, shadow = false, wrap = true) }
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
fun BoxScope.AppMenu(app: AppInfo, cfg: Config, onEdit: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = tok(cfg)
    val fav = app.key in cfg.favs
    val hid = app.key in cfg.hidden
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() })
    val topR = (32f * cfg.corner / 50f).dp
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .card(t, androidx.compose.foundation.shape.RoundedCornerShape(topStart = topR, topEnd = topR), t.bg)
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(app, 46f, cfg)
            T(app.label, 20f, color = t.text, weight = 600, shadow = false)
        }
        Spacer(Modifier.height(4.dp))
        MBtn(if (fav) "Tirar dos favoritos" else "Pôr nos favoritos") {
            Store.update { c -> c.copy(favs = if (fav) c.favs - app.key else c.favs + app.key) }; onClose()
        }
        if (fav) MBtn("Editar favoritos (arrastar, tirar, adicionar)") { onEdit() }
        MBtn(if (hid) "Mostrar de novo" else "Ocultar app") {
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
