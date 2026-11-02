package it.robbie.prisma.island

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.OvershootInterpolator
import kotlin.math.max

class IslandService : AccessibilityService(), IslandBus.Listener {

    private var wm: WindowManager? = null
    private var view: IslandView? = null
    private var params: WindowManager.LayoutParams? = null
    private var anim: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val collapse = Runnable { showDefault(force = true) }

    private var screenW = 0
    private var cx = 0
    private var cy = 0
    private var cutW = 0
    private var cutH = 0
    private var curW = 1
    private var curH = 1
    private var mode = Mode.HIDDEN

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Prefs.get(this@IslandService, Prefs.CHARGING)) showCharging()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        val w = getSystemService(WindowManager::class.java) ?: return
        wm = w
        computeGeometry()
        val v = IslandView(this)
        v.headerH = compactH()
        v.onTap = { handleTap() }
        v.onHold = { openSettings() }
        v.onMediaAction = { mediaAction(it) }
        v.media = IslandBus.media
        view = v

        val p = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "PrismaIsland"
        }
        params = p
        try {
            w.addView(v, p)
        } catch (e: Exception) {
            view = null; return
        }
        IslandBus.add(this)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(power, f, Context.RECEIVER_EXPORTED)
        showDefault(force = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        IslandBus.remove(this)
        try { unregisterReceiver(power) } catch (_: Exception) {}
        anim?.cancel()
        view?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        computeGeometry()
        view?.headerH = compactH()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            goTo(Mode.HIDDEN)
        } else {
            showDefault(force = true)
        }
    }

    private fun computeGeometry() {
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        val disp = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val rect = try {
            disp?.cutout?.boundingRects?.firstOrNull { it.top < dp(100) && it.width() < screenW / 2 }
        } catch (_: Exception) { null }
        if (rect != null && !rect.isEmpty) {
            cx = rect.centerX(); cy = rect.centerY(); cutW = rect.width(); cutH = rect.height()
        } else {
            cx = screenW / 2; cy = dp(20); cutW = dp(22); cutH = dp(22)
        }
    }

    private fun compactH() = max(dp(34), cutH + dp(12))
    private fun topY() = max(0, cy - compactH() / 2 + dp(Prefs.offset(this)))

    private fun sizeFor(m: Mode): Pair<Int, Int> = when (m) {
        Mode.HIDDEN -> 1 to 1
        Mode.IDLE -> max(dp(96), cutW + dp(64)) to compactH()
        Mode.MEDIA_COMPACT -> max(dp(200), cutW + dp(150)) to compactH()
        Mode.CHARGING -> max(dp(210), cutW + dp(160)) to compactH()
        Mode.NOTIF -> (screenW - dp(20)) to (compactH() + dp(58))
        Mode.MEDIA_EXPANDED -> (screenW - dp(20)) to (compactH() + dp(140))
    }

    private fun goTo(target: Mode) {
        val v = view ?: return
        val (tw, th) = sizeFor(target)
        val sw = curW; val sh = curH
        val changedContent = target != mode
        mode = target
        v.mode = target
        anim?.cancel()
        if (target == Mode.HIDDEN) {
            curW = 1; curH = 1; v.visibility = View.GONE; applyLayout(); return
        }
        v.visibility = View.VISIBLE
        if (changedContent) v.contentAlpha = 0f
        val startW = if (sw <= 1) tw / 2 else sw
        val startH = if (sh <= 1) th else sh
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                curW = (startW + (tw - startW) * f).toInt().coerceAtLeast(2)
                curH = (startH + (th - startH) * f).toInt().coerceAtLeast(2)
                if (changedContent) v.contentAlpha = (a.animatedFraction * 1.8f - 0.8f).coerceIn(0f, 1f)
                applyLayout()
            }
            start()
        }
    }

    private fun applyLayout() {
        val v = view ?: return
        val p = params ?: return
        p.width = curW
        p.height = curH
        p.x = (cx - curW / 2).coerceIn(0, max(0, screenW - curW))
        p.y = topY()
        try { wm?.updateViewLayout(v, p) } catch (_: Exception) {}
        v.invalidate()
    }

    private fun showDefault(force: Boolean = false) {
        handler.removeCallbacks(collapse)
        val m = IslandBus.media
        val target = when {
            m != null && m.playing && Prefs.get(this, Prefs.MEDIA) -> Mode.MEDIA_COMPACT
            Prefs.get(this, Prefs.IDLE) -> Mode.IDLE
            else -> Mode.HIDDEN
        }
        if (force || target != mode) goTo(target)
    }

    private fun isLandscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun showTransient(m: Mode, ms: Long) {
        if (isLandscape()) return
        handler.removeCallbacks(collapse)
        goTo(m)
        view?.invalidate()
        handler.postDelayed(collapse, ms)
    }

    private fun showCharging() {
        val bm = getSystemService(BatteryManager::class.java)
        view?.battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        showTransient(Mode.CHARGING, 3000)
    }

    private fun handleTap() {
        when (mode) {
            Mode.NOTIF -> {
                val pi = view?.notif?.intent
                if (pi != null) {
                    try {
                        val opts = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        pi.send(this, 0, null, null, null, null, opts.toBundle())
                    } catch (_: Exception) {
                    }
                }
                showDefault(force = true)
            }
            Mode.MEDIA_COMPACT, Mode.IDLE -> {
                if (IslandBus.media != null && Prefs.get(this, Prefs.MEDIA)) {
                    showTransient(Mode.MEDIA_EXPANDED, 6000)
                }
            }
            Mode.MEDIA_EXPANDED -> showDefault(force = true)
            else -> showDefault(force = true)
        }
    }

    private fun mediaAction(i: Int) {
        val tc = IslandBus.controller?.transportControls ?: return
        when (i) {
            0 -> tc.skipToPrevious()
            1 -> if (IslandBus.media?.playing == true) tc.pause() else tc.play()
            2 -> tc.skipToNext()
        }
        handler.removeCallbacks(collapse)
        handler.postDelayed(collapse, 6000)
    }

    private fun openSettings() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (_: Exception) {}
    }

    override fun onMedia(info: MediaInfo?) {
        view?.media = info
        when (mode) {
            Mode.HIDDEN, Mode.IDLE, Mode.MEDIA_COMPACT -> if (!isLandscape()) showDefault()
            Mode.MEDIA_EXPANDED -> if (info == null) showDefault(force = true) else view?.invalidate()
            else -> {}
        }
    }

    override fun onNotification(info: NotifInfo) {
        if (!Prefs.get(this, Prefs.NOTIFS)) return
        view?.notif = info
        showTransient(Mode.NOTIF, 4500)
    }

    override fun onPrefsChanged() {
        if (!isLandscape()) showDefault(force = true)
    }
}
