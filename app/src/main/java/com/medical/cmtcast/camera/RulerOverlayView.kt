package com.medical.cmtcast.camera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Overlay view to show real-time ruler detection feedback
 */
class RulerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rulerRect: RectF? = null
    private var confidence: Double = 0.0
    private var isRulerDetected: Boolean = false

    private val paintGood = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val paintBad = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    /**
     * Update ruler detection results
     */
    fun updateRulerDetection(rect: RectF?, confidence: Double) {
        this.rulerRect = rect
        this.confidence = confidence
        this.isRulerDetected = rect != null && confidence > 0.3
        invalidate() // Trigger redraw
    }

    /**
     * Clear detection
     */
    fun clearDetection() {
        this.rulerRect = null
        this.confidence = 0.0
        this.isRulerDetected = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rulerRect?.let { rect ->
            // Draw bounding box around detected ruler
            val paint = if (confidence > 0.5) paintGood else paintBad
            canvas.drawRect(rect, paint)

            // Draw confidence text
            val confidenceText = "Ruler: ${(confidence * 100).toInt()}%"
            val textX = rect.left
            val textY = rect.top - 10f
            canvas.drawText(confidenceText, textX, textY, textPaint)

            // Draw status message
            val statusText = when {
                confidence > 0.7 -> "✓ Ruler Detected - Good!"
                confidence > 0.5 -> "Ruler Detected - Hold Steady"
                confidence > 0.3 -> "Ruler Detected - Improve Position"
                else -> "Keep Searching..."
            }
            
            val statusX = width / 2f - textPaint.measureText(statusText) / 2f
            val statusY = 100f
            canvas.drawText(statusText, statusX, statusY, textPaint)
        }

        // If no ruler detected, show hint
        if (!isRulerDetected) {
            val hintText = "Place ruler next to leg"
            val hintX = width / 2f - textPaint.measureText(hintText) / 2f
            val hintY = 100f
            textPaint.color = Color.YELLOW
            canvas.drawText(hintText, hintX, hintY, textPaint)
            textPaint.color = Color.WHITE
        }
    }
}
