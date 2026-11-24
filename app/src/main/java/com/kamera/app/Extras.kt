package com.kamera.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageProxy
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

object Scene {
    var dark = false
    private var last = 0L
    private var chip: TextView? = null
    private var act: Activity? = null
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    fun attach(a: Activity) {
        act = a
        val t = TextView(a).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 8, 24, 8)
            textSize = 14f
            visibility = View.GONE
        }
        chip = t
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = 330
        a.addContentView(t, lp)
    }

    fun shots(): Int = if (Settings.auto("night") && dark) 5 else if (Settings.auto("burst")) 3 else 1

    fun feed(ip: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!(Settings.show("scene") || Settings.show("night")) || now - last < 3000) return
        last = now
        val big = try { ip.toBitmap() } catch (e: Exception) { return }
        val small = Bitmap.createScaledBitmap(big, 224, 224, true)
        if (small !== big) big.recycle()
        val px = IntArray(224 * 224)
        small.getPixels(px, 0, 224, 0, 0, 224, 224)
        var s = 0L
        for (c in px) s += Color.green(c)
        dark = s / px.size < 50
        labeler.process(InputImage.fromBitmap(small, ip.imageInfo.rotationDegrees))
            .addOnSuccessListener { ls ->
                val t = ls.map { it.text }
                val name = when {
                    dark -> "Gece sahnesi: sabit tut"
                    t.any { it in listOf("Food", "Dish", "Meal", "Cuisine", "Fast food") } -> "Yemek"
                    t.any { it in listOf("Sky", "Mountain", "Beach", "Tree", "Landscape", "Sea", "Plant") } -> "Manzara"
                    t.any { it in listOf("Face", "Smile", "Person", "Selfie") } -> "Portre"
                    else -> ""
                }
                act?.runOnUiThread {
                    chip?.text = name
                    chip?.visibility = if (name.isEmpty() || !Settings.show("scene")) View.GONE else View.VISIBLE
                }
            }
    }
}

object Voice {
    private var sr: SpeechRecognizer? = null
    private var shoot: () -> Unit = {}
    private val h = Handler(Looper.getMainLooper())

    fun start(a: ComponentActivity, f: () -> Unit) {
        shoot = f
        a.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { begin(a) }
            override fun onPause(owner: LifecycleOwner) { sr?.destroy(); sr = null }
        })
    }

    private fun begin(a: Activity) {
        if (!Settings.auto("voice") || !SpeechRecognizer.isRecognitionAvailable(a)) return
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(a)
        sr = r
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        val again = Runnable { if (sr === r) r.startListening(i) }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList<String>()
                if (l.any { it.lowercase().contains("çek") }) shoot()
                h.postDelayed(again, 300)
            }
            override fun onError(e: Int) {
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return
                h.postDelayed(again, 1000)
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(i)
    }
}
