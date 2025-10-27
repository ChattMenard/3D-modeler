package com.medical.cmtcast.processing

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects ruler in images and calculates pixel-to-mm scale
 */
class RulerDetector {
    
    companion object {
        private const val TAG = "RulerDetector"
        private const val RULER_LENGTH_MM = 300.0 // Standard 30cm ruler
    }
    
    data class RulerInfo(
        val pixelToMmRatio: Double,
        val rulerRect: Rect,
        val confidence: Double
    )
    
    /**
     * Detect ruler and calculate scale from frame
     */
    fun detectRuler(frame: Mat): RulerInfo? {
        try {
            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
            
            // Apply edge detection
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            
            // Find contours
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Look for rectangular contours (ruler should be rectangular)
            var bestRuler: RulerInfo? = null
            var maxArea = 0.0
            
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val area = (rect.width * rect.height).toDouble()
                
                // Filter by aspect ratio (ruler should be long and thin)
                val aspectRatio = rect.height.toDouble() / rect.width.toDouble()
                
                // Ruler should be vertical (height > width)
                if (aspectRatio > 3.0 && aspectRatio < 15.0 && area > maxArea) {
                    val pixelLength = rect.height.toDouble()
                    // CORRECT: mm per pixel (how many mm does 1 pixel represent)
                    val mmPerPixel = RULER_LENGTH_MM / pixelLength
                    
                    // Confidence based on how close it is to expected aspect ratio
                    val confidence = calculateConfidence(aspectRatio, area, frame.width() * frame.height())
                    
                    if (confidence > 0.3) { // Minimum confidence threshold
                        Log.d(TAG, "Ruler candidate: ${pixelLength.toInt()}px high, scale: $mmPerPixel mm/px")
                        bestRuler = RulerInfo(
                            pixelToMmRatio = mmPerPixel,  // This is mm/pixel (correct!)
                            rulerRect = rect,
                            confidence = confidence
                        )
                        maxArea = area
                    }
                }
            }
            
            // Cleanup
            gray.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
            
            if (bestRuler != null) {
                Log.d(TAG, "✓ Ruler detected: height=${bestRuler.rulerRect.height}px, " +
                        "width=${bestRuler.rulerRect.width}px, " +
                        "ratio=${bestRuler.rulerRect.height.toDouble() / bestRuler.rulerRect.width}:1, " +
                        "scale=${bestRuler.pixelToMmRatio} mm/px, " +
                        "confidence=${bestRuler.confidence}")
                Log.d(TAG, "  → If this is 300mm ruler: 1 pixel = ${bestRuler.pixelToMmRatio}mm")
                Log.d(TAG, "  → Ruler appears to be ${bestRuler.rulerRect.height}px × ${bestRuler.pixelToMmRatio}mm = " +
                        "${bestRuler.rulerRect.height * bestRuler.pixelToMmRatio}mm tall")
            } else {
                Log.w(TAG, "✗ No ruler detected in frame")
            }
            
            return bestRuler
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting ruler", e)
            return null
        }
    }
    
    /**
     * Calculate confidence score for ruler detection
     */
    private fun calculateConfidence(aspectRatio: Double, area: Double, frameArea: Int): Double {
        // Ideal aspect ratio for ruler is around 10:1
        val aspectScore = 1.0 - Math.min(1.0, Math.abs(aspectRatio - 10.0) / 10.0)
        
        // Ruler should occupy 5-30% of frame
        val areaRatio = area / frameArea.toDouble()
        val areaScore = if (areaRatio in 0.05..0.3) 1.0 else 0.5
        
        return (aspectScore * 0.7 + areaScore * 0.3)
    }
    
    /**
     * Detect ruler across multiple frames and get average scale
     */
    fun detectRulerMultiFrame(frames: List<Mat>): RulerInfo? {
        val detections = frames.mapNotNull { detectRuler(it) }
        
        if (detections.isEmpty()) {
            Log.w(TAG, "No ruler detected in any frame")
            return null
        }
        
        // Use detection with highest confidence
        val best = detections.maxByOrNull { it.confidence }
        
        // Calculate average from top 3 detections for better accuracy
        val topDetections = detections.sortedByDescending { it.confidence }.take(3)
        val avgRatio = topDetections.map { it.pixelToMmRatio }.average()
        
        Log.d(TAG, "Average pixel-to-mm ratio: $avgRatio (from ${topDetections.size} detections)")
        
        return best?.copy(pixelToMmRatio = avgRatio)
    }
}
