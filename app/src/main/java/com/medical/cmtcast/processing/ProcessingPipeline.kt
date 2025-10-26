package com.medical.cmtcast.processing

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Coordinates the entire 3D reconstruction pipeline
 */
class ProcessingPipeline(private val context: Context) {
    
    companion object {
        private const val TAG = "ProcessingPipeline"
        
        init {
            // Initialize OpenCV
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV initialization failed")
            } else {
                Log.d(TAG, "OpenCV initialized successfully")
            }
        }
    }
    
    data class ProcessingResult(
        val stlFilePath: String,
        val pointCount: Int,
        val triangleCount: Int,
        val processingTimeMs: Long,
        val measurements: Measurements
    )
    
    data class Measurements(
        val circumferenceAnkle: Double,
        val circumferenceCalf: Double,
        val lengthTotal: Double
    )
    
    /**
     * Process videos and generate 3D cast model
     */
    suspend fun processVideos(videoUris: List<Uri>): ProcessingResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val processingLogs = mutableListOf<String>()
        
        fun log(message: String) {
            val timestamp = System.currentTimeMillis() - startTime
            val logEntry = "[${timestamp}ms] $message"
            processingLogs.add(logEntry)
            Log.d(TAG, logEntry)
        }
        
        log("Starting 3D reconstruction pipeline with ${videoUris.size} videos")
        
        // Step 1: Extract frames from all videos
        val videoProcessor = VideoProcessor(context)
        val allFrames = mutableListOf<VideoProcessor.ProcessedFrame>()
        
        for ((index, uri) in videoUris.withIndex()) {
            log("Processing video ${index + 1}/${videoUris.size}: $uri")
            val frames = videoProcessor.extractFrames(uri)
            allFrames.addAll(frames)
            log("Extracted ${frames.size} frames from video ${index + 1}")
        }
        
        log("Total frames extracted: ${allFrames.size}")
        
        // Step 2: Detect ruler and calculate scale
        val rulerDetector = RulerDetector()
        val frameMats = allFrames.map { it.mat }
        val rulerInfo = rulerDetector.detectRulerMultiFrame(frameMats)
        
        val pixelToMm = rulerInfo?.pixelToMmRatio ?: run {
            log("WARNING: Ruler not detected, using default scale")
            0.5 // Default: 0.5 mm per pixel
        }
        
        if (rulerInfo != null) {
            log("Ruler detected with confidence ${rulerInfo.confidence}, scale: $pixelToMm px/mm")
        }
        
        // Step 3: Build 3D point cloud
        log("Building 3D point cloud...")
        val reconstruction = Reconstruction3D()
        val pointCloud = reconstruction.buildPointCloud(frameMats, pixelToMm)
        log("Generated ${pointCloud.size} 3D points")
        
        // Step 4: Downsample for performance
        log("Downsampling point cloud...")
        val downsampledCloud = reconstruction.downsamplePointCloud(pointCloud, voxelSize = 2.0)
        log("Downsampled to ${downsampledCloud.size} points")
        
        // Step 5: Generate mesh
        log("Generating mesh...")
        val meshGenerator = MeshGenerator()
        val legMesh = meshGenerator.createMesh(downsampledCloud)
        log("Initial mesh: ${legMesh.triangles.size} triangles")
        
        // Step 6: Apply cast thickness
        log("Applying cast thickness (3mm)...")
        val castMesh = meshGenerator.applyThickness(legMesh, thickness = 3.0)
        log("Cast mesh: ${castMesh.triangles.size} triangles")
        
        // Step 7: Export to STL
        log("Exporting to STL format...")
        val stlContent = meshGenerator.exportToSTL(castMesh, "cmt_leg_cast")
        
        // Step 8: Save files
        val timestamp = System.currentTimeMillis()
        log("Saving files...")
        val stlFile = saveSTLFile(stlContent)
        log("STL file saved: ${stlFile.absolutePath} (${stlFile.length()} bytes)")
        
        // Step 9: Calculate measurements
        log("Calculating measurements...")
        val measurements = calculateMeasurements(downsampledCloud)
        log("Ankle: ${measurements.circumferenceAnkle}mm, Calf: ${measurements.circumferenceCalf}mm, Length: ${measurements.lengthTotal}mm")
        
        // Step 10: Save measurement data
        val measurementFile = saveMeasurementData(
            measurements,
            pointCloud.size,
            castMesh.triangles.size,
            timestamp - startTime,
            videoUris.size,
            timestamp
        )
        log("Measurement data saved: ${measurementFile.absolutePath}")
        
        // Step 11: Save processing log
        val logFile = saveProcessingLog(
            videoCount = videoUris.size,
            frameCount = allFrames.size,
            rulerDetected = rulerInfo != null,
            pixelToMm = pixelToMm,
            pointCount = pointCloud.size,
            downsampledCount = downsampledCloud.size,
            triangleCount = castMesh.triangles.size,
            processingTimeMs = timestamp - startTime,
            timestamp = timestamp,
            logs = processingLogs
        )
        log("Processing log saved: ${logFile.absolutePath}")
        
        // Cleanup
        videoProcessor.cleanup(allFrames)
        frameMats.forEach { it.release() }
        
        val processingTime = System.currentTimeMillis() - startTime
        
        Log.d(TAG, "Processing complete in ${processingTime}ms")
        Log.d(TAG, "Files saved:")
        Log.d(TAG, "  - STL: ${stlFile.absolutePath}")
        Log.d(TAG, "  - Measurements: ${measurementFile.absolutePath}")
        Log.d(TAG, "  - Log: ${logFile.absolutePath}")
        
        return@withContext ProcessingResult(
            stlFilePath = stlFile.absolutePath,
            pointCount = downsampledCloud.size,
            triangleCount = castMesh.triangles.size,
            processingTimeMs = processingTime,
            measurements = measurements
        )
    }
    
    /**
     * Save STL file to device storage
     */
    private fun saveSTLFile(stlContent: String): File {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "CMTCast"
        )
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        val file = File(directory, "leg_cast_$timestamp.stl")
        
        file.writeText(stlContent)
        
        Log.d(TAG, "STL file saved: ${file.absolutePath} (${file.length()} bytes)")
        
        return file
    }
    
    /**
     * Save measurement data as JSON
     */
    private fun saveMeasurementData(
        measurements: Measurements,
        pointCount: Int,
        triangleCount: Int,
        processingTimeMs: Long,
        videoCount: Int,
        timestamp: Long
    ): File {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "CMTCast"
        )
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val file = File(directory, "measurements_$timestamp.json")
        
        val json = """
            {
              "timestamp": $timestamp,
              "date": "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))}",
              "measurements": {
                "ankleCircumference_mm": ${measurements.circumferenceAnkle},
                "calfCircumference_mm": ${measurements.circumferenceCalf},
                "totalLength_mm": ${measurements.lengthTotal}
              },
              "model": {
                "pointCount": $pointCount,
                "triangleCount": $triangleCount
              },
              "processing": {
                "videoCount": $videoCount,
                "processingTime_ms": $processingTimeMs,
                "processingTime_seconds": ${processingTimeMs / 1000.0}
              },
              "files": {
                "stl": "leg_cast_$timestamp.stl",
                "measurements": "measurements_$timestamp.json",
                "log": "processing_log_$timestamp.txt"
              }
            }
        """.trimIndent()
        
        file.writeText(json)
        
        Log.d(TAG, "Measurement data saved: ${file.absolutePath}")
        
        return file
    }
    
    /**
     * Save processing log
     */
    private fun saveProcessingLog(
        videoCount: Int,
        frameCount: Int,
        rulerDetected: Boolean,
        pixelToMm: Double,
        pointCount: Int,
        downsampledCount: Int,
        triangleCount: Int,
        processingTimeMs: Long,
        timestamp: Long,
        logs: List<String>
    ): File {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "CMTCast"
        )
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val file = File(directory, "processing_log_$timestamp.txt")
        
        val logContent = buildString {
            appendLine("=" * 60)
            appendLine("CMT CAST 3D RECONSTRUCTION - PROCESSING LOG")
            appendLine("=" * 60)
            appendLine()
            appendLine("TIMESTAMP: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))}")
            appendLine("PROCESSING TIME: ${processingTimeMs / 1000.0}s")
            appendLine()
            appendLine("=" * 60)
            appendLine("INPUT")
            appendLine("=" * 60)
            appendLine("Videos processed: $videoCount")
            appendLine("Frames extracted: $frameCount")
            appendLine()
            appendLine("=" * 60)
            appendLine("CALIBRATION")
            appendLine("=" * 60)
            appendLine("Ruler detected: ${if (rulerDetected) "YES" else "NO (using default)"}")
            appendLine("Scale: $pixelToMm mm/pixel")
            appendLine()
            appendLine("=" * 60)
            appendLine("3D RECONSTRUCTION")
            appendLine("=" * 60)
            appendLine("Initial point cloud: $pointCount points")
            appendLine("Downsampled cloud: $downsampledCount points")
            appendLine("Mesh triangles: $triangleCount")
            appendLine()
            appendLine("=" * 60)
            appendLine("DETAILED LOG")
            appendLine("=" * 60)
            logs.forEach { appendLine(it) }
            appendLine()
            appendLine("=" * 60)
            appendLine("END OF LOG")
            appendLine("=" * 60)
        }
        
        file.writeText(logContent)
        
        Log.d(TAG, "Processing log saved: ${file.absolutePath}")
        
        return file
    }
    
    private operator fun String.times(count: Int) = this.repeat(count)
    
    /**
     * Calculate key measurements from point cloud
     */
    private fun calculateMeasurements(points: List<Reconstruction3D.Point3D>): Measurements {
        // Find ankle (lowest 10% of points)
        val sortedByHeight = points.sortedBy { it.y }
        val ankleCutoff = (sortedByHeight.size * 0.1).toInt()
        val anklePoints = sortedByHeight.take(ankleCutoff)
        
        // Find calf (middle section)
        val calfStart = (sortedByHeight.size * 0.4).toInt()
        val calfEnd = (sortedByHeight.size * 0.6).toInt()
        val calfPoints = sortedByHeight.subList(calfStart, calfEnd)
        
        // Calculate circumferences (approximate from point spread)
        val ankleCircumference = estimateCircumference(anklePoints)
        val calfCircumference = estimateCircumference(calfPoints)
        
        // Total length
        val minY = sortedByHeight.first().y
        val maxY = sortedByHeight.last().y
        val totalLength = maxY - minY
        
        Log.d(TAG, "Measurements - Ankle: ${ankleCircumference}mm, Calf: ${calfCircumference}mm, Length: ${totalLength}mm")
        
        return Measurements(
            circumferenceAnkle = ankleCircumference,
            circumferenceCalf = calfCircumference,
            lengthTotal = totalLength
        )
    }
    
    /**
     * Estimate circumference from points at same height
     */
    private fun estimateCircumference(points: List<Reconstruction3D.Point3D>): Double {
        if (points.isEmpty()) return 0.0
        
        // Calculate average radius from center
        val centerX = points.map { it.x }.average()
        val centerZ = points.map { it.z }.average()
        
        val avgRadius = points.map { point ->
            val dx = point.x - centerX
            val dz = point.z - centerZ
            kotlin.math.sqrt(dx * dx + dz * dz)
        }.average()
        
        // Circumference = 2πr
        return 2 * Math.PI * avgRadius
    }
}
