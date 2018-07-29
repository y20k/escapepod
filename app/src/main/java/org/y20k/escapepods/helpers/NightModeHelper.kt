/*
 * NightModeHelper.kt
 * Implements the NightModeHelper object
 * A NightModeHelper can toggle and restore the state of the theme's Night Mode
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.preference.PreferenceManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate


object NightModeHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NightModeHelper::class.java)


    /* Switches to opposite theme */
    fun switchToOpposite(activity: AppCompatActivity) {
        when (getCurrentNightModeState(activity)) {
            Configuration.UI_MODE_NIGHT_NO -> {
                // night mode is currently not active - turn on night mode
                displayDefaultStatusBar(activity) // necessary hack :-/
                activateNightMode(activity)
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                // night mode is currently active - turn off night mode
                displayLightStatusBar(activity) // necessary hack :-/
                deactivateNightMode(activity)
            }
            Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                // don't know what mode is active - turn off night mode
                displayLightStatusBar(activity) // necessary hack :-/
                deactivateNightMode(activity)
            }
        }
    }


    /* Sets night mode / dark theme */
    fun restoreSavedState(context: Context) {
        val savedNightModeState = loadNightModeState(context)
        val currentNightModeState = getCurrentNightModeState(context)
        if (savedNightModeState != -1 && savedNightModeState != currentNightModeState) {
            when (savedNightModeState) {
                Configuration.UI_MODE_NIGHT_NO ->
                    // turn off night mode
                    deactivateNightMode(context)
                Configuration.UI_MODE_NIGHT_YES ->
                    // turn on night mode
                    activateNightMode(context)
                Configuration.UI_MODE_NIGHT_UNDEFINED ->
                    // turn off night mode
                    deactivateNightMode(context)
            }
        }
    }


    /* Returns state of night mode */
    private fun getCurrentNightModeState(context: Context): Int {
        return context.getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
    }


    /* Activates Night Mode */
    private fun activateNightMode(context: Context) {
        saveNightModeState(context, Configuration.UI_MODE_NIGHT_YES)

        // switch to Night Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }


    /* Deactivates Night Mode */
    private fun deactivateNightMode(context: Context) {
        // save the new state
        saveNightModeState(context, Configuration.UI_MODE_NIGHT_NO)

        // switch to Day Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }


    /* Displays the default status bar */
    private fun displayDefaultStatusBar(activity: AppCompatActivity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = 0
    }


    /* Displays the light (inverted) status bar - if possible */
    private fun displayLightStatusBar(activity: AppCompatActivity) {
        val decorView = activity.window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decorView.systemUiVisibility = 0
        }
    }


    /* Save state of night mode */
    private fun saveNightModeState(context: Context, currentState: Int) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putInt(Keys.PREF_NIGHT_MODE_STATE, currentState)
        editor.apply()
    }


    /* Load state of Night Mode */
    private fun loadNightModeState(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NIGHT_MODE_STATE, -1)
    }


}