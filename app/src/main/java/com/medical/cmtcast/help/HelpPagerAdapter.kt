package com.medical.cmtcast.help

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class HelpPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 5
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HelpGuideFragment()
            1 -> HelpSetupFragment()
            2 -> HelpRecordingFragment()
            3 -> HelpSafetyFragment()
            4 -> HelpTroubleshootingFragment()
            else -> HelpGuideFragment()
        }
    }
}
