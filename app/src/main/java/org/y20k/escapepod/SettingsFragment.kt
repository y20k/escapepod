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

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import org.y20k.escapepod.helpers.AppThemeHelper
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
        // show action bar
        (activity as AppCompatActivity).supportActionBar?.show()
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as AppCompatActivity).supportActionBar?.title = getString(R.string.fragment_settings_title)
    }


    /* Overrides onCreatePreferences from PreferenceFragmentCompat */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // set up "App Theme" preference
        val preferenceThemeSelection: ListPreference = ListPreference(activity as Context)
        preferenceThemeSelection.title = getString(R.string.pref_theme_selection_title)
        preferenceThemeSelection.key = Keys.PREF_THEME_SELECTION
        preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${AppThemeHelper.getCurrentTheme(activity as Context)}"
        preferenceThemeSelection.entries = arrayOf(getString(R.string.pref_theme_selection_mode_device_default), getString(R.string.pref_theme_selection_mode_light), getString(R.string.pref_theme_selection_mode_dark))
        preferenceThemeSelection.entryValues = arrayOf(Keys.STATE_THEME_FOLLOW_SYSTEM, Keys.STATE_THEME_LIGHT_MODE, Keys.STATE_THEME_DARK_MODE)
        preferenceThemeSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${preference.entries.get(index)}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
        }


        // set preference categories
        val preferenceCategoryGeneral: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryGeneral.title = getString(R.string.pref_general_title)
        // preferenceCategoryGeneral.contains(preferenceImperialMeasurementUnits)
        // preferenceCategoryGeneral.contains(preferenceGpsOnly)
        // preferenceCategoryGeneral.contains(preferenceThemeSelection)
        val preferenceCategoryAdvanced: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryAdvanced.title = getString(R.string.pref_advanced_title)
        // preferenceCategoryAdvanced.contains(preferenceAccuracyThreshold)
        // preferenceCategoryAdvanced.contains(preferenceResetAdvanced)

        // setup preference screen
        screen.addPreference(preferenceCategoryGeneral)
        // screen.addPreference(preferenceGpsOnly)
        // screen.addPreference(preferenceImperialMeasurementUnits)
        screen.addPreference(preferenceThemeSelection)
        screen.addPreference(preferenceCategoryAdvanced)
        // screen.addPreference(preferenceAccuracyThreshold)
        // screen.addPreference(preferenceResetAdvanced)


        preferenceScreen = screen
    }

}