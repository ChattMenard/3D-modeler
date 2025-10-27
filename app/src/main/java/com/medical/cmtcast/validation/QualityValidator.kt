package com.medical.cmtcast.validation

import android.util.Log

/**
 * Validates quality and safety of measurements and 3D reconstruction
 */
class QualityValidator {
    
    companion object {
        private const val TAG = "QualityValidator"
        
        // Medical measurement ranges (in mm)
        private const val MIN_ANKLE_CIRCUMFERENCE = 180.0  // 18cm
        private const val MAX_ANKLE_CIRCUMFERENCE = 350.0  // 35cm
        private const val MIN_CALF_CIRCUMFERENCE = 250.0   // 25cm
        private const val MAX_CALF_CIRCUMFERENCE = 500.0   // 50cm
        private const val MIN_LEG_LENGTH = 200.0           // 20cm
        private const val MAX_LEG_LENGTH = 500.0           // 50cm
        
        // Processing quality thresholds
        private const val MIN_RULER_CONFIDENCE = 0.3
        private const val RECOMMENDED_RULER_CONFIDENCE = 0.5
        private const val MIN_FRAME_COUNT = 50
        private const val RECOMMENDED_FRAME_COUNT = 100
        private const val MIN_POINT_COUNT = 1000
        private const val RECOMMENDED_POINT_COUNT = 5000
        private const val MIN_TRIANGLE_COUNT = 500
    }
    
    enum class ValidationLevel {
        OK,      // Everything looks good
        WARNING, // Issues detected but can proceed
        ERROR    // Critical issues, should not proceed
    }
    
    data class ValidationResult(
        val level: ValidationLevel,
        val messages: List<String>,
        val details: Map<String, Any> = emptyMap()
    ) {
        fun hasErrors(): Boolean = level == ValidationLevel.ERROR
        fun hasWarnings(): Boolean = level == ValidationLevel.WARNING
        fun isOk(): Boolean = level == ValidationLevel.OK
    }
    
    /**
     * Validate ruler detection quality
     */
    fun validateRulerDetection(
        rulerDetected: Boolean,
        confidence: Double?,
        frameCount: Int
    ): ValidationResult {
        val messages = mutableListOf<String>()
        var level = ValidationLevel.OK
        
        if (!rulerDetected || confidence == null) {
            level = ValidationLevel.ERROR
            messages.add("❌ Ruler not detected in any frame")
            messages.add("• Please ensure ruler is clearly visible")
            messages.add("• Place ruler vertically next to leg")
            messages.add("• Ensure good lighting conditions")
        } else if (confidence < MIN_RULER_CONFIDENCE) {
            level = ValidationLevel.ERROR
            messages.add("❌ Ruler detection confidence too low: ${(confidence * 100).toInt()}%")
            messages.add("• Minimum required: ${(MIN_RULER_CONFIDENCE * 100).toInt()}%")
            messages.add("• Retake videos with better ruler visibility")
        } else if (confidence < RECOMMENDED_RULER_CONFIDENCE) {
            level = ValidationLevel.WARNING
            messages.add("⚠️ Ruler detection confidence below recommended: ${(confidence * 100).toInt()}%")
            messages.add("• Recommended: ${(RECOMMENDED_RULER_CONFIDENCE * 100).toInt()}%")
            messages.add("• Measurements may be less accurate")
            messages.add("• Consider retaking for better results")
        } else {
            messages.add("✓ Ruler detected successfully: ${(confidence * 100).toInt()}% confidence")
        }
        
        Log.d(TAG, "Ruler validation: $level - ${messages.joinToString(", ")}")
        
        return ValidationResult(
            level = level,
            messages = messages,
            details = mapOf(
                "rulerDetected" to rulerDetected,
                "confidence" to (confidence ?: 0.0),
                "frameCount" to frameCount
            )
        )
    }
    
    /**
     * Validate frame count is adequate
     */
    fun validateFrameCount(frameCount: Int): ValidationResult {
        val messages = mutableListOf<String>()
        var level = ValidationLevel.OK
        
        when {
            frameCount < MIN_FRAME_COUNT -> {
                level = ValidationLevel.ERROR
                messages.add("❌ Insufficient frames: $frameCount")
                messages.add("• Minimum required: $MIN_FRAME_COUNT frames")
                messages.add("• Record longer videos or more angles")
            }
            frameCount < RECOMMENDED_FRAME_COUNT -> {
                level = ValidationLevel.WARNING
                messages.add("⚠️ Frame count below recommended: $frameCount")
                messages.add("• Recommended: $RECOMMENDED_FRAME_COUNT+ frames")
                messages.add("• More frames = better accuracy")
            }
            else -> {
                messages.add("✓ Frame count adequate: $frameCount frames")
            }
        }
        
        Log.d(TAG, "Frame count validation: $level - $frameCount frames")
        
        return ValidationResult(
            level = level,
            messages = messages,
            details = mapOf("frameCount" to frameCount)
        )
    }
    
    /**
     * Validate leg measurements are within realistic ranges
     */
    fun validateMeasurements(
        ankleCircumference: Double,
        calfCircumference: Double,
        legLength: Double
    ): ValidationResult {
        val messages = mutableListOf<String>()
        var level = ValidationLevel.OK
        
        // Validate ankle
        when {
            ankleCircumference < MIN_ANKLE_CIRCUMFERENCE || ankleCircumference > MAX_ANKLE_CIRCUMFERENCE -> {
                level = ValidationLevel.ERROR
                messages.add("❌ Ankle circumference out of range: ${ankleCircumference.toInt()}mm")
                messages.add("• Expected range: ${MIN_ANKLE_CIRCUMFERENCE.toInt()}-${MAX_ANKLE_CIRCUMFERENCE.toInt()}mm")
                messages.add("• Check ruler calibration and retake videos")
            }
            else -> {
                messages.add("✓ Ankle: ${ankleCircumference.toInt()}mm (within normal range)")
            }
        }
        
        // Validate calf
        when {
            calfCircumference < MIN_CALF_CIRCUMFERENCE || calfCircumference > MAX_CALF_CIRCUMFERENCE -> {
                if (level != ValidationLevel.ERROR) level = ValidationLevel.ERROR
                messages.add("❌ Calf circumference out of range: ${calfCircumference.toInt()}mm")
                messages.add("• Expected range: ${MIN_CALF_CIRCUMFERENCE.toInt()}-${MAX_CALF_CIRCUMFERENCE.toInt()}mm")
            }
            else -> {
                messages.add("✓ Calf: ${calfCircumference.toInt()}mm (within normal range)")
            }
        }
        
        // Validate length
        when {
            legLength < MIN_LEG_LENGTH || legLength > MAX_LEG_LENGTH -> {
                if (level != ValidationLevel.ERROR) level = ValidationLevel.ERROR
                messages.add("❌ Leg length out of range: ${legLength.toInt()}mm")
                messages.add("• Expected range: ${MIN_LEG_LENGTH.toInt()}-${MAX_LEG_LENGTH.toInt()}mm")
            }
            else -> {
                messages.add("✓ Length: ${legLength.toInt()}mm (within normal range)")
            }
        }
        
        // Check proportions
        if (calfCircumference < ankleCircumference) {
            if (level == ValidationLevel.OK) level = ValidationLevel.WARNING
            messages.add("⚠️ Calf smaller than ankle - unusual proportions")
            messages.add("• This may indicate measurement errors")
        }
        
        Log.d(TAG, "Measurement validation: $level")
        
        return ValidationResult(
            level = level,
            messages = messages,
            details = mapOf(
                "ankle" to ankleCircumference,
                "calf" to calfCircumference,
                "length" to legLength
            )
        )
    }
    
    /**
     * Validate 3D reconstruction quality
     */
    fun validateReconstruction(
        pointCount: Int,
        triangleCount: Int
    ): ValidationResult {
        val messages = mutableListOf<String>()
        var level = ValidationLevel.OK
        
        // Validate point count
        when {
            pointCount < MIN_POINT_COUNT -> {
                level = ValidationLevel.ERROR
                messages.add("❌ Insufficient 3D points: $pointCount")
                messages.add("• Minimum required: $MIN_POINT_COUNT points")
                messages.add("• Retake videos with better coverage")
            }
            pointCount < RECOMMENDED_POINT_COUNT -> {
                level = ValidationLevel.WARNING
                messages.add("⚠️ Low point count: $pointCount")
                messages.add("• Recommended: $RECOMMENDED_POINT_COUNT+ points")
                messages.add("• Model may lack detail")
            }
            else -> {
                messages.add("✓ Point cloud quality good: $pointCount points")
            }
        }
        
        // Validate triangle count
        when {
            triangleCount < MIN_TRIANGLE_COUNT -> {
                if (level != ValidationLevel.ERROR) level = ValidationLevel.ERROR
                messages.add("❌ Insufficient mesh triangles: $triangleCount")
                messages.add("• Minimum required: $MIN_TRIANGLE_COUNT triangles")
            }
            else -> {
                messages.add("✓ Mesh quality adequate: $triangleCount triangles")
            }
        }
        
        Log.d(TAG, "Reconstruction validation: $level - $pointCount points, $triangleCount triangles")
        
        return ValidationResult(
            level = level,
            messages = messages,
            details = mapOf(
                "pointCount" to pointCount,
                "triangleCount" to triangleCount
            )
        )
    }
    
    /**
     * Perform complete validation of entire pipeline
     */
    fun validateComplete(
        rulerDetected: Boolean,
        rulerConfidence: Double?,
        frameCount: Int,
        ankleCircumference: Double,
        calfCircumference: Double,
        legLength: Double,
        pointCount: Int,
        triangleCount: Int
    ): ValidationResult {
        val allMessages = mutableListOf<String>()
        var worstLevel = ValidationLevel.OK
        
        // Run all validations
        val rulerResult = validateRulerDetection(rulerDetected, rulerConfidence, frameCount)
        val frameResult = validateFrameCount(frameCount)
        val measurementResult = validateMeasurements(ankleCircumference, calfCircumference, legLength)
        val reconstructionResult = validateReconstruction(pointCount, triangleCount)
        
        // Collect messages
        allMessages.add("=== QUALITY VALIDATION REPORT ===\n")
        
        allMessages.add("📏 RULER CALIBRATION:")
        allMessages.addAll(rulerResult.messages)
        allMessages.add("")
        
        allMessages.add("🎥 VIDEO QUALITY:")
        allMessages.addAll(frameResult.messages)
        allMessages.add("")
        
        allMessages.add("📐 MEASUREMENTS:")
        allMessages.addAll(measurementResult.messages)
        allMessages.add("")
        
        allMessages.add("🔺 3D RECONSTRUCTION:")
        allMessages.addAll(reconstructionResult.messages)
        allMessages.add("")
        
        // Determine worst level
        listOf(rulerResult, frameResult, measurementResult, reconstructionResult).forEach { result ->
            if (result.level == ValidationLevel.ERROR) {
                worstLevel = ValidationLevel.ERROR
            } else if (result.level == ValidationLevel.WARNING && worstLevel != ValidationLevel.ERROR) {
                worstLevel = ValidationLevel.WARNING
            }
        }
        
        // Add summary
        when (worstLevel) {
            ValidationLevel.OK -> {
                allMessages.add("✅ ALL CHECKS PASSED")
                allMessages.add("Model is ready for 3D printing")
            }
            ValidationLevel.WARNING -> {
                allMessages.add("⚠️ WARNINGS DETECTED")
                allMessages.add("Review warnings before proceeding")
                allMessages.add("Consider retaking videos for better quality")
            }
            ValidationLevel.ERROR -> {
                allMessages.add("❌ CRITICAL ERRORS DETECTED")
                allMessages.add("DO NOT USE THIS MODEL")
                allMessages.add("Please retake videos and process again")
            }
        }
        
        Log.i(TAG, "Complete validation: $worstLevel")
        
        return ValidationResult(
            level = worstLevel,
            messages = allMessages,
            details = mapOf(
                "rulerConfidence" to (rulerConfidence ?: 0.0),
                "frameCount" to frameCount,
                "measurements" to mapOf(
                    "ankle" to ankleCircumference,
                    "calf" to calfCircumference,
                    "length" to legLength
                ),
                "reconstruction" to mapOf(
                    "points" to pointCount,
                    "triangles" to triangleCount
                )
            )
        )
    }
}
