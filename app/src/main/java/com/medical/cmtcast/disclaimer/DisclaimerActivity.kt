package com.medical.cmtcast.disclaimer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.medical.cmtcast.MainActivity
import com.medical.cmtcast.R

class DisclaimerActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var checkboxNotFDA: CheckBox
    private lateinit var checkboxUnderstand: CheckBox
    private lateinit var checkboxConsult: CheckBox
    private lateinit var btnAccept: Button
    private lateinit var btnDecline: Button

    companion object {
        private const val PREFS_NAME = "CMTCastPrefs"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        
        /**
         * Check if user has already accepted disclaimer
         */
        fun hasAcceptedDisclaimer(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        }
        
        /**
         * Mark disclaimer as accepted
         */
        fun setDisclaimerAccepted(context: Context, accepted: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, accepted).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disclaimer)

        scrollView = findViewById(R.id.scrollView)
        checkboxNotFDA = findViewById(R.id.checkboxNotFDA)
        checkboxUnderstand = findViewById(R.id.checkboxUnderstand)
        checkboxConsult = findViewById(R.id.checkboxConsult)
        btnAccept = findViewById(R.id.btnAccept)
        btnDecline = findViewById(R.id.btnDecline)

        setupButtons()
    }

    private fun setupButtons() {
        // Enable accept button only when all checkboxes are checked
        val checkboxes = listOf(checkboxNotFDA, checkboxUnderstand, checkboxConsult)
        
        checkboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                btnAccept.isEnabled = checkboxes.all { it.isChecked }
            }
        }
        
        btnAccept.setOnClickListener {
            if (checkboxNotFDA.isChecked && checkboxUnderstand.isChecked && checkboxConsult.isChecked) {
                acceptDisclaimer()
            } else {
                Toast.makeText(
                    this,
                    "Please read and accept all terms to continue",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        btnDecline.setOnClickListener {
            Toast.makeText(
                this,
                "You must accept the terms to use this app",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun acceptDisclaimer() {
        setDisclaimerAccepted(this, true)
        
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from bypassing disclaimer
        Toast.makeText(
            this,
            "You must accept or decline the terms",
            Toast.LENGTH_SHORT
        ).show()
    }
}
