package com.medical.cmtcast.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Extracts frames from video files for 3D reconstruction
 */
class VideoProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoProcessor"
        private const val FRAME_INTERVAL_MS = 100L // Extract frame every 100ms (10 fps)
    }
    
    data class ProcessedFrame(
        val timestamp: Long,
        val bitmap: Bitmap,
        val mat: Mat
    )
    
    /**
     * Extract frames from video at regular intervals
     */
    suspend fun extractFrames(videoUri: Uri): List<ProcessedFrame> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<ProcessedFrame>()
        val retriever = MediaMetadataRetriever()
        
        try {
            retriever.setDataSource(context, videoUri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Log.d(TAG, "Video duration: ${duration}ms")
            
            var currentTime = 0L
            while (currentTime < duration) {
                val bitmap = retriever.getFrameAtTime(
                    currentTime * 1000, // Convert to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                
                if (bitmap != null) {
                    // Convert bitmap to OpenCV Mat for processing
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)
                    
                    // Convert to proper color space
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
                    
                    frames.add(ProcessedFrame(currentTime, bitmap, mat))
                    Log.d(TAG, "Extracted frame at ${currentTime}ms")
                }
                
                currentTime += FRAME_INTERVAL_MS
            }
            
            Log.d(TAG, "Extracted ${frames.size} frames from video")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frames", e)
        } finally {
            retriever.release()
        }
        
        return@withContext frames
    }
    
    /**
     * Downsample frames for faster processing
     */
    fun downsampleFrame(mat: Mat, maxWidth: Int = 800): Mat {
        val scale = maxWidth.toDouble() / mat.width()
        if (scale >= 1.0) return mat
        
        val newSize = Size(maxWidth.toDouble(), (mat.height() * scale))
        val resized = Mat()
        Imgproc.resize(mat, resized, newSize)
        return resized
    }
    
    /**
     * Clean up resources
     */
    fun cleanup(frames: List<ProcessedFrame>) {
        frames.forEach { frame ->
            frame.mat.release()
            frame.bitmap.recycle()
        }
    }
}
