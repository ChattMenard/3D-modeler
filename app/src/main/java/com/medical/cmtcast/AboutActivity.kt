package com.medical.cmtcast

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.medical.cmtcast.disclaimer.DisclaimerActivity

class AboutActivity : AppCompatActivity() {

    private lateinit var tvVersion: TextView
    private lateinit var tvBuildInfo: TextView
    private lateinit var btnViewDisclaimer: Button
    private lateinit var btnGitHub: Button
    private lateinit var btnClose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        tvVersion = findViewById(R.id.tvVersion)
        tvBuildInfo = findViewById(R.id.tvBuildInfo)
        btnViewDisclaimer = findViewById(R.id.btnViewDisclaimer)
        btnGitHub = findViewById(R.id.btnGitHub)
        btnClose = findViewById(R.id.btnClose)

        // Get version info
        val versionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (e: Exception) {
            "Unknown"
        }

        val versionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }

        tvVersion.text = "Version $versionName (Build $versionCode)"
        tvBuildInfo.text = buildString {
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Build Type: Debug")
        }

        // View disclaimer button
        btnViewDisclaimer.setOnClickListener {
            val intent = Intent(this, DisclaimerActivity::class.java)
            startActivity(intent)
        }

        // GitHub repository button
        btnGitHub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ChattMenard/3D-modeler"))
            startActivity(intent)
        }

        // Close button
        btnClose.setOnClickListener {
            finish()
        }
    }
}
