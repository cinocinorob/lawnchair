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

        text("Tocca l'isola: espande i controlli musicali o apre la notifica.", 13f).gravity = Gravity.START

        setContentView(ScrollView(this).apply { addView(col) })
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
    }
}
