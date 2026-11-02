package it.robbie.prisma.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Mode { HIDDEN, IDLE, MEDIA_COMPACT, MEDIA_EXPANDED, NOTIF, CHARGING }

@SuppressLint("ViewConstructor")
class IslandView(ctx: Context) : View(ctx) {

    var mode = Mode.IDLE
    var media: MediaInfo? = null
    var notif: NotifInfo? = null
    var battery = 0
    var contentAlpha = 1f
    /** Altezza della "testa" della pillola (zona fotocamera): il contenuto esteso va sotto. */
    var headerH = 0

    var onTap: (() -> Unit)? = null
    var onHold: (() -> Unit)? = null
    var onMediaAction: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34C759.toInt() }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(15f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bodyP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textSize = dp(13.5f)
    }

    private val rect = RectF()
    private val tmp = RectF()
    private val path = Path()
    private val buttons = Array(3) { RectF() }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (mode == Mode.MEDIA_EXPANDED) {
                for (i in buttons.indices) {
                    val b = RectF(buttons[i]); b.inset(-dp(10f), -dp(10f))
                    if (b.contains(e.x, e.y)) { onMediaAction?.invoke(i); return true }
                }
            }
            onTap?.invoke(); return true
        }
        override fun onLongPress(e: MotionEvent) { onHold?.invoke() }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w < 2 || h < 2 || mode == Mode.HIDDEN) return
        rect.set(0f, 0f, w, h)
        val r = min(h / 2f, dp(30f))
        canvas.drawRoundRect(rect, r, r, bg)

        val a = (contentAlpha * 255).toInt().coerceIn(0, 255)
        if (a == 0) return
        val save = canvas.saveLayerAlpha(0f, 0f, w, h, a)
        when (mode) {
            Mode.MEDIA_COMPACT -> drawMediaCompact(canvas, w, h)
            Mode.MEDIA_EXPANDED -> drawMediaExpanded(canvas, w, h)
            Mode.NOTIF -> drawNotif(canvas, w, h)
            Mode.CHARGING -> drawCharging(canvas, w, h)
            else -> {}
        }
        canvas.restoreToCount(save)
    }

    private fun drawArt(c: Canvas, x: Float, y: Float, size: Float, radius: Float) {
        tmp.set(x, y, x + size, y + size)
        val art = media?.art
        if (art == null) {
            c.drawRoundRect(tmp, radius, radius, dim)
            return
        }
        path.reset(); path.addRoundRect(tmp, radius, radius, Path.Direction.CW)
        c.save(); c.clipPath(path); c.drawBitmap(art, null, tmp, bmp); c.restore()
    }

    private fun drawBars(c: Canvas, right: Float, cy: Float, h: Float, animate: Boolean) {
        val t = SystemClock.uptimeMillis() / 160.0
        val bw = dp(3f); val gap = dp(2.5f)
        for (i in 0 until 4) {
            val amp = if (animate) (0.35 + 0.65 * abs(sin(t + i * 1.3))).toFloat() else 0.3f
            val bh = h * amp
            val x = right - (4 - i) * (bw + gap)
            tmp.set(x, cy - bh / 2, x + bw, cy + bh / 2)
            c.drawRoundRect(tmp, bw / 2, bw / 2, fg)
        }
        if (animate) postInvalidateDelayed(60)
    }

    private fun drawMediaCompact(c: Canvas, w: Float, h: Float) {
        val pad = dp(6f)
        val s = h - 2 * pad
        drawArt(c, pad + dp(2f), pad, s, s * 0.3f)
        drawBars(c, w - dp(12f), h / 2, s * 0.7f, media?.playing == true)
    }

    private fun drawMediaExpanded(c: Canvas, w: Float, h: Float) {
        val m = media
        val pad = dp(18f)
        val top = max(headerH.toFloat(), dp(14f))
        val art = dp(54f)
        drawArt(c, pad, top, art, dp(12f))
        val tx = pad + art + dp(12f)
        val avail = w - tx - pad - dp(30f)
        c.drawText(ell(m?.title ?: "—", titleP, avail), tx, top + dp(22f), titleP)
        c.drawText(ell(m?.artist ?: "", bodyP, avail), tx, top + dp(44f), bodyP)
        drawBars(c, w - pad, top + dp(18f), dp(16f), m?.playing == true)

        // barra di avanzamento
        val py = top + art + dp(16f)
        tmp.set(pad, py, w - pad, py + dp(4f))
        c.drawRoundRect(tmp, dp(2f), dp(2f), dim)
        if (m != null && m.duration > 0) {
            val f = (m.currentPosition().toFloat() / m.duration).coerceIn(0f, 1f)
            tmp.right = pad + (w - 2 * pad) * f
            c.drawRoundRect(tmp, dp(2f), dp(2f), fg)
        }

        // controlli
        val cy = py + dp(30f)
        val cx = w / 2
        val bs = dp(26f)
        for (i in 0..2) {
            val x = cx + (i - 1) * dp(72f)
            buttons[i].set(x - bs / 2, cy - bs / 2, x + bs / 2, cy + bs / 2)
        }
        drawPrevNext(c, buttons[0], false)
        if (m?.playing == true) drawPause(c, buttons[1]) else drawPlay(c, buttons[1])
        drawPrevNext(c, buttons[2], true)
        if (m?.playing == true) postInvalidateDelayed(500)
    }

    private fun drawPlay(c: Canvas, b: RectF) {
        path.reset()
        path.moveTo(b.left + b.width() * 0.2f, b.top)
        path.lineTo(b.right, b.centerY())
        path.lineTo(b.left + b.width() * 0.2f, b.bottom)
        path.close(); c.drawPath(path, fg)
    }

    private fun drawPause(c: Canvas, b: RectF) {
        val bw = b.width() * 0.3f
        tmp.set(b.left + b.width() * 0.1f, b.top, b.left + b.width() * 0.1f + bw, b.bottom)
        c.drawRoundRect(tmp, dp(2f), dp(2f), fg)
        tmp.offset(b.width() * 0.5f, 0f)
        c.drawRoundRect(tmp, dp(2f), dp(2f), fg)
    }

    private fun drawPrevNext(c: Canvas, b: RectF, next: Boolean) {
        val half = b.width() / 2
        for (k in 0..1) {
            val ox = b.left + k * half * 0.9f
            path.reset()
            if (next) {
                path.moveTo(ox, b.top + b.height() * 0.15f)
                path.lineTo(ox + half, b.centerY())
                path.lineTo(ox, b.bottom - b.height() * 0.15f)
            } else {
                path.moveTo(ox + half, b.top + b.height() * 0.15f)
                path.lineTo(ox, b.centerY())
                path.lineTo(ox + half, b.bottom - b.height() * 0.15f)
            }
            path.close(); c.drawPath(path, fg)
        }
    }

    private fun drawNotif(c: Canvas, w: Float, h: Float) {
        val n = notif ?: return
        val pad = dp(16f)
        val top = max(headerH.toFloat() - dp(2f), dp(10f))
        val isz = dp(38f)
        val iy = top + (h - top - isz) / 2 - dp(2f)
        n.icon?.let {
            it.setBounds(pad.toInt(), iy.toInt(), (pad + isz).toInt(), (iy + isz).toInt())
            it.draw(c)
        }
        val tx = pad + isz + dp(12f)
        val avail = w - tx - pad
        c.drawText(ell(n.title, titleP, avail), tx, iy + dp(16f), titleP)
        c.drawText(ell(n.text.replace('\n', ' '), bodyP, avail), tx, iy + dp(35f), bodyP)
    }

    private fun drawCharging(c: Canvas, w: Float, h: Float) {
        val cy = h / 2
        val x = dp(16f)
        val bh = h * 0.5f
        path.reset()
        path.moveTo(x + bh * 0.55f, cy - bh / 2)
        path.lineTo(x, cy + bh * 0.08f)
        path.lineTo(x + bh * 0.4f, cy + bh * 0.08f)
        path.lineTo(x + bh * 0.25f, cy + bh / 2)
        path.lineTo(x + bh * 0.8f, cy - bh * 0.1f)
        path.lineTo(x + bh * 0.4f, cy - bh * 0.1f)
        path.close()
        c.drawPath(path, green)
        val txt = "$battery%"
        val tp = TextPaint(titleP).apply { color = green.color }
        c.drawText(txt, w - dp(16f) - tp.measureText(txt), cy + tp.textSize * 0.35f, tp)
    }

    private fun ell(s: String, p: TextPaint, avail: Float): String =
        TextUtils.ellipsize(s, p, max(avail, 1f), TextUtils.TruncateAt.END).toString()
}
