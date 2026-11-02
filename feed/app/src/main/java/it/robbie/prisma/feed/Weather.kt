package it.robbie.prisma.feed

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject

data class WeatherInfo(val temp: Int, val max: Int, val min: Int, val desc: String)

/** Meteo da Open-Meteo (gratuito, senza chiave), con cache di 30 minuti. */
object Weather {
    private var cache: WeatherInfo? = null
    private var cacheTime = 0L
    @Volatile private var loading = false

    fun get(c: Context, done: (WeatherInfo?) -> Unit) {
        val fresh = cache
        if (fresh != null && System.currentTimeMillis() - cacheTime < 30 * 60_000) { done(fresh); return }
        if (fresh != null) done(fresh)
        val loc = lastLocation(c) ?: run { if (fresh == null) done(null); return }
        if (loading) return
        loading = true
        Thread {
            val w = try { fetch(loc.latitude, loc.longitude) } catch (_: Exception) { null }
            loading = false
            if (w != null) { cache = w; cacheTime = System.currentTimeMillis() }
            done(w ?: cache)
        }.start()
    }

    private fun lastLocation(c: Context): Location? {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(LocationManager::class.java) ?: return null
        return try {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun fetch(lat: Double, lon: Double): WeatherInfo {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        return WeatherInfo(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            desc = describe(cur.getInt("weather_code")),
        )
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Sereno"
        1 -> "Prevalentemente sereno"
        2 -> "Parzialmente nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55, 56, 57 -> "Pioviggine"
        61, 63, 65, 66, 67 -> "Pioggia"
        71, 73, 75, 77 -> "Neve"
        80, 81, 82 -> "Rovesci"
        85, 86 -> "Rovesci di neve"
        95, 96, 99 -> "Temporale"
        else -> "—"
    }
}
