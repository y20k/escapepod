/*
 * SettingsFragment.kt
 * Implements the SettingsFragment fragment
 * A SettingsFragment displays the user accessible settings of the app
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import org.y20k.escapepod.helpers.LogHelper


/*
 * SettingsFragment class
 */
class SettingsFragment: PreferenceFragmentCompat() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(SettingsFragment::class.java)


    /* Overrides onViewCreated from PreferenceFragmentCompat */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // set the background color
        view.setBackgroundColor(resources.getColor(R.color.app_window_background, null))
        // add padding - necessary because translucent status bar is used
        val topPadding = this.resources.displayMetrics.density * 24 // 24 dp * display density
        view.setPadding(0, topPadding.toInt(), 0, 0)
    }


    /* Overrides onCreatePreferences from PreferenceFragmentCompat */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)




        preferenceScreen = screen
    }

}