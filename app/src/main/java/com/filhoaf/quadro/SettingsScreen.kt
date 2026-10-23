package com.filhoaf.quadro

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val BG = Color(0xFF0E1110)
private val CARD = Color(0xFF181C1A)
private val LINE = Color(0xFF2C322F)
private val MUT = Color(0xFF8D9690)

val TABS = listOf("Visual", "Ícones", "Apps", "Relógio", "Amiguinho", "Tela", "PC", "Sistema")
val PRESETS = listOf(0xFFFF7A1A, 0xFF3DDC84, 0xFFFF3B3B, 0xFF3D8BFF, 0xFFA45CFF, 0xFFFFD23D, 0xFFFF5FA2, 0xFF2EE6D6, 0xFFF3F5F4, 0xFF8A8F8C).map { it.toInt() }
val DIAS = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

fun notifAccess(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true

@Composable
fun SettingsScreen(onClose: () -> Unit, onEdit: () -> Unit) {
    val cfg = Store.cfg
    val acc = Color(Accent.current(cfg))
    var tab by remember { mutableStateOf("Visual") }
    MaterialTheme(colorScheme = darkColorScheme(primary = acc, secondary = acc)) {
        Column(
            Modifier.fillMaxSize().background(BG).pointerInput(Unit) { detectTapGestures { } }
                .systemBarsPadding().imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Ajustes do QUADRO", 22f, Modifier.weight(1f), weight = 800, shadow = false)
                SqBtn("✕", acc) { onClose() }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { TABS.forEach { t -> Chip(t, tab == t, acc) { tab = t } } }
            Spacer(Modifier.height(10.dp))
            if (tab == "Apps") AppsTab(cfg, acc)
            else Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                when (tab) {
                    "Visual" -> VisualTab(cfg, acc)
                    "Ícones" -> IconsTab(cfg, acc)
                    "Relógio" -> ClockTab(cfg, acc)
                    "Amiguinho" -> BuddyTab(cfg, acc)
                    "Tela" -> ScreenTab(cfg, acc, onEdit)
                    "PC" -> PcTab(cfg, acc)
                    "Sistema" -> SystemTab(acc)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/* ---------- peças ---------- */

@Composable
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(title.uppercase(), 11f, color = MUT, weight = 600, letter = 1.6f, shadow = false)
        content()
    }
}

@Composable
fun Chip(label: String, sel: Boolean, acc: Color, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, if (sel) acc else LINE).background(if (sel) acc.copy(alpha = 0.16f) else CARD)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) { T(label, 13f, color = if (sel) acc else INK, shadow = false) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(opts: List<Pair<String, String>>, sel: String, acc: Color, on: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { (v, l) -> Chip(l, v == sel, acc) { on(v) } }
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, shown: String, on: (Float) -> Unit) {
    Column {
        Row {
            T(label, 14f, Modifier.weight(1f), shadow = false)
            T(shown, 13f, color = MUT, shadow = false)
        }
        Slider(value = value, onValueChange = on, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, on: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, 15f, Modifier.weight(1f), shadow = false)
        Switch(checked = checked, onCheckedChange = on)
    }
}

@Composable
fun Field(label: String, value: String, acc: Color, on: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) T(label, 12f, color = MUT, shadow = false)
        BasicTextField(
            value, on,
            Modifier.fillMaxWidth().border(1.dp, LINE).background(CARD).padding(12.dp),
            textStyle = TextStyle(color = INK, fontSize = 15.sp), cursorBrush = SolidColor(acc), singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Swatches(sel: Int, allowTheme: Boolean, on: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowTheme) Box(
            Modifier.size(38.dp).border(2.dp, if (sel == 0) INK else LINE).background(CARD).clickable { on(0) },
            contentAlignment = Alignment.Center,
        ) { T("T", 14f, weight = 700, shadow = false) }
        PRESETS.forEach { c ->
            Box(Modifier.size(38.dp).border(2.dp, if (sel == c) INK else Color.Transparent).padding(3.dp).background(Color(c)).clickable { on(c) })
        }
    }
}

@Composable
fun Hint(text: String) = T(text, 12.5f, color = MUT, shadow = false, lineHeight = 18f)

/* ---------- abas ---------- */

@Composable
fun VisualTab(cfg: Config, acc: Color) {
    Group("Estilo") {
        Chips(listOf("minimal" to "Minimal", "tech" to "Tecnológico", "runa" to "Rúnico ᚱ"), cfg.theme, acc) { v -> Store.update { it.copy(theme = v) } }
    }
    Group("Nome dos apps") {
        Chips(
            listOf("icon_name" to "Ícone + nome", "name" to "Só nome", "icon" to "Só ícone", "name_icon" to "Nome + ícone"),
            cfg.labelMode, acc,
        ) { v -> Store.update { it.copy(labelMode = v) } }
        SwitchRow("Letras minúsculas", cfg.lowercase) { v -> Store.update { it.copy(lowercase = v) } }
    }
    Group("Tamanhos") {
        SliderRow("Letra", cfg.fontSize, 12f..36f, shown = "${cfg.fontSize.roundToInt()}") { v -> Store.update { it.copy(fontSize = v) } }
        SliderRow("Peso da letra", cfg.fontWeight.toFloat(), 100f..900f, steps = 7, shown = "${cfg.fontWeight}") { v ->
            Store.update { it.copy(fontWeight = ((v / 100).roundToInt() * 100).coerceIn(100, 900)) }
        }
        SliderRow("Ícone", cfg.iconSize, 16f..64f, shown = "${cfg.iconSize.roundToInt()} dp") { v -> Store.update { it.copy(iconSize = v) } }
    }
    Group("Barra de letras") {
        Chips(listOf("right" to "Direita", "left" to "Esquerda"), cfg.azSide, acc) { v -> Store.update { it.copy(azSide = v) } }
        Hint("Deslize o dedo na barra pra ver os apps de cada letra. ◎ no topo abre a busca, ⚙ embaixo abre estes ajustes.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconsTab(cfg: Config, acc: Color) {
    Group("Estilo dos ícones") {
        Chips(ICON_MODES, cfg.iconMode, acc) { v -> Store.update { it.copy(iconMode = v) } }
    }
    Group("Cor de destaque") {
        Chips(listOf("wall" to "Do papel de parede", "custom" to "Escolhida"), cfg.colorSource, acc) { v -> Store.update { it.copy(colorSource = v) } }
        if (cfg.colorSource == "custom") Swatches(cfg.customColor, false) { c -> Store.update { it.copy(customColor = c) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(Color(Accent.wall)))
            Hint("cor detectada no seu papel de parede")
        }
    }
    Group("Prévia") {
        val apps = Apps.list
        val prev = (cfg.favs.mapNotNull { k -> apps.firstOrNull { it.key == k } } + apps).distinct().take(10)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            prev.forEach { AppIcon(it, 46f, cfg) }
        }
        Hint("Algum ícone ficou ruim? Segure o app → \"Ícone só deste app\", ou use a aba Apps.")
    }
}

@Composable
fun AppsTab(cfg: Config, acc: Color) {
    var q by remember { mutableStateOf("") }
    val apps = Apps.list.filter { norm(it.label).contains(norm(q)) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Buscar", q, acc) { q = it }
                Hint("★ favorito · ◐ oculto · o terceiro botão troca o ícone só daquele app")
            }
        }
        items(apps, key = { it.key }) { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(a, 34f, cfg)
                T(a.label, 15f, Modifier.weight(1f), shadow = false)
                Chip("★", a.key in cfg.favs, acc) {
                    Store.update { c -> c.copy(favs = if (a.key in c.favs) c.favs - a.key else c.favs + a.key) }
                }
                Chip("◐", a.key in cfg.hidden, acc) {
                    Store.update { c -> c.copy(hidden = if (a.key in c.hidden) c.hidden - a.key else c.hidden + a.key) }
                }
                Chip(modeName(cfg.perApp[a.key]), cfg.perApp[a.key] != null, acc) { setAppMode(a.key, nextMode(cfg.perApp[a.key])) }
            }
        }
    }
}

@Composable
fun ClockTab(cfg: Config, acc: Color) {
    Group("Prévia") {
        Box(Modifier.fillMaxWidth().background(Color(0xFF1B1F1D)).padding(16.dp)) { ClockView(cfg, 0.7f, 0) }
    }
    Group("Estilo") { Chips(CLOCKS, cfg.clockStyle, acc) { v -> Store.update { it.copy(clockStyle = v) } } }
    Group("Fonte") {
        Chips(
            listOf("tema" to "Do tema", "sans" to "Sans", "mono" to "Mono", "serif" to "Serif", "cursiva" to "Cursiva"),
            cfg.clockFont, acc,
        ) { v -> Store.update { it.copy(clockFont = v) } }
    }
    Group("Ajustes") {
        SliderRow("Tamanho", cfg.clockScale, 0.4f..2f, shown = "${(cfg.clockScale * 100).roundToInt()}%") { v -> Store.update { it.copy(clockScale = v) } }
        SliderRow("Peso", cfg.clockWeight.toFloat(), 100f..900f, steps = 7, shown = "${cfg.clockWeight}") { v ->
            Store.update { it.copy(clockWeight = ((v / 100).roundToInt() * 100).coerceIn(100, 900)) }
        }
    }
    Group("Cor (T = cor do tema)") { Swatches(cfg.clockColor, true) { c -> Store.update { it.copy(clockColor = c) } } }
    Hint("Elástico: fica gigante e encolhe quando chega notificação. Rúnico: a runa do meio muda a cada segundo.")
}

@Composable
fun BuddyTab(cfg: Config, acc: Color) {
    val ctx = LocalContext.current
    Group("Amiguinho") {
        SwitchRow("Ligado", cfg.buddyOn) { v -> Store.update { it.copy(buddyOn = v) } }
        SwitchRow("Na mesma linha do relógio", cfg.buddyInline) { v -> Store.update { it.copy(buddyInline = v) } }
        Field("Nome", cfg.buddyName, acc) { v -> Store.update { it.copy(buddyName = v) } }
        Hint("Sozinho ele já reage: bateria baixa, carregando, madrugada e notificações. Toque nele pra ver ele feliz.")
    }
    Group("Reações por contato") {
        cfg.rules.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, LINE).background(CARD).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T("${r.who}  →  ${r.face}", 15f, shadow = false)
                    T(r.msg, 12f, color = MUT, shadow = false)
                }
                SqBtn("✕", acc) { Store.update { c -> c.copy(rules = c.rules.filterIndexed { j, _ -> j != i }) } }
            }
        }
        var who by remember { mutableStateOf("") }
        var face by remember { mutableStateOf(FACES[0]) }
        var msg by remember { mutableStateOf("{q} mandou mensagem") }
        Field("Nome do contato (como aparece na notificação)", who, acc) { who = it }
        Chips(FACES.map { it to it }, face, acc) { face = it }
        Field("Balãozinho ({q} vira o nome)", msg, acc) { msg = it }
        MBtn("+ Adicionar reação") {
            if (who.isNotBlank()) {
                Store.update { c -> c.copy(rules = c.rules + Rule(who.trim(), face, msg)) }
                who = ""
            }
        }
    }
    Group("Permissão de notificações") {
        Hint(if (notifAccess(ctx)) "Liberado ✓ O amiguinho e as bolinhas nos apps já funcionam."
        else "Ainda não liberado. Sem isso o amiguinho não sabe quem mandou mensagem.")
        MBtn("Abrir permissão de notificações") { startSafe(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}

@Composable
fun ScreenTab(cfg: Config, acc: Color, onEdit: () -> Unit) {
    Group("Organizar") {
        MBtn("Editar a tela (mover, alinhar, tamanho)") { onEdit() }
        Hint("Também dá pra entrar segurando o dedo num espaço vazio da tela inicial.")
    }
    Group("Blocos visíveis") {
        BLOCK_NAMES.filterKeys { it != "spacer" }.forEach { (k, n) ->
            SwitchRow(n, k !in cfg.hiddenBlocks) { on ->
                Store.update { c -> c.copy(hiddenBlocks = if (on) c.hiddenBlocks - k else c.hiddenBlocks + k) }
            }
        }
    }
    Group("Treino da semana") {
        DIAS.forEachIndexed { i, d ->
            Field(d, cfg.treino.getOrElse(i) { "" }, acc) { v ->
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
fun PcTab(cfg: Config, acc: Color) {
    val ctx = LocalContext.current
    Group("Seu PC") {
        Field("Nome", cfg.pcName, acc) { v -> Store.update { it.copy(pcName = v) } }
        Field("IP na rede (o script mostra quando abre)", cfg.pcIp, acc) { v -> Store.update { it.copy(pcIp = v.trim()) } }
        Field("Senha (a mesma do script)", cfg.pcKey, acc) { v -> Store.update { it.copy(pcKey = v) } }
        MBtn("Testar conexão") { pingPc(ctx, cfg) }
    }
    Group("Como usar") {
        Hint("1. No PC: python recebe_arquivos.py ${cfg.pcKey}\n2. Celular e PC no mesmo Wi-Fi.\n3. Em qualquer app (galeria, arquivos, navegador): Compartilhar → \"Enviar pro PC\".\n4. O arquivo cai na pasta Recebidos do PC.\n\nNada fica rodando no celular: a conexão só abre na hora do envio.")
    }
}

@Composable
fun SystemTab(acc: Color) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Group("Android") {
        MBtn("Escolher QUADRO como tela inicial") { startSafe(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
        MBtn("Trocar papel de parede") { startSafe(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Papel de parede")) }
        MBtn("Configurações do Android") { startSafe(ctx, Intent(Settings.ACTION_SETTINGS)) }
        MBtn("Permissão de notificações") { startSafe(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
    Group("Restaurar") {
        MBtn(if (confirm) "Toque de novo pra apagar tudo" else "Restaurar padrões") {
            if (confirm) { Store.reset(); confirm = false } else confirm = true
        }
    }
    Hint("QUADRO · launcher leve, quadrado e seu.")
}
