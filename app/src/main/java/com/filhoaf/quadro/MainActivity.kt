package com.filhoaf.quadro

import android.app.Activity
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private var homeTick by mutableIntStateOf(0)
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = Apps.load(c)
    }
    private var colorsListener: WallpaperManager.OnColorsChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Store.init(this)
        Apps.load(this)
        Accent.refresh(this)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)

        val l = WallpaperManager.OnColorsChangedListener { _, _ -> Accent.refresh(this) }
        colorsListener = l
        WallpaperManager.getInstance(this).addOnColorsChangedListener(l, Handler(Looper.getMainLooper()))

        setContent { QuadroApp(homeTick) }
    }

    // Apertou o botão home já estando no launcher: fecha tudo e volta pro início
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++
    }

    override fun onResume() {
        super.onResume()
        Apps.load(this)
        Accent.refresh(this)
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        colorsListener?.let { WallpaperManager.getInstance(this).removeOnColorsChangedListener(it) }
        super.onDestroy()
    }
}

/** Aparece no menu "Compartilhar" de qualquer app: manda o arquivo pro PC pela rede de casa. */
class SendToPcActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val cfg = Store.cfg

        @Suppress("DEPRECATION")
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        Toast.makeText(this, "Enviando pro ${cfg.pcName}…", Toast.LENGTH_SHORT).show()

        Thread {
            var ok = 0
            var total = 0
            for (u in uris) {
                total++
                val tmp = File(cacheDir, "envio.tmp")
                try {
                    contentResolver.openInputStream(u)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                    if (send(cfg, nameOf(u), tmp)) ok++
                } catch (e: Exception) { }
                tmp.delete()
            }
            if (uris.isEmpty() && !text.isNullOrBlank()) {
                total++
                val tmp = File(cacheDir, "texto.tmp")
                tmp.writeText(text)
                if (send(cfg, "texto_${System.currentTimeMillis() / 1000}.txt", tmp)) ok++
                tmp.delete()
            }
            runOnUiThread {
                val msg = if (total > 0 && ok == total) "Enviado pro ${cfg.pcName} ✓"
                else "Falhou ($ok de $total). O PC está ligado com o script rodando no mesmo Wi-Fi?"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun nameOf(u: Uri): String {
        try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        } catch (e: Exception) { }
        return u.lastPathSegment ?: "arquivo"
    }

    private fun send(cfg: Config, name: String, f: File): Boolean = try {
        val con = URL("http://${cfg.pcIp}:5757/").openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.doOutput = true
        con.connectTimeout = 4000
        con.readTimeout = 30000
        con.setRequestProperty("X-Nome", URLEncoder.encode(name, "UTF-8"))
        con.setRequestProperty("X-Senha", cfg.pcKey)
        con.setFixedLengthStreamingMode(f.length())
        f.inputStream().use { inp -> con.outputStream.use { inp.copyTo(it, 64 * 1024) } }
        val code = con.responseCode
        con.disconnect()
        code == 200
    } catch (e: Exception) {
        false
    }
}

/** Testa se o PC responde. Usado no botão "Testar conexão". */
fun pingPc(ctx: Context, cfg: Config) {
    Thread {
        val ok = try {
            val con = URL("http://${cfg.pcIp}:5757/").openConnection() as HttpURLConnection
            con.connectTimeout = 3000
            con.readTimeout = 3000
            val c = con.responseCode
            con.disconnect()
            c == 200
        } catch (e: Exception) { false }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, if (ok) "${cfg.pcName} respondeu ✓" else "Sem resposta de ${cfg.pcIp}", Toast.LENGTH_LONG).show()
        }
    }.start()
}
