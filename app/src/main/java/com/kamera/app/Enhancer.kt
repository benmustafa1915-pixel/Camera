package com.kamera.app

import android.graphics.Bitmap

object Enhancer {
    fun run(
        src: Bitmap,
        sharp: Float = Settings.f("sharp", 0.6f),
        sat: Float = Settings.f("sat", 1.12f),
        contrast: Float = Settings.f("con", 1.06f)
    ): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val strip = 128
        val inBuf = IntArray(w * (strip + 2))
        val outBuf = IntArray(w * strip)
        var y0 = 0
        while (y0 < h) {
            val rows = minOf(strip, h - y0)
            val top = maxOf(y0 - 1, 0)
            val bot = minOf(y0 + rows, h - 1)
            val n = bot - top + 1
            src.getPixels(inBuf, 0, w, 0, top, w, n)
            for (r in 0 until rows) {
                val yy = y0 + r - top
                val up = maxOf(yy - 1, 0)
                val dn = minOf(yy + 1, n - 1)
                for (x in 0 until w) {
                    val l = maxOf(x - 1, 0)
                    val rt = minOf(x + 1, w - 1)
                    outBuf[r * w + x] = px(
                        inBuf[yy * w + x], inBuf[yy * w + l], inBuf[yy * w + rt],
                        inBuf[up * w + x], inBuf[dn * w + x], sharp, sat, contrast
                    )
                }
            }
            out.setPixels(outBuf, 0, w, 0, y0, w, rows)
            y0 += rows
        }
        return out
    }

    private fun f(c: Int, a: Int, b: Int, u: Int, d: Int, s: Float): Float =
        c + s * 0.25f * (4 * c - a - b - u - d)

    private fun px(c: Int, a: Int, b: Int, u: Int, d: Int, s: Float, sat: Float, k: Float): Int {
        var r = f(c shr 16 and 255, a shr 16 and 255, b shr 16 and 255, u shr 16 and 255, d shr 16 and 255, s)
        var g = f(c shr 8 and 255, a shr 8 and 255, b shr 8 and 255, u shr 8 and 255, d shr 8 and 255, s)
        var bl = f(c and 255, a and 255, b and 255, u and 255, d and 255, s)
        val y = 0.299f * r + 0.587f * g + 0.114f * bl
        r = (y + (r - y) * sat - 128f) * k + 128f
        g = (y + (g - y) * sat - 128f) * k + 128f
        bl = (y + (bl - y) * sat - 128f) * k + 128f
        return (255 shl 24) or (cl(r) shl 16) or (cl(g) shl 8) or cl(bl)
    }

    private fun cl(v: Float): Int = if (v < 0f) 0 else if (v > 255f) 255 else v.toInt()
}
