package com.filhoaf.quadro

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val TABS = listOf("Visual", "Ícones", "Apps", "Relógio", "Amiguinho", "Tela", "PC", "Sistema")
val PRESETS = listOf(0xFFFF7A1A, 0xFF3DDC84, 0xFFFF3B3B, 0xFF3D8BFF, 0xFFA45CFF, 0xFFFFD23D, 0xFFFF5FA2, 0xFF2EE6D6, 0xFFF3F5F4, 0xFF8A8F8C).map { it.toInt() }
val DIAS = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

fun notifAccess(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true

@Composable
fun SettingsScreen(onClose: () -> Unit, onEdit: () -> Unit) {
    val cfg = Store.cfg
    val t = tok(cfg)
    var tab by remember { mutableStateOf("Visual") }
    CompositionLocalProvider(LocalTok provides t, LocalCfg provides cfg) {
        MaterialTheme(colorScheme = darkColorScheme(primary = t.acc, secondary = t.acc)) {
            Column(
                Modifier.fillMaxSize().background(t.bg).pointerInput(Unit) { detectTapGestures { } }
                    .systemBarsPadding().imePadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).card(t, cfg.rr(18f), t.card).clip(cfg.rr(18f)).clickable { onClose() },
                        contentAlignment = Alignment.Center,
                    ) { T("‹", 28f, color = t.text, shadow = false) }
                    T("Ajustes", 26f, color = t.text, weight = 600, shadow = false)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { TABS.forEach { n -> Pill(n, tab == n) { tab = n } } }
                Spacer(Modifier.height(12.dp))
                if (tab == "Apps") AppsTab(cfg)
                else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (tab) {
                        "Visual" -> VisualTab(cfg)
                        "Ícones" -> IconsTab(cfg)
                        "Relógio" -> ClockTab(cfg)
                        "Amiguinho" -> BuddyTab(cfg)
                        "Tela" -> ScreenTab(cfg, onEdit)
                        "PC" -> PcTab(cfg)
                        "Sistema" -> SystemTab()
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

/* ---------- peças ---------- */

@Composable
fun Pill(label: String, sel: Boolean, onClick: () -> Unit) {
    val t = LocalTok.current
    val sh = LocalCfg.current.rr(22f)
    Box(
        Modifier.clip(sh).background(if (sel) t.acc else t.card2, sh)
            .then(if (!sel && t.lineW > 0.dp) Modifier.border(1.5.dp, t.line, sh) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
    ) { T(label, 14f, color = if (sel) t.onAcc else t.text, weight = if (sel) 700 else 500, shadow = false) }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val cfg = LocalCfg.current
    Column(
        Modifier.fillMaxWidth().card(t, cfg.rr(30f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        T(title.uppercase(), 11f, color = t.muted, weight = 700, letter = 1.6f, shadow = false)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(opts: List<Pair<String, String>>, sel: String, on: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (v, l) -> Pill(l, v == sel) { on(v) } }
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, shown: String, on: (Float) -> Unit) {
    val t = LocalTok.current
    Column {
        Row {
            T(label, 14f, Modifier.weight(1f), color = t.text, shadow = false)
            T(shown, 13f, color = t.muted, shadow = false)
        }
        Slider(
            value = value, onValueChange = on, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = t.acc, activeTrackColor = t.acc, inactiveTrackColor = t.card2),
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, on: (Boolean) -> Unit) {
    val t = LocalTok.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, 15f, Modifier.weight(1f), color = t.text, shadow = false)
        Switch(
            checked = checked, onCheckedChange = on,
            colors = SwitchDefaults.colors(
                checkedThumbColor = t.onAcc, checkedTrackColor = t.acc,
                uncheckedThumbColor = t.muted, uncheckedTrackColor = t.card2, uncheckedBorderColor = t.muted,
            ),
        )
    }
}

@Composable
fun Field(label: String, value: String, on: (String) -> Unit) {
    val t = LocalTok.current
    val sh = LocalCfg.current.rr(18f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) T(label, 12f, color = t.muted, shadow = false, wrap = true)
        BasicTextField(
            value, on,
            Modifier.fillMaxWidth().clip(sh).background(t.card2, sh)
                .then(if (t.lineW > 0.dp) Modifier.border(1.5.dp, t.line, sh) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = TextStyle(color = t.text, fontSize = 15.sp), cursorBrush = SolidColor(t.acc), singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Swatches(sel: Int, allowTheme: Boolean, on: (Int) -> Unit) {
    val t = LocalTok.current
    val sh = LocalCfg.current.rr(14f)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowTheme) Box(
            Modifier.size(40.dp).clip(sh).background(t.card2, sh).border(2.dp, if (sel == 0) t.acc else t.muted, sh).clickable { on(0) },
            contentAlignment = Alignment.Center,
        ) { T("T", 14f, color = t.text, weight = 700, shadow = false) }
        PRESETS.forEach { c ->
            Box(
                Modifier.size(40.dp).clip(sh).background(Color(c), sh)
                    .border(if (sel == c) 3.dp else 0.dp, t.text, sh).clickable { on(c) },
            )
        }
    }
}

@Composable
fun Hint(text: String) = T(text, 12.5f, color = LocalTok.current.muted, shadow = false, lineHeight = 18f, wrap = true)

/* ---------- abas ---------- */

@Composable
fun VisualTab(cfg: Config) {
    val t = LocalTok.current
    Section("Formato") {
        Chips(listOf("0" to "Quadrado", "22" to "Suave", "50" to "Redondo"), cfg.corner.toString()) { v -> Store.update { it.copy(corner = v.toInt()) } }
        SliderRow("Arredondamento", cfg.corner.toFloat(), 0f..50f, shown = "${cfg.corner}%") { v -> Store.update { it.copy(corner = v.roundToInt()) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { i ->
                Box(Modifier.size((30 + i * 6).dp).clip(cfg.iconShape()).background(t.acc.copy(alpha = 0.35f + i * 0.2f)))
            }
        }
        Hint("Vale pra ícones, botões, cartões, quadrado da letra e relógio em blocos.")
    }
    Section("Estilo da tela") {
        Chips(listOf("minimal" to "Minimal", "tech" to "Tecnológico", "runa" to "Rúnico ᚱ"), cfg.theme) { v -> Store.update { it.copy(theme = v) } }
    }
    Section("Painel de ajustes") {
        Chips(listOf("suave" to "Suave (roxo)", "contorno" to "Contorno"), cfg.uiStyle) { v -> Store.update { it.copy(uiStyle = v) } }
    }
    Section("Lista de apps") {
        Chips(
            listOf("icon_name" to "Ícone + nome", "name" to "Só nome", "icon" to "Só ícone", "name_icon" to "Nome + ícone"),
            cfg.labelMode,
        ) { v -> Store.update { it.copy(labelMode = v) } }
        Chips(listOf("left" to "Esquerda", "center" to "Centro", "right" to "Direita"), cfg.align["favs"] ?: "left") { v ->
            Store.update { it.copy(align = it.align + ("favs" to v)) }
        }
        SwitchRow("Letras minúsculas", cfg.lowercase) { v -> Store.update { it.copy(lowercase = v) } }
        SliderRow("Tamanho da letra", cfg.fontSize, 12f..40f, shown = "${cfg.fontSize.roundToInt()}") { v -> Store.update { it.copy(fontSize = v) } }
        SliderRow("Peso da letra", cfg.fontWeight.toFloat(), 100f..900f, steps = 7, shown = "${cfg.fontWeight}") { v ->
            Store.update { it.copy(fontWeight = ((v / 100).roundToInt() * 100).coerceIn(100, 900)) }
        }
        SliderRow("Tamanho do ícone", cfg.iconSize, 18f..72f, shown = "${cfg.iconSize.roundToInt()} dp") { v -> Store.update { it.copy(iconSize = v) } }
        Chips(listOf("top" to "Favoritos logo abaixo do topo", "bottom" to "Favoritos embaixo"), cfg.favAnchor) { v -> Store.update { it.copy(favAnchor = v) } }
        SliderRow("Espaço entre o topo e os favoritos", cfg.favGap, 0f..240f, shown = "${cfg.favGap.roundToInt()} dp") { v -> Store.update { it.copy(favGap = v) } }
        if (cfg.favAnchor == "bottom") SliderRow("Distância da base", cfg.favBottom, 24f..360f, shown = "${cfg.favBottom.roundToInt()} dp") { v -> Store.update { it.copy(favBottom = v) } }
        SliderRow("Espaço entre relógio, data e treino", cfg.blockGap, 0f..48f, shown = "${cfg.blockGap.roundToInt()} dp") { v -> Store.update { it.copy(blockGap = v) } }
        Hint("Arrastar a lista pra cima mostra todos os apps. Segure 1 segundo no relógio pra editar a tela.")
    }
    Section("Efeito ao rolar a lista") {
        Chips(listOf("none" to "Nenhum", "dark" to "Escurecer", "color" to "Colorido", "blur" to "Borrado"), cfg.scrimMode) { v -> Store.update { it.copy(scrimMode = v) } }
        if (cfg.scrimMode != "none") SliderRow("Intensidade", cfg.scrimAmt, 0.05f..1f, shown = "${(cfg.scrimAmt * 100).roundToInt()}%") { v -> Store.update { it.copy(scrimAmt = v) } }
        if (cfg.scrimMode == "color") Swatches(cfg.scrimColor, false) { c -> Store.update { it.copy(scrimColor = c) } }
        Hint(if (cfg.scrimMode == "blur") "O borrado usa o recurso do Android 12 ou mais novo. Se o seu celular não suportar, fica só um leve escurecido." else "Nenhum: o papel de parede fica igual, só o relógio e os favoritos somem.")
    }
    Section("Barra de letras") {
        Chips(listOf("right" to "Direita", "left" to "Esquerda"), cfg.azSide) { v -> Store.update { it.copy(azSide = v) } }
        Hint("Ela só aparece quando você rola a lista ou encosta na borda da tela. ◎ no topo abre a busca e ⚙ embaixo abre estes ajustes.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconsTab(cfg: Config) {
    Section("Estilo dos ícones") {
        Chips(ICON_MODES, cfg.iconMode) { v -> Store.update { it.copy(iconMode = v) } }
    }
    Section("Cor de destaque") {
        Chips(listOf("wall" to "Do papel de parede", "custom" to "Escolhida"), cfg.colorSource) { v -> Store.update { it.copy(colorSource = v) } }
        if (cfg.colorSource == "custom") Swatches(cfg.customColor, false) { c -> Store.update { it.copy(customColor = c) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(cfg.rr(10f)).background(Color(Accent.wall)))
            Hint("cor detectada no seu papel de parede")
        }
    }
    Section("Prévia") {
        val apps = Apps.list
        val prev = (cfg.favs.mapNotNull { k -> apps.firstOrNull { it.key == k } } + apps).distinct().take(10)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            prev.forEach { AppIcon(it, 48f, cfg) }
        }
        Hint("Algum ícone ficou ruim? Segure o app e escolha \"Ícone só deste app\", ou use a aba Apps.")
    }
}

@Composable
fun AppsTab(cfg: Config) {
    val t = LocalTok.current
    var q by remember { mutableStateOf("") }
    val apps = Apps.list.filter { norm(it.label).contains(norm(q)) }
    val sh = cfg.rr(22f)
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Section("Buscar") {
                Field("", q) { q = it }
                Hint("★ favorito · ◐ oculto · o terceiro botão troca o ícone só daquele app")
            }
        }
        items(apps, key = { it.key }) { a ->
            Row(
                Modifier.fillMaxWidth().card(t, sh).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(a, 36f, cfg)
                T(a.label, 15f, Modifier.weight(1f), color = t.text, shadow = false, ellipsis = true, maxLines = 1)
                Pill("★", a.key in cfg.favs) {
                    Store.update { c -> c.copy(favs = if (a.key in c.favs) c.favs - a.key else c.favs + a.key) }
                }
                Pill("◐", a.key in cfg.hidden) {
                    Store.update { c -> c.copy(hidden = if (a.key in c.hidden) c.hidden - a.key else c.hidden + a.key) }
                }
                Pill(modeName(cfg.perApp[a.key]), cfg.perApp[a.key] != null) { setAppMode(a.key, nextMode(cfg.perApp[a.key])) }
            }
        }
    }
}

@Composable
fun ClockTab(cfg: Config) {
    Section("Prévia") {
        Box(Modifier.fillMaxWidth().background(Color(0xFF14121F), cfg.rr(22f)).padding(18.dp)) { ClockView(cfg, 0.7f, 0) }
    }
    Section("Estilo") { Chips(CLOCKS, cfg.clockStyle) { v -> Store.update { it.copy(clockStyle = v) } } }
    Section("Fonte") {
        Chips(
            listOf("tema" to "Do tema", "sans" to "Sans", "mono" to "Mono", "serif" to "Serif", "cursiva" to "Cursiva"),
            cfg.clockFont,
        ) { v -> Store.update { it.copy(clockFont = v) } }
    }
    Section("Ajustes") {
        SliderRow("Tamanho", cfg.clockScale, 0.4f..2f, shown = "${(cfg.clockScale * 100).roundToInt()}%") { v -> Store.update { it.copy(clockScale = v) } }
        SliderRow("Peso", cfg.clockWeight.toFloat(), 100f..900f, steps = 7, shown = "${cfg.clockWeight}") { v ->
            Store.update { it.copy(clockWeight = ((v / 100).roundToInt() * 100).coerceIn(100, 900)) }
        }
    }
    Section("Cor (T = cor do tema)") { Swatches(cfg.clockColor, true) { c -> Store.update { it.copy(clockColor = c) } } }
    Hint("Elástico: fica gigante e encolhe quando chega notificação. Rúnico: a runa do meio muda a cada segundo.")
}

@Composable
fun BuddyTab(cfg: Config) {
    val t = LocalTok.current
    val ctx = LocalContext.current
    Section("Amiguinho") {
        SwitchRow("Ligado", cfg.buddyOn) { v -> Store.update { it.copy(buddyOn = v) } }
        SwitchRow("Na mesma linha do relógio", cfg.buddyInline) { v -> Store.update { it.copy(buddyInline = v) } }
        Field("Nome", cfg.buddyName) { v -> Store.update { it.copy(buddyName = v) } }
        Hint("Sozinho ele já reage: bateria baixa, carregando, madrugada e notificações. Toque nele pra ver ele feliz.")
    }
    Section("Reações por contato") {
        cfg.rules.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().card(t, cfg.rr(20f), t.card2).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T("${r.who}  →  ${r.face}", 15f, color = t.text, shadow = false, face = "mono")
                    T(r.msg, 12f, color = t.muted, shadow = false, wrap = true)
                }
                SqBtn("✕", t.acc, t.onAcc, cfg.rr(14f)) { Store.update { c -> c.copy(rules = c.rules.filterIndexed { j, _ -> j != i }) } }
            }
        }
        var who by remember { mutableStateOf("") }
        var face by remember { mutableStateOf(FACES[0]) }
        var msg by remember { mutableStateOf("{q} mandou mensagem") }
        Field("Nome do contato (como aparece na notificação)", who) { who = it }
        Chips(FACES.map { it to it }, face) { face = it }
        Field("Balãozinho ({q} vira o nome)", msg) { msg = it }
        MBtn("+ Adicionar reação") {
            if (who.isNotBlank()) {
                Store.update { c -> c.copy(rules = c.rules + Rule(who.trim(), face, msg)) }
                who = ""
            }
        }
    }
    Section("Permissão de notificações") {
        Hint(
            if (notifAccess(ctx)) "Liberado ✓ O amiguinho e as bolinhas nos apps já funcionam."
            else "Ainda não liberado. Sem isso o amiguinho não sabe quem mandou mensagem.",
        )
        MBtn("Abrir permissão de notificações") { startSafe(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}

@Composable
fun ScreenTab(cfg: Config, onEdit: () -> Unit) {
    Section("Organizar") {
        MBtn("Editar a tela e os favoritos") { onEdit() }
        Hint("Atalho: segure 1 segundo em cima do relógio.")
        Hint("Os favoritos ficam sempre na parte de baixo. A posição deles é ajustável na aba Visual.")
    }
    Section("Blocos do topo") {
        BLOCK_NAMES.forEach { (k, n) ->
            SwitchRow(n, k !in cfg.hiddenBlocks) { on ->
                Store.update { c -> c.copy(hiddenBlocks = if (on) c.hiddenBlocks - k else c.hiddenBlocks + k) }
            }
        }
    }
    Section("Treino da semana") {
        DIAS.forEachIndexed { i, d ->
            Field(d, cfg.treino.getOrElse(i) { "" }) { v ->
                Store.update { c ->
                    val l = c.treino.toMutableList()
                    while (l.size < 7) l.add("")
                    l[i] = v
                    c.copy(treino = l)
                }
            }
        }
    }
}

@Composable
fun PcTab(cfg: Config) {
    val t = LocalTok.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var status by remember { mutableStateOf("não testado") }
    var checking by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(checking) {
        if (checking) {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pcOnline(cfg) }
            status = if (ok) "conectado ✓" else "sem resposta ✗"
            checking = false
        }
    }
    val cmd = "python recebe_arquivos.py ${cfg.pcKey}"
    val url = "https://github.com/Francisco12-cpu/celular/blob/main/pc/recebe_arquivos.py"
    Section("Status") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).clip(cfg.iconShape()).background(
                when { status.startsWith("conectado") -> Color(0xFF4ADE80); status.startsWith("sem") -> Color(0xFFF87171); else -> t.muted },
            ))
            T("${cfg.pcName}: " + (if (checking) "testando…" else status), 16f, color = t.text, weight = 600, shadow = false)
        }
        MBtn(if (checking) "Testando…" else "Testar conexão agora") { if (!checking) checking = true }
    }
    PcStep(1, "Baixe o programinha no PC", "É um arquivo Python pequeno, sem instalar nada. Abra este endereço no PC e baixe o arquivo:") {
        T(url, 12f, color = t.acc, shadow = false, wrap = true, face = "mono")
        MBtn("Copiar endereço") { clip.setText(androidx.compose.ui.text.AnnotatedString(url)) }
    }
    PcStep(2, "Rode no PC", "No terminal, dentro da pasta do arquivo, digite:") {
        T(cmd, 14f, color = t.text, shadow = false, wrap = true, face = "mono")
        MBtn("Copiar comando") { clip.setText(androidx.compose.ui.text.AnnotatedString(cmd)) }
        Hint("Ele mostra o IP do PC. Se o Windows perguntar do firewall, permita em rede privada.")
    }
    PcStep(3, "Coloque o IP aqui", "Celular e PC precisam estar no mesmo Wi-Fi.") {
        Field("IP do PC", cfg.pcIp) { v -> Store.update { it.copy(pcIp = v.trim()) } }
        Field("Senha (a mesma do comando)", cfg.pcKey) { v -> Store.update { it.copy(pcKey = v) } }
        Field("Nome do PC", cfg.pcName) { v -> Store.update { it.copy(pcName = v) } }
    }
    PcStep(4, "Envie", "Em qualquer app (galeria, arquivos, navegador): Compartilhar → \"Enviar pro PC\". O arquivo cai na pasta Recebidos do PC.") {}
}

@Composable
fun PcStep(n: Int, title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val cfg = LocalCfg.current
    Section("Passo $n") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(cfg.iconShape()).background(t.acc), contentAlignment = Alignment.Center) {
                T("$n", 16f, color = t.onAcc, weight = 800, shadow = false)
            }
            T(title, 17f, color = t.text, weight = 700, shadow = false, wrap = true)
        }
        Hint(text)
        content()
    }
}

@Composable
fun SystemTab() {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Section("Android") {
        MBtn("Escolher QUADRO como tela inicial") { startSafe(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
        MBtn("Trocar papel de parede") { startSafe(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Papel de parede")) }
        MBtn("Configurações do Android") { startSafe(ctx, Intent(Settings.ACTION_SETTINGS)) }
        MBtn("Permissão de notificações") { startSafe(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
    Section("Restaurar") {
        MBtn(if (confirm) "Toque de novo pra apagar tudo" else "Restaurar padrões") {
            if (confirm) { Store.reset(); confirm = false } else confirm = true
        }
    }
    Hint("QUADRO · launcher leve e seu.")
}
