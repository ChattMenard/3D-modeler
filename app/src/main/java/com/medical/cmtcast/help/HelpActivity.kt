package com.medical.cmtcast.help

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.medical.cmtcast.R

class HelpActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnClose: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        
        // Setup toolbar
        supportActionBar?.apply {
            title = "Help & Instructions"
            setDisplayHomeAsUpEnabled(true)
        }
        
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        btnClose = findViewById(R.id.btnClose)
        
        // Setup ViewPager with help sections
        val adapter = HelpPagerAdapter(this)
        viewPager.adapter = adapter
        
        // Connect TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "📖 Guide"
                1 -> "📏 Setup"
                2 -> "🎥 Recording"
                3 -> "⚠️ Safety"
                4 -> "🔧 Troubleshooting"
                else -> "Help"
            }
        }.attach()
        
        btnClose.setOnClickListener {
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
