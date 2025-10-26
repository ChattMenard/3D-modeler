package com.medical.cmtcast.measurement

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medical.cmtcast.R
import com.medical.cmtcast.processing.ProcessingPipeline
import kotlinx.coroutines.launch

class MeasurementActivity : AppCompatActivity() {

    private lateinit var tvProcessingStatus: TextView
    
    private var stlFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)

        tvProcessingStatus = findViewById(R.id.tvProcessingStatus)

        // Get video URIs from intent
        val videoUriStrings = intent.getStringArrayListExtra("video_uris") ?: arrayListOf()
        val videoUris = videoUriStrings.map { Uri.parse(it) }

        if (videoUris.isNotEmpty()) {
            startProcessing(videoUris)
        } else {
            tvProcessingStatus.text = "Error: No videos to process"
        }
    }

    private fun startProcessing(videoUris: List<Uri>) {
        lifecycleScope.launch {
            try {
                tvProcessingStatus.text = "Processing ${videoUris.size} videos...\n\nThis may take a few minutes."

                val pipeline = ProcessingPipeline(this@MeasurementActivity)
                
                // Process in background
                val result = pipeline.processVideos(videoUris)
                
                // Update UI with results
                stlFilePath = result.stlFilePath
                
                // Display measurements
                val measurements = """
                    |✅ 3D RECONSTRUCTION COMPLETE!
                    |
                    |📏 LEG MEASUREMENTS
                    |Ankle: ${String.format("%.1f", result.measurements.circumferenceAnkle)} mm
                    |Calf: ${String.format("%.1f", result.measurements.circumferenceCalf)} mm
                    |Length: ${String.format("%.1f", result.measurements.lengthTotal)} mm
                    |
                    |🔺 3D MODEL
                    |Points: ${result.pointCount}
                    |Triangles: ${result.triangleCount}
                    |Time: ${result.processingTimeMs / 1000.0}s
                    |
                    |📄 STL FILE SAVED:
                    |${result.stlFilePath}
                """.trimMargin()
                
                tvProcessingStatus.text = measurements
                
                Toast.makeText(
                    this@MeasurementActivity,
                    "Cast model generated successfully!",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                tvProcessingStatus.text = "Error: ${e.message}\n\n${e.stackTraceToString()}"
                Toast.makeText(
                    this@MeasurementActivity,
                    "Processing failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
