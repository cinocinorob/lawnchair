package it.robbie.prisma.island

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Disattiva i banner (heads-up) di sistema, così le notifiche compaiono solo nell'isola.
 * Serve WRITE_SECURE_SETTINGS, concesso una volta sola tramite Shizuku (resta dopo i riavvii).
 */
object Banners {
    private const val KEY = "heads_up_notifications_enabled"
    const val REQ = 4242

    fun hasSecure(c: Context) =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun systemBannersOn(c: Context) = Settings.Global.getInt(c.contentResolver, KEY, 1) != 0

    fun setSystemBanners(c: Context, on: Boolean): Boolean = try {
        Settings.Global.putInt(c.contentResolver, KEY, if (on) 1 else 0)
    } catch (_: SecurityException) {
        false
    }

    fun shizukuRunning() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun shizukuGranted() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Esegue `pm grant` con i privilegi di Shizuku. Restituisce null se ok, altrimenti l'errore. */
    fun grantViaShizuku(c: Context): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val cmd = arrayOf("pm", "grant", c.packageName, "android.permission.WRITE_SECURE_SETTINGS")
        val p = m.invoke(null, cmd, null, null) as Process
        val code = p.waitFor()
        val err = p.errorStream.bufferedReader().readText().trim()
        if (code == 0 && hasSecure(c)) null else "codice $code ${err.take(200)}"
    } catch (t: Throwable) {
        (t.cause ?: t).toString().take(200)
    }
}
