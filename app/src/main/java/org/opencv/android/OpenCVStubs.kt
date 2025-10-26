package org.opencv.android

import android.util.Log

/**
 * Stub implementation of OpenCV loader
 * TODO: Replace with actual OpenCV library
 */
object OpenCVLoader {
    fun initDebug(): Boolean {
        Log.w("OpenCVLoader", "Using stub OpenCV implementation - computer vision features disabled")
        return false
    }
}

object Utils {
    fun bitmapToMat(bitmap: android.graphics.Bitmap, mat: org.opencv.core.Mat) {
        // Stub implementation
        Log.w("Utils", "OpenCV not available")
    }
}
