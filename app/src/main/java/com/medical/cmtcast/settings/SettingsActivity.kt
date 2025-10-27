package com.medical.cmtcast.settings

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.medical.cmtcast.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        
        findViewById<Button>(R.id.btnResetDefaults)?.setOnClickListener {
            resetToDefaults()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun resetToDefaults() {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(SettingsFragment.KEY_CAST_THICKNESS, 3.0f)
            putFloat(SettingsFragment.KEY_RULER_LENGTH, 30.0f)
            putString(SettingsFragment.KEY_RULER_UNIT, "cm")
            putInt(SettingsFragment.KEY_FRAME_RATE, 10)
            putString(SettingsFragment.KEY_MESH_DETAIL, "medium")
            putString(SettingsFragment.KEY_MEASUREMENT_UNIT, "mm")
            apply()
        }
        
        // Recreate to refresh UI
        recreate()
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        companion object {
            const val PREFS_NAME = "CMTCastSettings"
            
            // Cast settings
            const val KEY_CAST_THICKNESS = "cast_thickness"
            const val DEFAULT_CAST_THICKNESS = 3.0f
            
            // Ruler settings
            const val KEY_RULER_LENGTH = "ruler_length"
            const val DEFAULT_RULER_LENGTH = 30.0f  // 30cm or 12 inches
            const val KEY_RULER_UNIT = "ruler_unit"
            const val DEFAULT_RULER_UNIT = "cm"
            
            // Processing settings
            const val KEY_FRAME_RATE = "frame_rate"
            const val DEFAULT_FRAME_RATE = 10  // fps
            const val KEY_MESH_DETAIL = "mesh_detail"
            const val DEFAULT_MESH_DETAIL = "medium"
            
            // Display settings
            const val KEY_MEASUREMENT_UNIT = "measurement_unit"
            const val DEFAULT_MEASUREMENT_UNIT = "mm"
        }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = PREFS_NAME
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }
}
