package com.medical.cmtcast.measurement

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.medical.cmtcast.R
import com.medical.cmtcast.processing.ProcessingPipeline
import com.medical.cmtcast.viewer.Model3DViewerActivity
import kotlinx.coroutines.launch
import java.io.File

class MeasurementActivity : AppCompatActivity() {

    private lateinit var tvProcessingStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvStepLog: TextView
    private lateinit var btnView3DModel: Button
    private lateinit var btnShareSTL: Button
    private lateinit var btnShareAll: Button
    private lateinit var layoutShareButtons: LinearLayout
    
    private var stlFilePath: String? = null
    private var measurementFilePath: String? = null
    private var logFilePath: String? = null
    private val stepLog = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)

        tvProcessingStatus = findViewById(R.id.tvProcessingStatus)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvStepLog = findViewById(R.id.tvStepLog)
        btnView3DModel = findViewById(R.id.btnView3DModel)
        btnShareSTL = findViewById(R.id.btnShareSTL)
        btnShareAll = findViewById(R.id.btnShareAll)
        layoutShareButtons = findViewById(R.id.layoutShareButtons)
        
        // Setup 3D viewer button
        btnView3DModel.setOnClickListener {
            stlFilePath?.let { path ->
                val intent = Intent(this, Model3DViewerActivity::class.java)
                intent.putExtra("stl_path", path)
                startActivity(intent)
            }
        }
        
        // Setup share buttons
        btnShareSTL.setOnClickListener {
            stlFilePath?.let { shareFile(it, "Share STL File", "model/stl") }
        }
        
        btnShareAll.setOnClickListener {
            shareAllFiles()
        }

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
                tvProcessingStatus.text = "Starting 3D reconstruction pipeline..."

                val pipeline = ProcessingPipeline(this@MeasurementActivity)
                
                // Set up progress callback
                pipeline.setProgressCallback(object : ProcessingPipeline.ProgressCallback {
                    override fun onProgress(progress: Int, message: String) {
                        runOnUiThread {
                            progressBar.progress = progress
                            tvProgressPercent.text = "$progress%"
                            tvProcessingStatus.text = message
                        }
                    }
                    
                    override fun onStepComplete(step: String, details: String) {
                        runOnUiThread {
                            val logEntry = "✓ $step: $details"
                            stepLog.add(logEntry)
                            tvStepLog.text = stepLog.takeLast(10).joinToString("\n")
                        }
                    }
                })
                
                // Process in background
                val result = pipeline.processVideos(videoUris)
                
                // Update UI with results and store file paths
                stlFilePath = result.stlFilePath
                
                // Derive measurement and log file paths
                val stlFile = File(result.stlFilePath)
                val parentDir = stlFile.parentFile
                val timestamp = stlFile.nameWithoutExtension.substringAfterLast("_")
                measurementFilePath = File(parentDir, "measurements_$timestamp.json").absolutePath
                logFilePath = File(parentDir, "processing_log_$timestamp.txt").absolutePath
                
                // Show 3D viewer and share buttons
                btnView3DModel.visibility = View.VISIBLE
                layoutShareButtons.visibility = View.VISIBLE
                
                // Build result message with validation
                val validationIcon = when (result.validationResult.level) {
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.OK -> "✅"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.WARNING -> "⚠️"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.ERROR -> "❌"
                }
                
                val statusHeader = when (result.validationResult.level) {
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.OK -> 
                        "✅ 3D RECONSTRUCTION COMPLETE!"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.WARNING -> 
                        "⚠️ RECONSTRUCTION COMPLETE WITH WARNINGS"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.ERROR -> 
                        "❌ RECONSTRUCTION COMPLETE WITH ERRORS"
                }
                
                val measurements = buildString {
                    appendLine(statusHeader)
                    appendLine()
                    appendLine("📏 LEG MEASUREMENTS")
                    appendLine("Ankle: ${String.format("%.1f", result.measurements.circumferenceAnkle)} mm")
                    appendLine("Calf: ${String.format("%.1f", result.measurements.circumferenceCalf)} mm")
                    appendLine("Length: ${String.format("%.1f", result.measurements.lengthTotal)} mm")
                    appendLine()
                    appendLine("🔺 3D MODEL")
                    appendLine("Points: ${result.pointCount}")
                    appendLine("Triangles: ${result.triangleCount}")
                    appendLine("Time: ${result.processingTimeMs / 1000.0}s")
                    appendLine()
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine()
                    appendLine(result.validationResult.messages.joinToString("\n"))
                    appendLine()
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine()
                    appendLine("📄 FILES SAVED:")
                    appendLine(result.stlFilePath)
                    appendLine()
                    appendLine("Also saved:")
                    appendLine("- measurements_*.json (measurement data)")
                    appendLine("- processing_log_*.txt (detailed logs)")
                    appendLine()
                    appendLine("All files in: CMTCast folder")
                }
                
                tvProcessingStatus.text = measurements
                
                // Show appropriate toast based on validation
                val toastMessage = when (result.validationResult.level) {
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.OK ->
                        "Cast model generated successfully!"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.WARNING ->
                        "Model generated with warnings - review before use"
                    com.medical.cmtcast.validation.QualityValidator.ValidationLevel.ERROR ->
                        "CRITICAL ERRORS DETECTED - Do not use this model!"
                }
                
                Toast.makeText(
                    this@MeasurementActivity,
                    toastMessage,
                    if (result.validationResult.hasErrors()) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
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
    
    /**
     * Share a single file using Android Share Sheet
     */
    private fun shareFile(filePath: String, title: String, mimeType: String = "*/*") {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CMT Cast - ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "3D cast model generated by CMT Cast Designer app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, title))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Share all generated files (STL, JSON, LOG)
     */
    private fun shareAllFiles() {
        try {
            val uris = ArrayList<Uri>()
            
            // Add STL file
            stlFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    uris.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", file))
                }
            }
            
            // Add measurement JSON
            measurementFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    uris.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", file))
                }
            }
            
            // Add processing log
            logFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    uris.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", file))
                }
            }
            
            if (uris.isEmpty()) {
                Toast.makeText(this, "No files found to share", Toast.LENGTH_SHORT).show()
                return
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "CMT Cast - Complete Model Package")
                putExtra(Intent.EXTRA_TEXT, 
                    "Complete 3D cast model package including:\n" +
                    "- STL file for 3D printing\n" +
                    "- Measurement data (JSON)\n" +
                    "- Processing log\n\n" +
                    "Generated by CMT Cast Designer app"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share All Files (${uris.size} files)"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
