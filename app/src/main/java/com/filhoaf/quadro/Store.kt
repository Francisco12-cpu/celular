package com.filhoaf.quadro

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Rule(val who: String, val face: String, val msg: String)

/** Blocos que ficam no topo da tela inicial. A lista de favoritos é sempre a parte de baixo. */
val TOP_BLOCKS = listOf("clock", "buddy", "date", "treino")

const val CFG_VERSION = 2

data class Config(
    // visual
    val theme: String = "minimal",          // minimal | tech | runa
    val labelMode: String = "icon_name",    // icon_name | name | icon | name_icon
    val fontSize: Float = 26f,
    val fontWeight: Int = 300,
    val lowercase: Boolean = true,
    val iconSize: Float = 40f,
    val azSide: String = "right",
    val corner: Int = 22,                    // 0 = quadrado, 50 = redondo
    val uiStyle: String = "suave",           // suave | contorno (painéis de ajustes)
    val favBottom: Float = 120f,             // distância dos favoritos até o fim da tela (dp)
    // ícones
    val iconMode: String = "orig",
    val colorSource: String = "wall",        // wall | custom
    val customColor: Int = 0xFFFF7A1A.toInt(),
    val perApp: Map<String, String> = emptyMap(),
    // apps
    val favs: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    // relógio
    val clockStyle: String = "digital",
    val clockFont: String = "tema",
    val clockScale: Float = 1f,
    val clockWeight: Int = 500,
    val clockColor: Int = 0,                 // 0 = cor do tema
    // amiguinho
    val buddyOn: Boolean = true,
    val buddyInline: Boolean = true,
    val buddyName: String = "Bit",
    val rules: List<Rule> = listOf(Rule("Mãe", "(♥‿♥)", "{q} mandou mensagem ♥")),
    // tela
    val order: List<String> = TOP_BLOCKS,
    val hiddenBlocks: List<String> = emptyList(),
    val align: Map<String, String> = emptyMap(),
    val scale: Map<String, Float> = emptyMap(),
    val treino: List<String> = listOf("Descanso", "Peito + tríceps", "Costas + bíceps", "Perna", "Ombro + abdômen", "Braço completo", "Cardio leve"),
    // PC
    val pcName: String = "C.O.R.E.",
    val pcIp: String = "192.168.0.10",
    val pcKey: String = "quadro",
    val firstRun: Boolean = true,
)

object Store {
    private var sp: SharedPreferences? = null
    var cfg by mutableStateOf(Config())
        private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("quadro", Context.MODE_PRIVATE)
        sp = p
        cfg = try {
            p.getString("cfg", null)?.let { fromJson(it) } ?: Config()
        } catch (e: Exception) {
            Config()
        }
    }

    fun update(f: (Config) -> Config) {
        cfg = f(cfg)
        sp?.edit()?.putString("cfg", toJson(cfg))?.apply()
    }

    fun reset() = update { Config(firstRun = true) }

    fun export(): String = toJson(cfg)

    private fun toJson(c: Config): String = JSONObject().apply {
        put("ver", CFG_VERSION)
        put("theme", c.theme); put("labelMode", c.labelMode); put("fontSize", c.fontSize.toDouble())
        put("fontWeight", c.fontWeight); put("lowercase", c.lowercase); put("iconSize", c.iconSize.toDouble())
        put("azSide", c.azSide); put("corner", c.corner); put("uiStyle", c.uiStyle)
        put("favBottom", c.favBottom.toDouble())
        put("iconMode", c.iconMode); put("colorSource", c.colorSource)
        put("customColor", c.customColor); put("perApp", JSONObject(c.perApp as Map<*, *>))
        put("favs", JSONArray(c.favs)); put("hidden", JSONArray(c.hidden))
        put("clockStyle", c.clockStyle); put("clockFont", c.clockFont); put("clockScale", c.clockScale.toDouble())
        put("clockWeight", c.clockWeight); put("clockColor", c.clockColor)
        put("buddyOn", c.buddyOn); put("buddyInline", c.buddyInline); put("buddyName", c.buddyName)
        put("rules", JSONArray().apply {
            c.rules.forEach { r -> put(JSONObject().put("who", r.who).put("face", r.face).put("msg", r.msg)) }
        })
        put("order", JSONArray(c.order)); put("hiddenBlocks", JSONArray(c.hiddenBlocks))
        put("align", JSONObject(c.align as Map<*, *>))
        put("scale", JSONObject(c.scale.mapValues { it.value.toDouble() } as Map<*, *>))
        put("treino", JSONArray(c.treino))
        put("pcName", c.pcName); put("pcIp", c.pcIp); put("pcKey", c.pcKey); put("firstRun", c.firstRun)
    }.toString()

    private fun fromJson(s: String): Config {
        val o = JSONObject(s)
        val d = Config()
        val old = o.optInt("ver", 1) < CFG_VERSION
        fun str(k: String, def: String) = o.optString(k, def)
        fun flt(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
        fun list(k: String, def: List<String>): List<String> =
            o.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: def
        fun smap(k: String): Map<String, String> =
            o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
        fun fmap(k: String): Map<String, Float> =
            o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getDouble(it).toFloat() } } ?: emptyMap()
        val rules = o.optJSONArray("rules")?.let { a ->
            List(a.length()) { i ->
                val r = a.getJSONObject(i)
                Rule(r.optString("who"), r.optString("face"), r.optString("msg"))
            }
        } ?: d.rules
        val order = list("order", d.order).filter { it in TOP_BLOCKS }.distinct()
        return Config(
            theme = str("theme", d.theme), labelMode = str("labelMode", d.labelMode),
            // versão 1 tinha tamanhos pequenos demais: pega os novos padrões
            fontSize = if (old) d.fontSize else flt("fontSize", d.fontSize),
            fontWeight = if (old) d.fontWeight else o.optInt("fontWeight", d.fontWeight),
            lowercase = o.optBoolean("lowercase", d.lowercase),
            iconSize = if (old) d.iconSize else flt("iconSize", d.iconSize),
            azSide = str("azSide", d.azSide), corner = o.optInt("corner", d.corner).coerceIn(0, 50),
            uiStyle = str("uiStyle", d.uiStyle), favBottom = flt("favBottom", d.favBottom),
            iconMode = str("iconMode", d.iconMode),
            colorSource = str("colorSource", d.colorSource), customColor = o.optInt("customColor", d.customColor),
            perApp = smap("perApp"), favs = list("favs", d.favs), hidden = list("hidden", d.hidden),
            clockStyle = str("clockStyle", d.clockStyle), clockFont = str("clockFont", d.clockFont),
            clockScale = flt("clockScale", d.clockScale), clockWeight = o.optInt("clockWeight", d.clockWeight),
            clockColor = o.optInt("clockColor", d.clockColor),
            buddyOn = o.optBoolean("buddyOn", d.buddyOn), buddyInline = o.optBoolean("buddyInline", d.buddyInline),
            buddyName = str("buddyName", d.buddyName), rules = rules,
            order = order + TOP_BLOCKS.filter { it !in order },
            hiddenBlocks = list("hiddenBlocks", d.hiddenBlocks).filter { it in TOP_BLOCKS },
            align = smap("align"), scale = fmap("scale"),
            treino = list("treino", d.treino), pcName = str("pcName", d.pcName), pcIp = str("pcIp", d.pcIp),
            pcKey = str("pcKey", d.pcKey), firstRun = o.optBoolean("firstRun", d.firstRun),
        )
    }
}

/** Cor de destaque: detectada do papel de parede ou escolhida. */
object Accent {
    var wall by mutableIntStateOf(0xFF7FD1A0.toInt())

    fun refresh(ctx: Context) {
        try {
            val c = WallpaperManager.getInstance(ctx)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            if (c != null) wall = vivid(c)
        } catch (e: Exception) { }
    }

    private fun vivid(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.08f) return 0xFFD6D9D7.toInt() // papel de parede sem cor: cinza claro
        hsv[1] = hsv[1].coerceAtLeast(0.55f)
        hsv[2] = hsv[2].coerceAtLeast(0.85f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    fun current(cfg: Config): Int = if (cfg.colorSource == "wall") wall else cfg.customColor
}
