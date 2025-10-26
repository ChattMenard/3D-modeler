package com.medical.cmtcast.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.medical.cmtcast.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.viewFinder)
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
        startCamera()
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
                
                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
                
                Log.d("CameraActivity", "Camera started successfully with video recording")
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
}