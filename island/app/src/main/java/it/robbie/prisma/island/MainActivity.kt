package it.robbie.prisma.island

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var a11yStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var bannerStatus: TextView
    private lateinit var bannerSwitch: Switch

    private val shizukuListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == Banners.REQ) runOnUiThread {
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) doGrant()
            else bannerStatus.text = "⚠️ Permesso Shizuku negato"
        }
    }

    private fun doGrant() {
        bannerStatus.text = "Concessione in corso…"
        Thread {
            val err = Banners.grantViaShizuku(this)
            runOnUiThread {
                if (err == null) {
                    Prefs.set(this, Prefs.HIDE_BANNERS, true)
                    Banners.setSystemBanners(this, false)
                }
                refreshBanners(err)
            }
        }.start()
    }

    private fun setupBanners() {
        when {
            Banners.hasSecure(this) -> {}
            !Banners.shizukuRunning() -> {
                bannerStatus.text = "⚠️ Shizuku non è attivo: installalo e avvialo con il debug wireless, poi torna qui."
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            }
            !Banners.shizukuGranted() -> rikka.shizuku.Shizuku.requestPermission(Banners.REQ)
            else -> doGrant()
        }
    }

    private fun refreshBanners(err: String? = null) {
        val secure = Banners.hasSecure(this)
        bannerSwitch.isEnabled = secure
        bannerSwitch.setOnCheckedChangeListener(null)
        bannerSwitch.isChecked = secure && !Banners.systemBannersOn(this)
        bannerSwitch.setOnCheckedChangeListener { _, v ->
            Prefs.set(this, Prefs.HIDE_BANNERS, v); Banners.setSystemBanners(this, !v); refreshBanners()
        }
        bannerStatus.text = when {
            err != null -> "⚠️ Errore: $err"
            secure -> "✅ Permesso concesso (resta anche dopo i riavvii)"
            else -> "Serve Shizuku una volta sola per concedere il permesso."
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        fun text(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }.also { col.addView(it) }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; setOnClickListener { onClick() }
        }.also { col.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        fun toggle(s: String, key: String) = Switch(this).apply {
            text = s; textSize = 16f
            isChecked = Prefs.get(this@MainActivity, key)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, v -> Prefs.set(this@MainActivity, key, v) }
        }.also { col.addView(it) }

        text("Prisma Isola", 28f, true)
        text("Dynamic Island per Pixel. Tieni premuta l'isola per tornare qui.", 14f)

        text("1 · Permessi", 18f, true)
        notifStatus = text("")
        button("Consenti accesso alle notifiche") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        a11yStatus = text("")
        button("Attiva l'isola in Accessibilità") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        text(
            "Se l'interruttore in Accessibilità è grigio: apri Info app (pulsante sotto) › menu ⋮ in alto a destra › " +
                "\"Consenti impostazioni con restrizioni\", poi riprova.", 13f
        )
        button("Apri Info app") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        text("2 · Cosa mostrare", 18f, true)
        toggle("Musica e media", Prefs.MEDIA)
        toggle("Notifiche", Prefs.NOTIFS)
        toggle("Ricarica", Prefs.CHARGING)
        toggle("Pillola sempre visibile attorno alla fotocamera", Prefs.IDLE)

        text("3 · Posizione verticale", 18f, true)
        val off = text("")
        fun label(v: Int) { off.text = "Spostamento: ${if (v > 0) "+" else ""}$v dp" }
        label(Prefs.offset(this))
        SeekBar(this).apply {
            max = 40
            progress = Prefs.offset(this@MainActivity) + 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    label(p - 10)
                    if (fromUser) Prefs.setOffset(this@MainActivity, p - 10)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }.also { col.addView(it) }

        text("4 · Notifiche solo nell'isola", 18f, true)
        bannerStatus = text("")
        button("Concedi permesso con Shizuku") { setupBanners() }
        bannerSwitch = Switch(this).apply {
            text = "Nascondi i banner di sistema"; textSize = 16f
            setPadding(0, dp(10), 0, dp(10))
        }.also { col.addView(it) }
        text("Se disattivi l'isola, rimetti questo interruttore su spento per riavere i banner di Android.", 13f)

        text("Tocca l'isola: espande i controlli musicali o apre la notifica.", 13f).gravity = Gravity.START

        setContentView(ScrollView(this).apply { addView(col) })
        try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        val nOk = nm?.isNotificationListenerAccessGranted(
            ComponentName(this, IslandNotificationListener::class.java)
        ) == true
        notifStatus.text = if (nOk) "✅ Accesso notifiche attivo" else "⚠️ Accesso notifiche non attivo"

        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val me = ComponentName(this, IslandService::class.java)
        val aOk = enabled.split(':').any {
            ComponentName.unflattenFromString(it) == me
        }
        a11yStatus.text = if (aOk) "✅ Isola attiva" else "⚠️ Isola non attiva in Accessibilità"
        refreshBanners()
    }
}
