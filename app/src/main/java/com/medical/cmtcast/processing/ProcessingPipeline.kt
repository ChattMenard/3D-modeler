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
        
        Log.d(TAG, "Starting 3D reconstruction pipeline with ${videoUris.size} videos")
        
        // Step 1: Extract frames from all videos
        val videoProcessor = VideoProcessor(context)
        val allFrames = mutableListOf<VideoProcessor.ProcessedFrame>()
        
        for (uri in videoUris) {
            Log.d(TAG, "Processing video: $uri")
            val frames = videoProcessor.extractFrames(uri)
            allFrames.addAll(frames)
        }
        
        Log.d(TAG, "Extracted ${allFrames.size} total frames")
        
        // Step 2: Detect ruler and calculate scale
        val rulerDetector = RulerDetector()
        val frameMats = allFrames.map { it.mat }
        val rulerInfo = rulerDetector.detectRulerMultiFrame(frameMats)
        
        val pixelToMm = rulerInfo?.pixelToMmRatio ?: run {
            Log.w(TAG, "Ruler not detected, using default scale")
            0.5 // Default: 0.5 mm per pixel
        }
        
        Log.d(TAG, "Using scale: $pixelToMm px/mm")
        
        // Step 3: Build 3D point cloud
        val reconstruction = Reconstruction3D()
        val pointCloud = reconstruction.buildPointCloud(frameMats, pixelToMm)
        
        // Step 4: Downsample for performance
        val downsampledCloud = reconstruction.downsamplePointCloud(pointCloud, voxelSize = 2.0)
        
        Log.d(TAG, "Point cloud: ${downsampledCloud.size} points")
        
        // Step 5: Generate mesh
        val meshGenerator = MeshGenerator()
        val legMesh = meshGenerator.createMesh(downsampledCloud)
        
        Log.d(TAG, "Initial mesh: ${legMesh.triangles.size} triangles")
        
        // Step 6: Apply cast thickness
        val castMesh = meshGenerator.applyThickness(legMesh, thickness = 3.0)
        
        // Step 7: Export to STL
        val stlContent = meshGenerator.exportToSTL(castMesh, "cmt_leg_cast")
        
        // Step 8: Save STL file
        val stlFile = saveSTLFile(stlContent)
        
        // Step 9: Calculate measurements
        val measurements = calculateMeasurements(downsampledCloud)
        
        // Cleanup
        videoProcessor.cleanup(allFrames)
        frameMats.forEach { it.release() }
        
        val processingTime = System.currentTimeMillis() - startTime
        
        Log.d(TAG, "Processing complete in ${processingTime}ms")
        Log.d(TAG, "STL saved to: ${stlFile.absolutePath}")
        
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
