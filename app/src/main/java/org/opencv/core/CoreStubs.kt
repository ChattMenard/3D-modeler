package org.opencv.core

/**
 * Stub implementations of OpenCV core classes
 * TODO: Replace with actual OpenCV library
 */
class Mat {
    fun width(): Int = 1920
    fun height(): Int = 1080
    fun release() {}
}

class MatOfPoint {
    fun toArray(): Array<Point> = emptyArray()
    fun release() {}
}

class Point(val x: Double = 0.0, val y: Double = 0.0)

class Scalar(
    val v0: Double = 0.0,
    val v1: Double = 0.0,
    val v2: Double = 0.0,
    val v3: Double = 0.0
)

class Size(val width: Double = 0.0, val height: Double = 0.0)

class Rect(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

object Core {
    fun inRange(src: Mat, lowerb: Scalar, upperb: Scalar, dst: Mat) {}
}
