package it.robbie.prisma.feed

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        col.addView(TextView(this).apply {
            text = "Prisma Pagina"; textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(12))
            text = "È la pagina a sinistra della home di Prisma, con meteo, impegni, scorciatoie e batteria.\n\n" +
                "Per vederla: dalla prima schermata della home, scorri verso destra. Per chiuderla scorri verso sinistra."
        })
        status = TextView(this).apply { textSize = 15f; setPadding(0, dp(8), 0, dp(8)) }
        col.addView(status)
        col.addView(Button(this).apply {
            text = "Consenti posizione e calendario"
            setOnClickListener {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_CALENDAR), 1
                )
            }
        })
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        update()
    }

    private fun update() {
        fun ok(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        status.text = (if (ok(Manifest.permission.ACCESS_COARSE_LOCATION)) "✅" else "⚠️") + " Posizione (per il meteo)\n" +
            (if (ok(Manifest.permission.READ_CALENDAR)) "✅" else "⚠️") + " Calendario (per gli impegni)"
    }
}
