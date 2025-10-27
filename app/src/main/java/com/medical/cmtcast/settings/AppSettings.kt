package com.medical.cmtcast.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Helper class to access app settings from anywhere
 */
object AppSettings {
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            SettingsActivity.SettingsFragment.PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }
    
    // Cast Settings
    fun getCastThickness(context: Context): Float {
        return getPrefs(context).getFloat(
            SettingsActivity.SettingsFragment.KEY_CAST_THICKNESS,
            SettingsActivity.SettingsFragment.DEFAULT_CAST_THICKNESS
        )
    }
    
    // Ruler Settings
    fun getRulerLength(context: Context): Float {
        return getPrefs(context).getFloat(
            SettingsActivity.SettingsFragment.KEY_RULER_LENGTH,
            SettingsActivity.SettingsFragment.DEFAULT_RULER_LENGTH
        )
    }
    
    fun getRulerUnit(context: Context): String {
        return getPrefs(context).getString(
            SettingsActivity.SettingsFragment.KEY_RULER_UNIT,
            SettingsActivity.SettingsFragment.DEFAULT_RULER_UNIT
        ) ?: SettingsActivity.SettingsFragment.DEFAULT_RULER_UNIT
    }
    
    fun getRulerLengthInMm(context: Context): Float {
        val length = getRulerLength(context)
        val unit = getRulerUnit(context)
        return if (unit == "in") {
            length * 25.4f  // Convert inches to mm
        } else {
            length * 10f  // Convert cm to mm
        }
    }
    
    // Processing Settings
    fun getFrameRate(context: Context): Int {
        return getPrefs(context).getInt(
            SettingsActivity.SettingsFragment.KEY_FRAME_RATE,
            SettingsActivity.SettingsFragment.DEFAULT_FRAME_RATE
        )
    }
    
    fun getFrameIntervalMs(context: Context): Long {
        val fps = getFrameRate(context)
        return (1000L / fps)  // Convert fps to milliseconds between frames
    }
    
    fun getMeshDetail(context: Context): String {
        return getPrefs(context).getString(
            SettingsActivity.SettingsFragment.KEY_MESH_DETAIL,
            SettingsActivity.SettingsFragment.DEFAULT_MESH_DETAIL
        ) ?: SettingsActivity.SettingsFragment.DEFAULT_MESH_DETAIL
    }
    
    fun getMeshDetailFactor(context: Context): Float {
        return when (getMeshDetail(context)) {
            "low" -> 0.5f
            "high" -> 2.0f
            else -> 1.0f  // medium
        }
    }
    
    fun isSmoothingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("enable_smoothing", false)
    }
    
    // Display Settings
    fun getMeasurementUnit(context: Context): String {
        return getPrefs(context).getString(
            SettingsActivity.SettingsFragment.KEY_MEASUREMENT_UNIT,
            SettingsActivity.SettingsFragment.DEFAULT_MEASUREMENT_UNIT
        ) ?: SettingsActivity.SettingsFragment.DEFAULT_MEASUREMENT_UNIT
    }
    
    fun convertMeasurement(valueInMm: Float, context: Context): Pair<Float, String> {
        val unit = getMeasurementUnit(context)
        return when (unit) {
            "cm" -> Pair(valueInMm / 10f, "cm")
            "in" -> Pair(valueInMm / 25.4f, "in")
            else -> Pair(valueInMm, "mm")
        }
    }
    
    fun formatMeasurement(valueInMm: Float, context: Context): String {
        val (value, unit) = convertMeasurement(valueInMm, context)
        return String.format("%.1f %s", value, unit)
    }
    
    fun showValidationDetails(context: Context): Boolean {
        return getPrefs(context).getBoolean("show_validation_details", true)
    }
    
    // Advanced Settings
    fun shouldSaveIntermediateFiles(context: Context): Boolean {
        return getPrefs(context).getBoolean("save_intermediate_files", true)
    }
    
    fun isDebugMode(context: Context): Boolean {
        return getPrefs(context).getBoolean("enable_debug_mode", false)
    }
}
