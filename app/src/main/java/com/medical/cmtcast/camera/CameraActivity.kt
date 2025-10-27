package com.medical.cmtcast.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.medical.cmtcast.R
import com.medical.cmtcast.processing.RulerDetector
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var rulerOverlay: RulerOverlayView
    private lateinit var tvStepIndicator: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var progressCapture: ProgressBar
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var btnRetake: Button
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button
    
    private var currentCaptureStep = 0
    private val captureSteps = arrayOf("Rotate Slowly - Front to Side", "Continue Rotating - Side to Back", "Complete Rotation", "Detail View - Ankle Area")
    private val capturedVideos = mutableListOf<String>()
    private lateinit var cameraExecutor: ExecutorService
    
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    private val rulerDetector = RulerDetector()
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        init {
            try {
                // Load C++ shared library first (required by OpenCV)
                System.loadLibrary("c++_shared")
                // Then load OpenCV native library
                System.loadLibrary("opencv_java4")
                Log.d("CameraActivity", "OpenCV libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("CameraActivity", "Failed to load native libraries", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        Log.d("CameraActivity", "OpenCV library loaded successfully")

        previewView = findViewById(R.id.viewFinder)
        rulerOverlay = findViewById(R.id.rulerOverlay)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)
        tvInstructions = findViewById(R.id.tvInstructions)
        progressCapture = findViewById(R.id.progressCapture)
        btnCapture = findViewById(R.id.btnCapture)
        btnRetake = findViewById(R.id.btnRetake)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        updateStepIndicator()
        
        // Check permissions before starting camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera and audio permissions are required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupUI() {
        btnCapture.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        btnRetake.setOnClickListener {
            if (capturedVideos.isNotEmpty()) {
                capturedVideos.removeLastOrNull()
                currentCaptureStep = maxOf(0, currentCaptureStep - 1)
                updateStepIndicator()
            }
        }
        
        btnNext.setOnClickListener {
            if (capturedVideos.size >= 2) {
                proceedToMeasurement()
            } else {
                Toast.makeText(this, "Please record at least 2 rotation videos", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        
        btnCapture.isEnabled = false
        isRecording = true
        
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + "_step${currentCaptureStep}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CMTCast")
            }
        }
        
        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(
                        this@CameraActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        btnCapture.isEnabled = true
                        btnCapture.setImageResource(android.R.drawable.ic_media_pause)
                        Toast.makeText(this, "Recording started - rotate slowly", Toast.LENGTH_SHORT).show()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val uri = recordEvent.outputResults.outputUri
                            capturedVideos.add(uri.toString())
                            currentCaptureStep++
                            updateStepIndicator()
                            Toast.makeText(this, "Video saved successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            recording?.close()
                            recording = null
                            Log.e("CameraActivity", "Video capture error: ${recordEvent.error}")
                            Toast.makeText(this, "Video recording failed", Toast.LENGTH_SHORT).show()
                        }
                        btnCapture.setImageResource(android.R.drawable.ic_menu_camera)
                        btnCapture.isEnabled = true
                        isRecording = false
                    }
                }
            }
    }
    
    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun updateStepIndicator() {
        val stepText = if (currentCaptureStep < captureSteps.size) {
            "Step ${currentCaptureStep + 1}/4: ${captureSteps[currentCaptureStep]}"
        } else {
            "Additional captures (${capturedVideos.size} total)"
        }
        
        tvStepIndicator.text = stepText
        tvInstructions.text = getInstructionForStep(currentCaptureStep)
        
        btnRetake.isEnabled = capturedVideos.isNotEmpty()
        btnNext.isEnabled = capturedVideos.size >= 2
        
        // Update progress
        val progress = (capturedVideos.size.toFloat() / 4 * 100).toInt()
        progressCapture.progress = progress
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview use case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Video capture use case
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                
                // Image analysis for real-time ruler detection
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, RulerAnalyzer())
                    }
                
                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera (preview, video, analysis)
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture, imageAnalyzer
                )
                
                Log.d("CameraActivity", "Camera started successfully with video recording and ruler detection")
            } catch (e: Exception) {
                Log.e("CameraActivity", "Camera initialization failed", e)
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getInstructionForStep(step: Int): String {
        return when (step) {
            0 -> "Place ruler next to leg. Tap to START recording, then slowly rotate 90° (front to side)."
            1 -> "Continue rotation. Record another 90° turn (side to back). Tap when done."
            2 -> "Complete the rotation. Record final 90° (back to front). Keep ruler visible."
            3 -> "Detail view: Focus on ankle area. Rotate slowly around ankle joint."
            else -> "Additional angles can help improve measurement accuracy."
        }
    }

    private fun proceedToMeasurement() {
        // Show loading message
        Toast.makeText(this, "Preparing analysis... This may take a moment", Toast.LENGTH_LONG).show()
        
        // Pass video URIs to measurement activity
        val intent = Intent(this, com.medical.cmtcast.measurement.MeasurementActivity::class.java)
        intent.putStringArrayListExtra("video_uris", ArrayList(capturedVideos))
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    /**
     * Analyzer for real-time ruler detection
     */
    private inner class RulerAnalyzer : ImageAnalysis.Analyzer {
        
        private var lastAnalysisTime = 0L
        private val analysisInterval = 500L // Analyze every 500ms to avoid overhead
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTime = System.currentTimeMillis()
            
            // Throttle analysis to avoid performance issues
            if (currentTime - lastAnalysisTime < analysisInterval) {
                imageProxy.close()
                return
            }
            
            lastAnalysisTime = currentTime
            
            try {
                // Convert ImageProxy to Bitmap
                val bitmap = imageProxyToBitmap(imageProxy)
                
                if (bitmap != null) {
                    // Convert Bitmap to OpenCV Mat
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)
                    
                    // Detect ruler
                    val rulerInfo = rulerDetector.detectRuler(mat)
                    
                    // Update overlay on main thread
                    runOnUiThread {
                        if (rulerInfo != null) {
                            // Scale detection rect from image to view coordinates
                            val scaleX = previewView.width.toFloat() / mat.width().toFloat()
                            val scaleY = previewView.height.toFloat() / mat.height().toFloat()
                            
                            val scaledRect = RectF(
                                rulerInfo.rulerRect.x * scaleX,
                                rulerInfo.rulerRect.y * scaleY,
                                (rulerInfo.rulerRect.x + rulerInfo.rulerRect.width) * scaleX,
                                (rulerInfo.rulerRect.y + rulerInfo.rulerRect.height) * scaleY
                            )
                            
                            rulerOverlay.updateRulerDetection(scaledRect, rulerInfo.confidence)
                        } else {
                            rulerOverlay.clearDetection()
                        }
                    }
                    
                    mat.release()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e("RulerAnalyzer", "Analysis failed", e)
            } finally {
                imageProxy.close()
            }
        }
        
        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            try {
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                // Convert NV21 to Bitmap
                val yuvImage = android.graphics.YuvImage(
                    nv21,
                    android.graphics.ImageFormat.NV21,
                    imageProxy.width,
                    imageProxy.height,
                    null
                )
                
                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                    100,
                    out
                )
                
                val imageBytes = out.toByteArray()
                return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                Log.e("RulerAnalyzer", "Bitmap conversion failed", e)
                return null
            }
        }
    }
}