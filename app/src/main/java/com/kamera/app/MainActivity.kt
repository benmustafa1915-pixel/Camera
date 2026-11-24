package com.kamera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.*
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.concurrent.Executors
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var pv: PreviewView
    private lateinit var ov: Overlay
    private lateinit var msg: TextView
    private var camera: Camera? = null
    private var cap: ImageCapture? = null
    private var vcap: VideoCapture<Recorder>? = null
    private var rec: Recording? = null
    private var video = false
    private var back = true
    private var flash = 0
    private var timer = 0
    private var gyro = 0f
    private var roll = 0f
    private var lastFocus = 0L
    private val shakeText = "Titreme var, sabit tut"
    private val flashModes = intArrayOf(ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)
    private val flashNames = arrayOf("Flaş: kapalı", "Flaş: oto", "Flaş: açık")
    private val timerVals = intArrayOf(0, 3, 10)
    private val exec = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
        )
    }

    inner class Overlay : View(this@MainActivity) {
        var faces: List<RectF> = emptyList()
        private val p = Paint().apply {
            color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (Settings.show("faces")) for (r in faces) c.drawRect(r, p)
            if (Settings.show("grid")) for (i in 1..2) {
                c.drawLine(w * i / 3, 0f, w * i / 3, h, p)
                c.drawLine(0f, h * i / 3, w, h * i / 3, p)
            }
            if (Settings.show("level")) {
                c.save()
                c.rotate(roll, w / 2, h / 2)
                c.drawLine(w * 0.2f, h / 2, w * 0.8f, h / 2, p)
                c.restore()
            }
        }
    }

    private fun btn(t: String, f: () -> Unit) =
        Button(this).apply { text = t; setOnClickListener { f() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Settings.init(this)
        pv = PreviewView(this)
        ov = Overlay()
        msg = TextView(this).apply {
            setTextColor(Color.YELLOW); textSize = 18f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, 240, 0, 0)
        }
        pv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val pt = pv.meteringPointFactory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
            }
            true
        }
        val fb = btn(flashNames[0]) {}
        fb.setOnClickListener {
            flash = (flash + 1) % 3
            fb.text = flashNames[flash]
            cap?.flashMode = flashModes[flash]
        }
        val tb = btn("Zaman: kapalı") {}
        tb.setOnClickListener {
            timer = (timer + 1) % 3
            tb.text = if (timer == 0) "Zaman: kapalı" else "Zaman: " + timerVals[timer] + " sn"
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 90, 0, 0)
            addView(fb); addView(tb)
            addView(btn("Çevir") { back = !back; bind() })
        }
        val zoom = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            for ((z, t) in listOf(0.5f to "0.5x", 1f to "1x", 2f to "2x", 4f to "4x", 10f to "10x"))
                addView(btn(t) { setZoom(z) })
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(btn("Ayar") { SettingsActivity.open(this@MainActivity) })
            addView(btn("ÇEK") { shoot() })
            addView(btn("Foto/Video") {
                video = !video
                msg.text = if (video) "Video modu" else "Foto modu"
                bind()
            })
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 60)
            addView(zoom); addView(row)
        }
        val root = FrameLayout(this)
        root.addView(pv); root.addView(ov); root.addView(msg)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        Scene.attach(this)
        Voice.start(this) { shoot() }
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { bind() }
            .launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun bind() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            msg.text = "Kamera izni gerekli: uygulama ayarlarından izin ver"
            return
        }
        rec?.stop()
        rec = null
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            try {
                val p = f.get()
                p.unbindAll()
                val sel = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                if (video) {
                    ov.faces = emptyList()
                    ov.postInvalidate()
                    val vc = VideoCapture.withOutput(
                        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
                    )
                    vcap = vc
                    camera = p.bindToLifecycle(this, sel, prev, vc)
                } else {
                    val ic = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashModes[flash]).build()
                    cap = ic
                    val an = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    an.setAnalyzer(exec) { analyze(it) }
                    camera = p.bindToLifecycle(this, sel, prev, ic, an)
                }
            } catch (e: Exception) {
                msg.text = "Kamera açılamadı: " + e.message
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(ip: ImageProxy) {
        Scene.feed(ip)
        val img = ip.image
        if (img == null || !Settings.show("faces")) {
            ov.faces = emptyList(); ov.postInvalidate(); ip.close(); return
        }
        val rot = ip.imageInfo.rotationDegrees
        val w = (if (rot % 180 == 0) ip.width else ip.height).toFloat()
        val h = (if (rot % 180 == 0) ip.height else ip.width).toFloat()
        detector.process(InputImage.fromMediaImage(img, rot))
            .addOnSuccessListener { fs ->
                val sc = maxOf(ov.width / w, ov.height / h)
                val dx = (ov.width - w * sc) / 2
                val dy = (ov.height - h * sc) / 2
                ov.faces = fs.map {
                    val b = it.boundingBox
                    var l = b.left * sc + dx
                    var r = b.right * sc + dx
                    if (!back) { val t = ov.width - l; l = ov.width - r; r = t }
                    RectF(l, b.top * sc + dy, r, b.bottom * sc + dy)
                }
                ov.postInvalidate()
                val now = System.currentTimeMillis()
                if (Settings.auto("faces") && ov.faces.isNotEmpty() && now - lastFocus > 2000) {
                    lastFocus = now
                    val big = ov.faces.maxByOrNull { it.width() * it.height() }!!
                    val pt = pv.meteringPointFactory.createPoint(big.centerX(), big.centerY())
                    camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                }
            }
            .addOnCompleteListener { ip.close() }
    }

    private fun setZoom(z: Float) {
        val c = camera ?: return
        val s = c.cameraInfo.zoomState.value ?: return
        c.cameraControl.setZoomRatio(z.coerceIn(s.minZoomRatio, s.maxZoomRatio))
    }

    private fun shoot() {
        if (video) { toggleVideo(); return }
        countdown(timerVals[timer]) { capture() }
    }

    private fun countdown(s: Int, go: () -> Unit) {
        if (s <= 0) go()
        else { msg.text = s.toString(); pv.postDelayed({ countdown(s - 1, go) }, 1000) }
    }

    private fun capture() {
        val c = cap ?: return
        val n = Scene.shots()
        var best: Bitmap? = null
        var bs = -1.0
        fun take(i: Int) {
            c.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(ip: ImageProxy) {
                    val m = Matrix().apply { postRotate(ip.imageInfo.rotationDegrees.toFloat()) }
                    val raw = ip.toBitmap()
                    ip.close()
                    val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    if (raw !== bmp) raw.recycle()
                    val s = sharp(bmp)
                    if (s > bs) { val old = best; best = bmp; bs = s; old?.recycle() } else bmp.recycle()
                    if (i + 1 < n) take(i + 1) else save(best!!)
                }

                override fun onError(e: ImageCaptureException) {
                    runOnUiThread { msg.text = "Hata: " + e.message }
                }
            })
        }
        if (Settings.auto("shake")) waitSteady(0) { take(0) } else take(0)
    }

    private fun waitSteady(t: Int, go: () -> Unit) {
        if (gyro < 0.15f || t >= 20) go()
        else { msg.text = "Sabit tut…"; pv.postDelayed({ waitSteady(t + 1, go) }, 100) }
    }

    private fun sharp(b: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(b, 512, 512 * b.height / b.width, true)
        val w = s.width
        val h = s.height
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        if (s !== b) s.recycle()
        val g = DoubleArray(w * h) { Color.green(px[it]).toDouble() }
        var sum = 0.0; var sq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val l = 4 * g[y * w + x] - g[y * w + x - 1] - g[y * w + x + 1] - g[(y - 1) * w + x] - g[(y + 1) * w + x]
            sum += l; sq += l * l; n++
        }
        return sq / n - (sum / n) * (sum / n)
    }

    private fun save(b: Bitmap) {
        val t = System.currentTimeMillis()
        when (Settings.mode("enhance")) {
            0 -> put(b, "IMG_$t")
            1 -> { put(b, "IMG_$t"); put(Enhancer.run(b), "IMG_${t}_duzenli") }
            else -> put(Enhancer.run(b), "IMG_$t")
        }
        runOnUiThread { msg.text = "Kaydedildi" }
    }

    private fun put(b: Bitmap, name: String) {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kamera")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { u ->
            contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }
    }

    private fun toggleVideo() {
        val r = rec
        if (r != null) { r.stop(); rec = null; msg.text = "Video kaydedildi"; return }
        val v = vcap ?: return
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_" + System.currentTimeMillis())
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kamera")
        }
        val o = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(cv).build()
        var pr = v.output.prepareRecording(this, o)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pr = pr.withAudioEnabled()
        }
        rec = pr.start(ContextCompat.getMainExecutor(this)) { }
        msg.text = "Kayıt…"
    }

    override fun onKeyDown(k: Int, e: KeyEvent?): Boolean {
        if (Settings.auto("volume") && (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_UP)) {
            shoot(); return true
        }
        return super.onKeyDown(k, e)
    }

    override fun onResume() {
        super.onResume()
        ov.invalidate()
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        for (t in listOf(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER))
            sm.getDefaultSensor(t)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val v = e.values
        if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyro = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (Settings.show("shake") && !video) {
                if (gyro > 0.5f) msg.text = shakeText
                else if (msg.text.toString() == shakeText) msg.text = ""
            }
        } else {
            roll = Math.toDegrees(Math.atan2(v[0].toDouble(), v[1].toDouble())).toFloat()
            if (Settings.show("level")) ov.invalidate()
        }
    }
}
