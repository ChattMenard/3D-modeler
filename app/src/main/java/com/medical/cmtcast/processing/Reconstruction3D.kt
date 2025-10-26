package com.medical.cmtcast.processing

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Creates 3D point cloud from video frames
 */
class Reconstruction3D {
    
    companion object {
        private const val TAG = "Reconstruction3D"
    }
    
    data class Point3D(
        val x: Double,
        val y: Double,
        val z: Double
    )
    
    data class LegContour(
        val points: List<Point>,
        val frameIndex: Int,
        val rotationAngle: Double
    )
    
    /**
     * Extract leg contour from frame
     */
    fun extractLegContour(frame: Mat): MatOfPoint? {
        try {
            // Convert to HSV for better skin detection
            val hsv = Mat()
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV)
            
            // Skin color range (adjust based on lighting)
            val lowerSkin = Scalar(0.0, 20.0, 70.0)
            val upperSkin = Scalar(20.0, 255.0, 255.0)
            
            // Create mask for skin
            val mask = Mat()
            Core.inRange(hsv, lowerSkin, upperSkin, mask)
            
            // Morphological operations to clean up mask
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            
            // Find contours
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Find largest contour (should be the leg)
            val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
            
            // Cleanup
            hsv.release()
            mask.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { if (it != largestContour) it.release() }
            
            return largestContour
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting leg contour", e)
            return null
        }
    }
    
    /**
     * Build 3D point cloud from multiple views
     */
    fun buildPointCloud(
        frames: List<Mat>,
        pixelToMm: Double,
        totalRotationDegrees: Double = 360.0
    ): List<Point3D> {
        val pointCloud = mutableListOf<Point3D>()
        
        Log.d(TAG, "Building point cloud from ${frames.size} frames")
        
        frames.forEachIndexed { index, frame ->
            // Calculate rotation angle for this frame
            val rotationAngle = (index.toDouble() / frames.size) * totalRotationDegrees
            val angleRad = Math.toRadians(rotationAngle)
            
            // Extract leg contour
            val contour = extractLegContour(frame)
            
            if (contour != null) {
                val points = contour.toArray()
                
                // Convert 2D contour points to 3D using rotation
                for (point in points) {
                    // Center the coordinates
                    val x2d = (point.x - frame.width() / 2.0) * pixelToMm
                    val y2d = (point.y - frame.height() / 2.0) * pixelToMm
                    
                    // Apply rotation to create 3D point
                    // Assuming leg is vertical (Y axis) and camera rotates around it
                    val x3d = x2d * cos(angleRad)
                    val y3d = y2d // Height stays the same
                    val z3d = x2d * sin(angleRad)
                    
                    pointCloud.add(Point3D(x3d, y3d, z3d))
                }
                
                contour.release()
                
                if (index % 10 == 0) {
                    Log.d(TAG, "Processed frame $index/${frames.size}, angle: $rotationAngle°")
                }
            }
        }
        
        Log.d(TAG, "Generated ${pointCloud.size} 3D points")
        return pointCloud
    }
    
    /**
     * Simplify point cloud by voxel downsampling
     */
    fun downsamplePointCloud(points: List<Point3D>, voxelSize: Double = 2.0): List<Point3D> {
        val voxelMap = mutableMapOf<Triple<Int, Int, Int>, MutableList<Point3D>>()
        
        // Group points into voxels
        for (point in points) {
            val voxelX = (point.x / voxelSize).toInt()
            val voxelY = (point.y / voxelSize).toInt()
            val voxelZ = (point.z / voxelSize).toInt()
            val key = Triple(voxelX, voxelY, voxelZ)
            
            voxelMap.getOrPut(key) { mutableListOf() }.add(point)
        }
        
        // Average points in each voxel
        val downsampled = voxelMap.values.map { voxelPoints ->
            Point3D(
                x = voxelPoints.map { it.x }.average(),
                y = voxelPoints.map { it.y }.average(),
                z = voxelPoints.map { it.z }.average()
            )
        }
        
        Log.d(TAG, "Downsampled from ${points.size} to ${downsampled.size} points")
        return downsampled
    }
    
    /**
     * Calculate bounding box of point cloud
     */
    fun calculateBounds(points: List<Point3D>): Pair<Point3D, Point3D> {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val minZ = points.minOf { it.z }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val maxZ = points.maxOf { it.z }
        
        return Pair(
            Point3D(minX, minY, minZ),
            Point3D(maxX, maxY, maxZ)
        )
    }
}
