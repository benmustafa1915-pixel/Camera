package com.kamera.app

import android.content.Context
import android.content.SharedPreferences

object Settings {
    val names = linkedMapOf(
        "faces" to "Yüz kutusu ve odak",
        "shake" to "Titreme koruması",
        "burst" to "3 kare çek, en netini seç",
        "night" to "Gece modu (5 kare)",
        "enhance" to "Otomatik iyileştirme",
        "scene" to "Sahne önerisi",
        "grid" to "Izgara çizgileri",
        "level" to "Ufuk çizgisi",
        "volume" to "Ses tuşuyla çekim",
        "voice" to "Sesle çekim (çek de)"
    )
    private val two = setOf("burst", "night", "grid", "level", "volume", "voice")
    private val defs = mapOf("grid" to 0, "voice" to 0)
    private lateinit var p: SharedPreferences

    fun init(c: Context) {
        p = c.getSharedPreferences("ayar2", Context.MODE_PRIVATE)
    }

    fun mode(k: String): Int = p.getInt(k, defs[k] ?: 2)

    fun cycle(k: String) {
        val m = mode(k)
        val n = if (k in two) (if (m == 0) 2 else 0) else (m + 1) % 3
        p.edit().putInt(k, n).apply()
    }

    fun label(k: String): String {
        val m = mode(k)
        return if (k in two) (if (m == 0) "Kapalı" else "Açık")
        else arrayOf("Kapalı", "Öneri", "Otomatik")[m]
    }

    fun auto(k: String) = mode(k) == 2
    fun show(k: String) = mode(k) >= 1
    fun f(k: String, d: Float): Float = p.getFloat(k, d)
    fun setF(k: String, v: Float) = p.edit().putFloat(k, v).apply()

    fun profile(m: Map<String, Int>) {
        val e = p.edit()
        for ((k, v) in m) e.putInt(k, v)
        e.apply()
    }
}
