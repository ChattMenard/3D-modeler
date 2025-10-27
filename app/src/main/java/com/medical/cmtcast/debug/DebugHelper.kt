package com.medical.cmtcast.debug

import android.util.Log
import java.io.File
import java.io.FileWriter

/**
 * Debug utilities for troubleshooting video analysis failures
 */
object DebugHelper {
    private const val TAG = "DebugHelper"
    
    /**
     * Log point cloud statistics for debugging
     */
    fun logPointCloudStats(points: List<com.medical.cmtcast.processing.Reconstruction3D.Point3D>, stage: String) {
        Log.d(TAG, "=== Point Cloud Stats - $stage ===")
        Log.d(TAG, "Total points: ${points.size}")
        
        if (points.isEmpty()) {
            Log.w(TAG, "Point cloud is empty!")
            return
        }
        
        // Check for invalid points
        val validPoints = points.filter { 
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite() 
        }
        val invalidCount = points.size - validPoints.size
        
        if (invalidCount > 0) {
            Log.w(TAG, "Invalid points found: $invalidCount")
        }
        
        if (validPoints.isEmpty()) {
            Log.e(TAG, "No valid points after filtering!")
            return
        }
        
        // Calculate bounds
        val minX = validPoints.minOf { it.x }
        val maxX = validPoints.maxOf { it.x }
        val minY = validPoints.minOf { it.y }
        val maxY = validPoints.maxOf { it.y }
        val minZ = validPoints.minOf { it.z }
        val maxZ = validPoints.maxOf { it.z }
        
        Log.d(TAG, "Bounds X: $minX to $maxX (range: ${maxX - minX})")
        Log.d(TAG, "Bounds Y: $minY to $maxY (range: ${maxY - minY})")
        Log.d(TAG, "Bounds Z: $minZ to $maxZ (range: ${maxZ - minZ})")
        
        // Calculate density
        val volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ)
        val density = if (volume > 0) validPoints.size / volume else 0.0
        Log.d(TAG, "Point density: ${"%.3f".format(density)} points/mm³")
        
        // Check for height variation (critical for slicing)
        val heightRange = maxY - minY
        if (heightRange < 10) {
            Log.w(TAG, "WARNING: Very small height range ($heightRange mm) - may cause mesh generation issues")
        }
        
        // Sample some points for visual inspection
        if (validPoints.size >= 5) {
            Log.d(TAG, "Sample points:")
            val samples = listOf(0, validPoints.size / 4, validPoints.size / 2, 3 * validPoints.size / 4, validPoints.size - 1)
            samples.forEach { index ->
                val p = validPoints[index]
                Log.d(TAG, "  Point $index: (${p.x}, ${p.y}, ${p.z})")
            }
        }
        
        Log.d(TAG, "=== End Point Cloud Stats ===")
    }
    
    /**
     * Export point cloud to PLY format for external analysis
     */
    fun exportPointCloudToPLY(
        points: List<com.medical.cmtcast.processing.Reconstruction3D.Point3D>, 
        filename: String
    ): String? {
        return try {
            val file = File("/sdcard/Documents/debug_$filename.ply")
            FileWriter(file).use { writer ->
                writer.write("ply\n")
                writer.write("format ascii 1.0\n")
                writer.write("element vertex ${points.size}\n")
                writer.write("property float x\n")
                writer.write("property float y\n")
                writer.write("property float z\n")
                writer.write("end_header\n")
                
                points.forEach { point ->
                    writer.write("${point.x} ${point.y} ${point.z}\n")
                }
            }
            
            Log.d(TAG, "Point cloud exported to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export point cloud: ${e.message}")
            null
        }
    }
    
    /**
     * Check system resources before intensive operations
     */
    fun checkSystemResources(): ResourceStatus {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory
        
        val memoryUsagePercent = (usedMemory.toDouble() / maxMemory * 100).toInt()
        
        Log.d(TAG, "=== Memory Status ===")
        Log.d(TAG, "Max memory: ${maxMemory / (1024 * 1024)}MB")
        Log.d(TAG, "Total memory: ${totalMemory / (1024 * 1024)}MB") 
        Log.d(TAG, "Used memory: ${usedMemory / (1024 * 1024)}MB")
        Log.d(TAG, "Available memory: ${availableMemory / (1024 * 1024)}MB")
        Log.d(TAG, "Memory usage: $memoryUsagePercent%")
        
        // Force garbage collection if memory is getting low
        if (memoryUsagePercent > 75) {
            Log.w(TAG, "Memory usage high, requesting garbage collection")
            System.gc()
            Thread.sleep(100) // Give GC time to run
            
            // Recalculate after GC
            val newUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            val newMemoryUsagePercent = (newUsedMemory.toDouble() / maxMemory * 100).toInt()
            Log.d(TAG, "After GC - Used memory: ${newUsedMemory / (1024 * 1024)}MB ($newMemoryUsagePercent%)")
        }
        
        return ResourceStatus(
            memoryUsagePercent = memoryUsagePercent,
            availableMemoryMB = (availableMemory / (1024 * 1024)).toInt(),
            isMemoryLow = memoryUsagePercent > 80,
            maxMemoryMB = (maxMemory / (1024 * 1024)).toInt(),
            usedMemoryMB = (usedMemory / (1024 * 1024)).toInt()
        )
    }
    
    data class ResourceStatus(
        val memoryUsagePercent: Int,
        val availableMemoryMB: Int,
        val isMemoryLow: Boolean,
        val maxMemoryMB: Int,
        val usedMemoryMB: Int
    )
    
    /**
     * Log detailed mesh generation parameters
     */
    fun logMeshGenerationDebug(
        points: List<com.medical.cmtcast.processing.Reconstruction3D.Point3D>,
        sliceHeight: Double,
        numSlices: Int
    ) {
        Log.d(TAG, "=== Mesh Generation Debug ===")
        Log.d(TAG, "Input points: ${points.size}")
        Log.d(TAG, "Slice height: ${sliceHeight}mm")
        Log.d(TAG, "Number of slices: $numSlices")
        
        // Analyze slice distribution
        val sortedPoints = points.sortedBy { it.y }
        val minY = sortedPoints.first().y
        
        for (i in 0 until numSlices) {
            val y1 = minY + i * sliceHeight
            val y2 = minY + (i + 1) * sliceHeight
            val slicePoints = sortedPoints.filter { it.y >= y1 && it.y < y2 }
            
            Log.d(TAG, "Slice $i (Y: ${y1.toInt()}-${y2.toInt()}mm): ${slicePoints.size} points")
            
            if (slicePoints.size < 3) {
                Log.w(TAG, "  WARNING: Slice $i has insufficient points for triangulation")
            }
        }
        
        Log.d(TAG, "=== End Mesh Generation Debug ===")
    }
}