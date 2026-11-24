package com.kamera.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*

class SettingsActivity : Activity() {
    companion object {
        fun open(a: Activity) = a.startActivity(Intent(a, SettingsActivity::class.java))
    }

    private var sv: ScrollView? = null
    private val prof = listOf(
        "Gündüz" to mapOf("faces" to 2, "shake" to 2, "burst" to 0, "night" to 0, "enhance" to 2, "scene" to 2),
        "Gece" to mapOf("faces" to 2, "shake" to 2, "burst" to 2, "night" to 2, "enhance" to 2, "scene" to 2),
        "Çocuk" to mapOf("faces" to 2, "shake" to 0, "burst" to 2, "night" to 0, "enhance" to 2, "scene" to 1)
    )
    private val sliders = listOf(
        listOf("sharp", "Keskinlik", 0f, 1.5f, 0.6f),
        listOf("sat", "Doygunluk", 0.8f, 1.5f, 1.12f),
        listOf("con", "Kontrast", 0.9f, 1.3f, 1.06f)
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Settings.init(this)
        build()
    }

    private fun tv(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, 32, 0, 8) }

    private fun build() {
        val y = sv?.scrollY ?: 0
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 120, 32, 80)
        }
        col.addView(tv("Hazır profiller"))
        val pr = LinearLayout(this)
        for ((n, m) in prof) pr.addView(Button(this).apply {
            text = n
            setOnClickListener { Settings.profile(m); build() }
        })
        col.addView(pr)
        col.addView(tv("Özellikler: dokununca değişir. Öneri sadece uyarır, Otomatik kendisi yapar."))
        for ((k, n) in Settings.names) col.addView(Button(this).apply {
            text = n + ": " + Settings.label(k)
            setOnClickListener { Settings.cycle(k); build() }
        })
        col.addView(tv("İyileştirme gücü"))
        for (s in sliders) {
            val k = s[0] as String
            val lo = s[2] as Float
            val hi = s[3] as Float
            col.addView(tv(s[1] as String))
            col.addView(SeekBar(this).apply {
                max = 100
                progress = ((Settings.f(k, s[4] as Float) - lo) / (hi - lo) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                        if (u) Settings.setF(k, lo + (hi - lo) * p / 100f)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }
        val s = ScrollView(this)
        s.addView(col)
        sv = s
        setContentView(s)
        s.post { s.scrollTo(0, y) }
    }
}
