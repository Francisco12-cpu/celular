package com.filhoaf.quadro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun letterOf(s: String): String {
    val c = norm(s).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c.toString() else "#"
}

data class AppInfo(val key: String, val label: String, val pkg: String, val cls: String) {
    val letter: String = letterOf(label)
}

object Apps {
    var list by mutableStateOf(listOf<AppInfo>())
        private set

    fun load(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            val pm = app.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val l = pm.queryIntentActivities(i, 0)
                .filter { it.activityInfo.packageName != app.packageName }
                .map {
                    val a = it.activityInfo
                    AppInfo("${a.packageName}/${a.name}", it.loadLabel(pm).toString(), a.packageName, a.name)
                }
                .distinctBy { it.key }
                .sortedBy { norm(it.label) }
            Handler(Looper.getMainLooper()).post { list = l }
        }.start()
    }

    fun launch(ctx: Context, a: AppInfo) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.pkg, a.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não deu pra abrir ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }
}

val ICON_MODES = listOf(
    "orig" to "Normal", "black" to "Preto", "black_color" to "Preto + cor",
    "white" to "Branco", "white_color" to "Branco + cor", "grey" to "Cinza", "duo" to "Cor total",
)

/** Ícones quadrados com filtro. Cada ícone é processado uma vez e fica em cache. */
object IconCache {
    private val mem = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun get(ctx: Context, app: AppInfo, mode: String, accent: Int, px: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val k = "${app.key}|$mode|$accent|$px"
            mem[k] ?: try {
                val d = ctx.packageManager.getActivityIcon(ComponentName(app.pkg, app.cls))
                val b = square(d, px)
                if (mode != "orig") tint(b, mode, accent)
                val img = b.asImageBitmap()
                if (mem.size > 500) mem.clear()
                mem[k] = img
                img
            } catch (e: Exception) {
                null
            }
        }

    /** Desenha o ícone ocupando o quadrado inteiro, quinas retas. */
    private fun square(d: Drawable, px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (d is AdaptiveIconDrawable) {
            val o = px / 4
            d.background?.let { it.setBounds(-o, -o, px + o, px + o); it.draw(c) }
            d.foreground?.let { it.setBounds(-o, -o, px + o, px + o); it.draw(c) }
        } else {
            val t = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(t))
            c.drawColor(avgColor(t))
            val inset = (px * 0.14f).toInt()
            c.drawBitmap(t, null, Rect(inset, inset, px - inset, px - inset), null)
        }
        return b
    }

    private fun avgColor(b: Bitmap): Int {
        val p = IntArray(b.width * b.height)
        b.getPixels(p, 0, b.width, 0, 0, b.width, b.height)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (c in p) if (Color.alpha(c) > 200) { r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++ }
        if (n == 0L) return Color.DKGRAY
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun mixC(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun colors(mode: String, acc: Int): Pair<Int, Int> = when (mode) {
        "black" -> 0xFF0B0B0B.toInt() to 0xFFBDBDBD.toInt()
        "grey" -> 0xFF3A3E3C.toInt() to 0xFFF0F0F0.toInt()
        "black_color" -> 0xFF0B0B0B.toInt() to acc
        "white" -> 0xFFF2F2F2.toInt() to 0xFF1C1C1C.toInt()
        "white_color" -> 0xFFF2F2F2.toInt() to mixC(acc, Color.BLACK, 0.25f)
        else -> mixC(acc, Color.BLACK, 0.72f) to mixC(acc, Color.WHITE, 0.65f) // duo
    }

    /**
     * Filtro: descobre a cor do fundo do ícone pelos cantos e mede o quanto cada pixel
     * se afasta dele. Fundo vira a cor 1, desenho vira a cor 2. Funciona com ícone de qualquer cor.
     */
    private fun tint(b: Bitmap, mode: String, accent: Int) {
        val w = b.width; val h = b.height
        val p = IntArray(w * h)
        b.getPixels(p, 0, w, 0, 0, w, h)
        val k = (w * 0.08f).toInt().coerceAtLeast(2)
        var sum = 0f; var n = 0
        for (y in 0 until k) for (x in 0 until k) {
            sum += lum(p[y * w + x]) + lum(p[y * w + (w - 1 - x)]) +
                lum(p[(h - 1 - y) * w + x]) + lum(p[(h - 1 - y) * w + (w - 1 - x)])
            n += 4
        }
        val bg = sum / n
        var maxDev = 0.08f
        for (c in p) { val dv = abs(lum(c) - bg); if (dv > maxDev) maxDev = dv }
        val (lo, hi) = colors(mode, accent)
        for (i in p.indices) {
            var t = (abs(lum(p[i]) - bg) / maxDev).coerceIn(0f, 1f)
            t = t * t * (3 - 2 * t)
            p[i] = mixC(lo, hi, t)
        }
        b.setPixels(p, 0, w, 0, 0, w, h)
    }
}

/* ---------- notificações (para o amiguinho e as bolinhas) ---------- */

data class Notif(val pkg: String, val title: String, val text: String)

object NotifCenter {
    val items = MutableStateFlow<List<Notif>>(emptyList())
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() = push()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = push()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = push()
    override fun onListenerDisconnected() { NotifCenter.items.value = emptyList() }

    private fun push() {
        val arr = try { activeNotifications } catch (e: Exception) { null } ?: return
        NotifCenter.items.value = arr
            .filter { !it.isOngoing && it.packageName != "android" && it.packageName != packageName }
            .map {
                val ex = it.notification.extras
                Notif(
                    it.packageName,
                    ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                    ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                )
            }
    }
}
