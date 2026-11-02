package it.robbie.prisma.feed

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FeedView(
    ctx: Context,
    private val onSwipe: (Float) -> Unit,
    private val onSwipeEnd: () -> Unit,
    private val onLaunched: () -> Unit,
) : FrameLayout(ctx) {

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private fun sys(id: Int) = context.getColor(id)
    private val bgColor = if (dark) sys(android.R.color.system_neutral1_900) else sys(android.R.color.system_neutral1_50)
    private val cardColor = if (dark) sys(android.R.color.system_neutral2_800) else sys(android.R.color.system_accent2_100)
    private val accent = if (dark) sys(android.R.color.system_accent1_200) else sys(android.R.color.system_accent1_600)
    private val textMain = if (dark) Color.WHITE else sys(android.R.color.system_neutral1_900)
    private val textSub = if (dark) sys(android.R.color.system_neutral2_200) else sys(android.R.color.system_neutral2_700)
    private val chipColor = if (dark) sys(android.R.color.system_neutral2_700) else sys(android.R.color.system_accent2_200)

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val main = Handler(Looper.getMainLooper())
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val greeting = text(15f, sub = true)
    private val date = text(30f, bold = true)
    private val weatherTitle = text(28f, bold = true)
    private val weatherSub = text(14f, sub = true)
    private val agendaBody = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val batteryText = text(17f, bold = true)
    private val batterySub = text(13f, sub = true)
    private var torchBtn: TextView? = null
    private var torchOn = false
    private var torchId: String? = null

    private val cam = ctx.getSystemService(CameraManager::class.java)
    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; paintTorch() }
        }
    }

    init {
        setBackgroundColor(bgColor)
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(16), dp(64), dp(16), dp(48))
            addView(col)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        col.addView(greeting)
        col.addView(date, lp(bottom = 16))

        card("Meteo") { c ->
            c.addView(weatherTitle); c.addView(weatherSub)
        }
        card("Prossimi impegni") { c -> c.addView(agendaBody) }
        card("Scorciatoie") { c -> buildShortcuts(c) }
        card("Batteria") { c -> c.addView(batteryText); c.addView(batterySub) }

        try {
            val c = cam
            if (c != null) {
                torchId = c.cameraIdList.firstOrNull {
                    c.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                c.registerTorchCallback(torchCb, main)
            }
        } catch (_: Exception) {}
        refresh()
    }

    override fun onDetachedFromWindow() {
        try { cam?.unregisterTorchCallback(torchCb) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    fun setOpen(p: Float, width: Int) {
        translationX = -width * (1f - p)
    }

    // ---- costruzione UI ----

    private fun text(size: Float, bold: Boolean = false, sub: Boolean = false) = TextView(context).apply {
        textSize = size
        setTextColor(if (sub) textSub else textMain)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun card(title: String, fill: (LinearLayout) -> Unit) {
        val c = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 28)
            setPadding(dp(20), dp(16), dp(20), dp(18))
        }
        c.addView(TextView(context).apply {
            text = title; textSize = 13f; setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }, lp(bottom = 6))
        fill(c)
        col.addView(c, lp(bottom = 12))
    }

    private fun buildShortcuts(c: LinearLayout) {
        val items = listOf<Pair<String, () -> Unit>>(
            "🔦\nTorcia" to { toggleTorch() },
            "📷\nFotocamera" to { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
            "⏰\nSveglie" to { launch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
            "📶\nWi-Fi" to { launch(Intent(Settings.Panel.ACTION_WIFI)) },
            "🔵\nBluetooth" to { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            "🧮\nCalcolatrice" to {
                launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            },
        )
        items.chunked(3).forEach { rowItems ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { (label, action) ->
                val b = TextView(context).apply {
                    text = label; textSize = 13f; gravity = Gravity.CENTER
                    setTextColor(textMain)
                    background = rounded(chipColor, 20)
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                    setOnClickListener { action() }
                }
                if (label.contains("Torcia")) torchBtn = b
                row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
            }
            c.addView(row, lp(top = 6))
        }
        paintTorch()
    }

    private fun paintTorch() {
        torchBtn?.background = rounded(if (torchOn) accent else chipColor, 20)
        torchBtn?.setTextColor(if (torchOn) (if (dark) Color.BLACK else Color.WHITE) else textMain)
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        try { cam?.setTorchMode(id, !torchOn) } catch (_: Exception) {}
    }

    private fun launch(i: Intent) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onLaunched()
        } catch (_: Exception) {}
    }

    // ---- dati ----

    fun refresh() {
        val now = Date()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when (h) {
            in 5..12 -> "Buongiorno"
            in 13..17 -> "Buon pomeriggio"
            else -> "Buonasera"
        }
        date.text = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(now)
            .replaceFirstChar { it.uppercase() }

        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        batteryText.text = "$pct%"
        batterySub.text = if (bm?.isCharging == true) "In carica" else "Non in carica"

        loadAgenda()
        Weather.get(context) { w ->
            main.post {
                if (w == null) {
                    weatherTitle.text = "—"
                    weatherSub.text = if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) "Apri l'app Prisma Pagina per consentire la posizione" else "Meteo non disponibile"
                } else {
                    weatherTitle.text = "${w.temp}° · ${w.desc}"
                    weatherSub.text = "Max ${w.max}° · Min ${w.min}°"
                }
            }
        }
    }

    private fun loadAgenda() {
        agendaBody.removeAllViews()
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            agendaBody.addView(text(14f, sub = true).apply { text = "Apri l'app Prisma Pagina per consentire il calendario" })
            return
        }
        val start = System.currentTimeMillis()
        val end = start + 2L * 24 * 3600 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, end)
        }.build()
        val fmtDay = SimpleDateFormat("EEE HH:mm", Locale.ITALIAN)
        var n = 0
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cur ->
                while (cur.moveToNext() && n < 4) {
                    val title = cur.getString(0) ?: continue
                    val begin = cur.getLong(1)
                    val allDay = cur.getInt(2) == 1
                    val whenTxt = if (allDay) "Tutto il giorno" else fmtDay.format(Date(begin))
                    agendaBody.addView(text(15f).apply { text = title }, lp(top = 6))
                    agendaBody.addView(text(13f, sub = true).apply { text = whenTxt })
                    n++
                }
            }
        } catch (_: Exception) {}
        if (n == 0) agendaBody.addView(text(14f, sub = true).apply { text = "Nessun impegno nelle prossime 48 ore" })
    }

    // ---- swipe a sinistra per chiudere ----

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX; val dy = ev.rawY - downY
                if (dx < -slop && abs(dx) > abs(dy) * 1.5f) { dragging = true; return true }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX; val dy = ev.rawY - downY
                if (!dragging && dx < -slop && abs(dx) > abs(dy) * 1.5f) dragging = true
                if (dragging) {
                    val w = width.coerceAtLeast(1)
                    onSwipe((1f + dx / w).coerceIn(0f, 1f))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; onSwipeEnd() }
            }
        }
        return true
    }
}
