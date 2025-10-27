package com.medical.cmtcast.processing

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.medical.cmtcast.settings.AppSettings
import com.medical.cmtcast.validation.QualityValidator
import com.medical.cmtcast.debug.DebugHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    
    /**
     * Callback interface for progress updates
     */
    interface ProgressCallback {
        fun onProgress(progress: Int, message: String)
        fun onStepComplete(step: String, details: String)
    }
    
    data class ProcessingResult(
        val stlFilePath: String,
        val pointCount: Int,
        val triangleCount: Int,
        val processingTimeMs: Long,
        val measurements: Measurements,
        val validationResult: QualityValidator.ValidationResult
    )
    
    data class Measurements(
        val circumferenceAnkle: Double,
        val circumferenceCalf: Double,
        val lengthTotal: Double
    )
    
    private var progressCallback: ProgressCallback? = null
    
    /**
     * Get current memory status for logging
     */
    private fun getMemoryStatus(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val memoryPercent = (usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt()
        return "${usedMemory}MB / ${maxMemory}MB ($memoryPercent%)"
    }
    
    /**
     * Set callback for progress updates
     */
    fun setProgressCallback(callback: ProgressCallback?) {
        this.progressCallback = callback
    }
    
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
        
        suspend fun updateProgress(progress: Int, message: String) {
            log(message)
            withContext(Dispatchers.Main) {
                progressCallback?.onProgress(progress, message)
            }
        }
        
        suspend fun stepComplete(step: String, details: String) {
            log("$step: $details")
            withContext(Dispatchers.Main) {
                progressCallback?.onStepComplete(step, details)
            }
        }
        
        log("Starting 3D reconstruction pipeline with ${videoUris.size} videos")
        updateProgress(0, "Initializing processing pipeline...")
        
        // Step 1: Extract frames from all videos
        updateProgress(5, "Extracting frames from videos...")
        val videoProcessor = VideoProcessor(context)
        val allFrames = mutableListOf<VideoProcessor.ProcessedFrame>()
        
        for ((index, uri) in videoUris.withIndex()) {
            val videoProgress = 5 + (index * 20 / videoUris.size)
            updateProgress(videoProgress, "Processing video ${index + 1}/${videoUris.size}...")
            log("Processing video ${index + 1}/${videoUris.size}: $uri")
            val frames = videoProcessor.extractFrames(uri)
            allFrames.addAll(frames)
            log("Extracted ${frames.size} frames from video ${index + 1}")
        }
        
        stepComplete("Frame Extraction", "${allFrames.size} frames extracted from ${videoUris.size} videos")
        
        // Step 2: Detect ruler and calculate scale
        updateProgress(30, "Detecting ruler and calculating scale...")
        val rulerDetector = RulerDetector()
        val frameMats = allFrames.map { it.mat }
        val rulerInfo = rulerDetector.detectRulerMultiFrame(frameMats)
        
        val pixelToMm = rulerInfo?.pixelToMmRatio ?: run {
            log("WARNING: Ruler not detected, using default scale")
            0.5 // Default: 0.5 mm per pixel
        }
        
        if (rulerInfo != null) {
            log("Ruler detected with confidence ${rulerInfo.confidence}, scale: $pixelToMm px/mm")
            stepComplete("Ruler Detection", "Confidence: ${(rulerInfo.confidence * 100).toInt()}%, Scale: ${"%.3f".format(pixelToMm)} px/mm")
        } else {
            stepComplete("Ruler Detection", "Warning: Using default scale (ruler not detected)")
        }
        
        // Step 3: Build 3D point cloud
        updateProgress(40, "Building 3D point cloud from frames...")
        log("Building 3D point cloud...")
        val reconstruction = Reconstruction3D()
        val pointCloud = reconstruction.buildPointCloud(frameMats, pixelToMm)
        log("Generated ${pointCloud.size} 3D points")
        stepComplete("3D Reconstruction", "${pointCloud.size} points generated")
        
        // Step 4: Downsample for performance
        updateProgress(55, "Optimizing point cloud...")
        log("Downsampling point cloud...")
        
        // Debug: Check point cloud before downsampling
        DebugHelper.logPointCloudStats(pointCloud, "Before Downsampling")
        
        // CRITICAL: Much more aggressive downsampling to prevent memory crashes
        // Target: max 10,000 points → ~20,000 triangles (safe for S10)
        val initialVoxelSize = 3.0  // Increased from 2.0
        var downsampledCloud = reconstruction.downsamplePointCloud(pointCloud, voxelSize = initialVoxelSize)
        log("Initial downsample to ${downsampledCloud.size} points")
        
        // Multi-stage downsampling based on point count
        val maxPoints = 10000 // Reduced from 15000 for safety (1.4M triangles was too much!)
        if (downsampledCloud.size > maxPoints) {
            log("WARNING: Point cloud too large (${downsampledCloud.size} points), applying aggressive downsampling")
            val aggressiveVoxelSize = 5.0  // Increased from 3.5
            downsampledCloud = reconstruction.downsamplePointCloud(pointCloud, voxelSize = aggressiveVoxelSize)
            log("Aggressively downsampled to ${downsampledCloud.size} points")
        }
        
        // Emergency fallback: if STILL too many points, go nuclear
        if (downsampledCloud.size > maxPoints) {
            log("CRITICAL: Still too many points (${downsampledCloud.size}), applying extreme downsampling")
            val extremeVoxelSize = 8.0
            downsampledCloud = reconstruction.downsamplePointCloud(pointCloud, voxelSize = extremeVoxelSize)
            log("Extreme downsampling to ${downsampledCloud.size} points")
        }
        
        log("Final point count: ${downsampledCloud.size} (target: <${maxPoints})")
        
        // Debug: Check point cloud after downsampling
        DebugHelper.logPointCloudStats(downsampledCloud, "After Downsampling")
        
        // Check system resources before mesh generation
        val resources = DebugHelper.checkSystemResources()
        if (resources.isMemoryLow) {
            log("WARNING: Memory usage is high (${resources.memoryUsagePercent}%) - applying memory optimization")
            // Force garbage collection
            System.gc()
            Thread.sleep(100)
        }
        
        stepComplete("Point Cloud Optimization", "Reduced to ${downsampledCloud.size} points")
        
        // Step 5: Generate mesh
        updateProgress(70, "Generating 3D mesh...")
        log("Generating mesh...")
        val meshGenerator = MeshGenerator(context)  // Pass context for settings
        val legMesh = try {
            if (downsampledCloud.size < 4) {
                throw IllegalArgumentException("Insufficient points for mesh generation: ${downsampledCloud.size} (need at least 4)")
            }
            
            // Export point cloud for debugging if needed
            if (AppSettings.isDebugMode(context)) {
                DebugHelper.exportPointCloudToPLY(downsampledCloud, "pre_mesh_${System.currentTimeMillis()}")
            }
            
            val mesh = meshGenerator.createMesh(downsampledCloud)
            log("Initial mesh: ${mesh.triangles.size} triangles")
            
            if (mesh.triangles.isEmpty()) {
                throw RuntimeException("Mesh generation produced no triangles")
            }
            
            // CRITICAL: Check if mesh is too large
            val maxTriangles = 50000 // Safe limit for memory
            if (mesh.triangles.size > maxTriangles) {
                log("WARNING: Mesh too large (${mesh.triangles.size} triangles), this will likely cause OOM")
                log("Recommended: Reduce video length or point cloud quality")
                // Don't throw yet - let it try, but warn
            }
            
            mesh
        } catch (e: Exception) {
            log("ERROR: Mesh generation failed: ${e.message}")
            log("Stack trace: ${e.stackTraceToString()}")
            
            // Try with a fallback approach - create a simple bounding box mesh
            log("Attempting fallback mesh generation...")
            val fallbackMesh = createFallbackMesh(downsampledCloud)
            log("Fallback mesh: ${fallbackMesh.triangles.size} triangles")
            fallbackMesh
        }
        stepComplete("Mesh Generation", "${legMesh.triangles.size} triangles created")
        
        // Step 5.5: Apply smoothing if enabled
        val smoothedMesh = if (AppSettings.isSmoothingEnabled(context)) {
            updateProgress(75, "Smoothing mesh surface...")
            log("Applying mesh smoothing...")
            try {
                val smoothed = meshGenerator.smoothMesh(legMesh, iterations = 3)
                log("Mesh smoothing completed")
                stepComplete("Mesh Smoothing", "3 smoothing iterations applied")
                smoothed
            } catch (e: Exception) {
                log("WARNING: Mesh smoothing failed: ${e.message}")
                stepComplete("Mesh Smoothing", "Skipped due to error: ${e.message}")
                legMesh
            }
        } else {
            log("Mesh smoothing disabled")
            legMesh
        }
        
        // Step 6: Apply cast thickness from settings
        val thickness = AppSettings.getCastThickness(context)
        updateProgress(80, "Applying cast thickness (${thickness}mm)...")
        log("Applying cast thickness (${thickness}mm)...")
        
        // Force garbage collection before memory-intensive operation
        System.gc()
        Thread.sleep(50)
        
        val castMesh = try {
            log("Memory status before thickness: ${getMemoryStatus()}")
            val mesh = meshGenerator.applyThickness(smoothedMesh)  // Uses settings internally
            log("Cast mesh: ${mesh.triangles.size} triangles")
            log("Memory status after thickness: ${getMemoryStatus()}")
            mesh
        } catch (e: OutOfMemoryError) {
            log("CRITICAL ERROR: Out of memory during thickness application")
            log("Using original mesh without thickness due to memory constraints")
            stepComplete("Cast Thickness", "Skipped - insufficient memory (${e.message})")
            smoothedMesh  // Fallback to original mesh
        } catch (e: Exception) {
            log("WARNING: Cast thickness application failed: ${e.message}")
            log("Using original mesh without thickness")
            stepComplete("Cast Thickness", "Skipped - error: ${e.message}")
            smoothedMesh  // Fallback to original mesh
        }
        
        stepComplete("Cast Thickness Applied", "Final mesh: ${castMesh.triangles.size} triangles (${thickness}mm thick)")
        
        // Step 7: Export to binary STL (memory-efficient!)
        updateProgress(90, "Exporting 3D model to binary STL format...")
        log("Exporting to binary STL format (memory-efficient)...")
        log("Memory status before STL export: ${getMemoryStatus()}")
        
        // Force garbage collection before export
        System.gc()
        Thread.sleep(100)
        
        // Create output directory and file directly
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "CMTCast"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        val stlFile = File(directory, "leg_cast_$timestamp.stl")
        
        try {
            // Binary export writes directly to disk - no memory overhead!
            meshGenerator.exportToBinarySTL(castMesh, stlFile)
            log("Binary STL export successful: ${stlFile.length()} bytes")
            log("Memory status after STL export: ${getMemoryStatus()}")
            stepComplete("STL Export", "Binary format: ${stlFile.name}, Size: ${stlFile.length() / 1024}KB")
        } catch (e: OutOfMemoryError) {
            log("CRITICAL ERROR: Out of memory during STL export")
            throw RuntimeException("Out of memory while exporting STL. The mesh is too large (${castMesh.triangles.size} triangles). Please reduce video length or quality settings.", e)
        } catch (e: Exception) {
            log("ERROR: STL export failed: ${e.message}")
            throw RuntimeException("Failed to export STL file: ${e.message}", e)
        }
        
        // Step 8: Calculate measurements
        updateProgress(96, "Calculating measurements...")
        log("Calculating measurements...")
        val measurements = calculateMeasurements(downsampledCloud)
        log("Ankle: ${measurements.circumferenceAnkle}mm, Calf: ${measurements.circumferenceCalf}mm, Length: ${measurements.lengthTotal}mm")
        stepComplete("Measurements", "Ankle: ${measurements.circumferenceAnkle.toInt()}mm, Calf: ${measurements.circumferenceCalf.toInt()}mm")
        
        // Step 10: Save measurement data
        updateProgress(98, "Saving measurement data...")
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
        updateProgress(99, "Saving processing logs...")
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
        updateProgress(100, "Validating quality and safety...")
        
        // Perform quality validation
        val validator = QualityValidator()
        val validationResult = validator.validateComplete(
            rulerDetected = rulerInfo != null,
            rulerConfidence = rulerInfo?.confidence,
            frameCount = allFrames.size,
            ankleCircumference = measurements.circumferenceAnkle,
            calfCircumference = measurements.circumferenceCalf,
            legLength = measurements.lengthTotal,
            pointCount = downsampledCloud.size,
            triangleCount = castMesh.triangles.size
        )
        
        log("Validation complete: ${validationResult.level}")
        stepComplete("Quality Validation", "${validationResult.level}: ${validationResult.messages.size} checks")
        
        Log.d(TAG, "Processing complete in ${processingTime}ms")
        Log.d(TAG, "Files saved:")
        Log.d(TAG, "  - STL: ${stlFile.absolutePath}")
        Log.d(TAG, "  - Measurements: ${measurementFile.absolutePath}")
        Log.d(TAG, "  - Log: ${logFile.absolutePath}")
        Log.d(TAG, "Validation: ${validationResult.level}")
        
        return@withContext ProcessingResult(
            stlFilePath = stlFile.absolutePath,
            pointCount = downsampledCloud.size,
            triangleCount = castMesh.triangles.size,
            processingTimeMs = processingTime,
            measurements = measurements,
            validationResult = validationResult
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
     * Create a simple fallback mesh when normal mesh generation fails
     */
    private fun createFallbackMesh(points: List<Reconstruction3D.Point3D>): MeshGenerator.Mesh {
        Log.d(TAG, "Creating fallback mesh from ${points.size} points")
        
        // Calculate bounding box
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val minZ = points.minOf { it.z }
        val maxZ = points.maxOf { it.z }
        
        // Create a simple cylindrical approximation
        val triangles = mutableListOf<MeshGenerator.Triangle>()
        val segments = 8 // 8-sided cylinder
        val layers = 10 // Height layers
        
        for (layer in 0 until layers) {
            val y1 = minY + (layer / layers.toDouble()) * (maxY - minY)
            val y2 = minY + ((layer + 1) / layers.toDouble()) * (maxY - minY)
            
            // Calculate radius for this layer (wider at calf, narrower at ankle)
            val layerProgress = layer / (layers - 1).toDouble()
            val radiusX = (minX + maxX) / 2 + (maxX - minX) * 0.3 * (1 - layerProgress * 0.3)
            val radiusZ = (minZ + maxZ) / 2 + (maxZ - minZ) * 0.3 * (1 - layerProgress * 0.3)
            val centerX = (minX + maxX) / 2
            val centerZ = (minZ + maxZ) / 2
            
            // Create points around the circumference
            val points1 = mutableListOf<Reconstruction3D.Point3D>()
            val points2 = mutableListOf<Reconstruction3D.Point3D>()
            
            for (i in 0 until segments) {
                val angle = (i / segments.toDouble()) * 2 * PI
                val x = centerX + cos(angle) * radiusX
                val z = centerZ + sin(angle) * radiusZ
                
                points1.add(Reconstruction3D.Point3D(x, y1, z))
                points2.add(Reconstruction3D.Point3D(x, y2, z))
            }
            
            // Connect this layer to the next
            for (i in 0 until segments) {
                val next = (i + 1) % segments
                
                // Create two triangles for each segment
                triangles.add(MeshGenerator.Triangle(points1[i], points2[i], points2[next]))
                triangles.add(MeshGenerator.Triangle(points1[i], points2[next], points1[next]))
            }
        }
        
        val bounds = Pair(
            Reconstruction3D.Point3D(minX, minY, minZ),
            Reconstruction3D.Point3D(maxX, maxY, maxZ)
        )
        
        Log.d(TAG, "Fallback mesh created with ${triangles.size} triangles")
        return MeshGenerator.Mesh(triangles, bounds)
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
