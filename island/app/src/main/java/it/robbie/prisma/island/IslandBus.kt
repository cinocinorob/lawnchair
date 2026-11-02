package it.robbie.prisma.island

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val positionTime: Long,
    val speed: Float,
    val pkg: String,
) {
    fun currentPosition(): Long {
        if (!playing || positionTime <= 0) return position
        val p = position + ((SystemClock.elapsedRealtime() - positionTime) * speed).toLong()
        return if (duration > 0) p.coerceIn(0, duration) else p
    }
}

data class NotifInfo(
    val key: String,
    val pkg: String,
    val icon: Drawable?,
    val title: String,
    val text: String,
    val intent: PendingIntent?,
)

/** Canale in-process tra il listener delle notifiche e l'overlay. */
object IslandBus {
    interface Listener {
        fun onMedia(info: MediaInfo?) {}
        fun onNotification(info: NotifInfo) {}
        fun onPrefsChanged() {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<Listener>()

    var media: MediaInfo? = null
        private set
    var controller: MediaController? = null

    fun add(l: Listener) { listeners.add(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun postMedia(info: MediaInfo?) {
        main.post {
            media = info
            listeners.toList().forEach { it.onMedia(info) }
        }
    }

    fun postNotification(info: NotifInfo) {
        main.post { listeners.toList().forEach { it.onNotification(info) } }
    }

    fun prefsChanged() {
        main.post { listeners.toList().forEach { it.onPrefsChanged() } }
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("island", Context.MODE_PRIVATE)
    fun get(c: Context, key: String, def: Boolean = true) = sp(c).getBoolean(key, def)
    fun set(c: Context, key: String, v: Boolean) {
        sp(c).edit().putBoolean(key, v).apply(); IslandBus.prefsChanged()
    }
    fun offset(c: Context) = sp(c).getInt("offset", 0)
    fun setOffset(c: Context, v: Int) {
        sp(c).edit().putInt("offset", v).apply(); IslandBus.prefsChanged()
    }

    const val MEDIA = "media"
    const val NOTIFS = "notifs"
    const val CHARGING = "charging"
    const val IDLE = "idle"
    const val HIDE_BANNERS = "hide_banners"
}
