package com.medical.cmtcast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.medical.cmtcast.camera.CameraActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartMeasurement: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnViewInstructions: Button
    private lateinit var btnMedicalDisclaimer: Button
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        btnStartMeasurement = findViewById(R.id.btnStartMeasurement)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnViewInstructions = findViewById(R.id.btnViewInstructions)
        btnMedicalDisclaimer = findViewById(R.id.btnMedicalDisclaimer)
        
        setupUI()
        
        // Request permissions immediately on startup
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            checkPermissions()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check permissions every time we resume
        if (!hasAllPermissions()) {
            tvPermissionStatus.text = "⚠️ Camera permission required - tap Start to grant"
            requestPermissions()
        } else {
            checkPermissions()
        }
    }
    
    private fun setupUI() {
        btnStartMeasurement.setOnClickListener {
            if (hasAllPermissions()) {
                startCameraActivity()
            } else {
                requestPermissions()
            }
        }
        
        btnViewInstructions.setOnClickListener {
            showInstructions()
        }
        
        btnMedicalDisclaimer.setOnClickListener {
            showMedicalDisclaimer()
        }
    }
    
    private fun checkPermissions() {
        if (!hasAllPermissions()) {
            btnStartMeasurement.isEnabled = true
            btnStartMeasurement.text = "Grant Camera Permission"
            tvPermissionStatus.text = "⚠️ Camera permission required"
        } else {
            btnStartMeasurement.isEnabled = true
            btnStartMeasurement.text = "Start Leg Measurement"
            tvPermissionStatus.text = "✅ Ready to start"
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            CAMERA_PERMISSION_REQUEST
        )
    }
    
    private fun startCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }
    
    private fun showInstructions() {
        Toast.makeText(this, "Instructions: Position ruler next to leg, capture multiple angles", Toast.LENGTH_LONG).show()
    }
    
    private fun showMedicalDisclaimer() {
        Toast.makeText(this, "Medical Disclaimer: This is a prototype. Consult medical professionals.", Toast.LENGTH_LONG).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkPermissions()
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "✅ Camera ready! Tap Start to begin", Toast.LENGTH_SHORT).show()
            // Auto-launch camera if permissions just granted
            startCameraActivity()
        } else {
            Toast.makeText(this, "⚠️ Camera permission required to measure leg", Toast.LENGTH_LONG).show()
        }
    }
}