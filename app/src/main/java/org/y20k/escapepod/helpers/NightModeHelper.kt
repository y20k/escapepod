/*
 * NightModeHelper.kt
 * Implements the NightModeHelper object
 * A NightModeHelper can toggle and restore the state of the theme's Night Mode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import org.y20k.escapepod.R


/*
 * NightModeHelper object
 */
object NightModeHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NightModeHelper::class.java)


    /* Switches between modes: day, night, undefined */
    @SuppressLint("SwitchIntDef")
    fun switchMode(activity: Activity) {
        // SWITCH: undefined -> night / night -> day / day - undefined
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> {
                // currently: day mode -> switch to: follow system
                displayDefaultStatusBar(activity) // necessary hack :-/
                activateFollowSystemMode(activity, true)
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                // currently: night mode -> switch to: day mode
                displayLightStatusBar(activity) // necessary hack :-/
                activateDayMode(activity, true)
            }
            else -> {
                // currently: follow system / undefined -> switch to: day mode
                displayLightStatusBar(activity) // necessary hack :-/
                activateNightMode(activity, true)
            }
        }
    }


    /* Sets night mode / dark theme */
    fun restoreSavedState(context: Context) {
        val savedNightModeState = PreferencesHelper.loadNightModeState(context)
        val currentNightModeState = AppCompatDelegate.getDefaultNightMode()
        if (savedNightModeState != currentNightModeState) {
            when (savedNightModeState) {
                AppCompatDelegate.MODE_NIGHT_NO ->
                    // turn on day mode
                    activateDayMode(context, false)
                AppCompatDelegate.MODE_NIGHT_YES ->
                    // turn on night mode
                    activateNightMode(context, false)
                else ->
                    // turn on mode "follow system"
                    activateFollowSystemMode(context, false)
            }
        }
    }


    /* Activates Night Mode */
    private fun activateNightMode(context: Context, notifyUser: Boolean) {
        PreferencesHelper.saveNightModeState(context, AppCompatDelegate.MODE_NIGHT_YES)

        // switch to Night Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // notify user
        if (notifyUser) {
            Toast.makeText(context, context.getText(R.string.toast_message_theme_night), Toast.LENGTH_LONG).show()
        }
    }


    /* Activates Day Mode */
    private fun activateDayMode(context: Context, notifyUser: Boolean) {
        // save the new state
        PreferencesHelper.saveNightModeState(context, AppCompatDelegate.MODE_NIGHT_NO)

        // switch to Day Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // notify user
        if (notifyUser) {
            Toast.makeText(context, context.getText(R.string.toast_message_theme_day), Toast.LENGTH_LONG).show()
        }
    }


    /* Activate Mode "Follow System" */
    private fun activateFollowSystemMode(context: Context, notifyUser: Boolean) {
        // save the new state
        PreferencesHelper.saveNightModeState(context, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // switch to Undefined Mode / Follow System
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // notify user
        if (notifyUser) {
            Toast.makeText(context, context.getText(R.string.toast_message_theme_follow_system), Toast.LENGTH_LONG).show()
        }
    }


    /* Displays the default status bar */
    private fun displayDefaultStatusBar(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = 0
    }


    /* Displays the light (inverted) status bar */
    private fun displayLightStatusBar(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

}