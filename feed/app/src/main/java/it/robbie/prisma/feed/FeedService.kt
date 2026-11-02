package it.robbie.prisma.feed

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

class FeedService : Service() {
    private var overlay: FeedOverlay? = null

    override fun onBind(intent: Intent?): IBinder {
        val o = overlay ?: FeedOverlay(this).also { overlay = it }
        return o
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlay?.release()
        overlay = null
        return false
    }
}

/** Implementazione del protocollo overlay usato dal launcher per la pagina −1. */
class FeedOverlay(private val ctx: Context) : ILauncherOverlay.Stub() {

    private val main = Handler(Looper.getMainLooper())
    private var cb: ILauncherOverlayCallback? = null
    private var root: FeedView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var progress = 0f
    private var anim: ValueAnimator? = null

    // ---- protocollo ----

    override fun startScroll() {
        main.post {
            anim?.cancel()
            root?.refresh()
        }
    }

    override fun onScroll(p: Float) {
        main.post { setProgress(p, report = false) }
    }

    override fun endScroll() {
        main.post { settle(if (progress > 0.35f) 1f else 0f) }
    }

    override fun windowAttached(lp: WindowManager.LayoutParams?, cb: ILauncherOverlayCallback?, flags: Int) {
        val token = lp?.token
        main.post { attach(token, cb) }
    }

    override fun windowAttached2(bundle: Bundle?, cb: ILauncherOverlayCallback?) {
        val lp = bundle?.getParcelable("layout_params", WindowManager.LayoutParams::class.java)
        val token = lp?.token
        main.post { attach(token, cb) }
    }

    override fun windowDetached(isChangingConfigurations: Boolean) {
        main.post { detach() }
    }

    override fun closeOverlay(flags: Int) {
        main.post { if ((flags and 1) != 0) settle(0f) else { anim?.cancel(); setProgress(0f, true) } }
    }

    override fun openOverlay(flags: Int) {
        main.post { root?.refresh(); settle(1f) }
    }

    override fun onPause() {}
    override fun onResume() {}
    override fun requestVoiceDetection(start: Boolean) {}
    override fun getVoiceSearchLanguage(): String = "it-IT"
    override fun isVoiceDetectionRunning(): Boolean = false
    override fun hasOverlayContent(): Boolean = true
    override fun unusedMethod() {}
    override fun setActivityState(flags: Int) {}
    override fun startSearch(data: ByteArray?, bundle: Bundle?): Boolean = false

    // ---- finestra ----

    private fun attach(token: IBinder?, callback: ILauncherOverlayCallback?) {
        cb = callback
        if (root != null) detach()
        if (token == null) return
        val w = ctx.getSystemService(WindowManager::class.java) ?: return
        val v = FeedView(
            ctx,
            onSwipe = { p -> anim?.cancel(); setProgress(p, true) },
            onSwipeEnd = { settle(if (progress > 0.65f) 1f else 0f) },
            onLaunched = { anim?.cancel(); setProgress(0f, true) },
        )
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.token = token
            gravity = Gravity.START or Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "PrismaFeed"
            windowAnimations = 0
        }
        v.visibility = View.INVISIBLE
        try {
            w.addView(v, p)
            wm = w; root = v; params = p
            setProgress(0f, false)
            cb?.overlayStatusChanged(1)
        } catch (e: Exception) {
            Log.e("PrismaFeed", "attach failed", e)
        }
    }

    private fun detach() {
        anim?.cancel()
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null; params = null
    }

    fun release() {
        main.post { detach(); cb = null }
    }

    private fun setTouchable(t: Boolean) {
        val p = params ?: return
        val v = root ?: return
        val f = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (t) p.flags and f.inv() else p.flags or f
        if (newFlags != p.flags) {
            p.flags = newFlags
            try { wm?.updateViewLayout(v, p) } catch (_: Exception) {}
        }
    }

    private fun setProgress(p: Float, report: Boolean) {
        val v = root ?: return
        progress = p.coerceIn(0f, 1f)
        val w = if (v.width > 0) v.width else ctx.resources.displayMetrics.widthPixels
        v.setOpen(progress, w)
        v.visibility = if (progress > 0f) View.VISIBLE else View.INVISIBLE
        if (progress == 0f) setTouchable(false)
        if (progress >= 0.999f) setTouchable(true)
        if (report) try { cb?.overlayScrollChanged(progress) } catch (_: Exception) {}
    }

    private fun settle(target: Float) {
        anim?.cancel()
        val from = progress
        anim = ValueAnimator.ofFloat(from, target).apply {
            duration = (260 * kotlin.math.abs(target - from)).toLong().coerceAtLeast(80)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setProgress(it.animatedValue as Float, true) }
            start()
        }
    }
}
