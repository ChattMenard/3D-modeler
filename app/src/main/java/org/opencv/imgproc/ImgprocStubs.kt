package org.opencv.imgproc

import org.opencv.core.*

/**
 * Stub implementations of OpenCV imgproc classes
 * TODO: Replace with actual OpenCV library
 */
object Imgproc {
    const val COLOR_RGBA2BGR = 2
    const val COLOR_BGR2GRAY = 6
    const val COLOR_BGR2HSV = 40
    const val RETR_EXTERNAL = 0
    const val CHAIN_APPROX_SIMPLE = 2
    const val MORPH_CLOSE = 3
    const val MORPH_OPEN = 2
    const val MORPH_ELLIPSE = 2
    
    fun cvtColor(src: Mat, dst: Mat, code: Int) {}
    fun Canny(image: Mat, edges: Mat, threshold1: Double, threshold2: Double) {}
    fun findContours(image: Mat, contours: MutableList<MatOfPoint>, hierarchy: Mat, mode: Int, method: Int) {}
    fun boundingRect(points: MatOfPoint): Rect = Rect()
    fun contourArea(contour: MatOfPoint): Double = 0.0
    fun resize(src: Mat, dst: Mat, dsize: Size) {}
    fun morphologyEx(src: Mat, dst: Mat, op: Int, kernel: Mat) {}
    fun getStructuringElement(shape: Int, ksize: Size): Mat = Mat()
}
