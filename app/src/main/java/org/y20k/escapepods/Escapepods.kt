/*
 * Escapepods.kt
 * Implements the Escapepods class
 * Escapepods is the base Application class that sets up day and night theme
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.app.Application
import android.os.Build
import android.support.v7.app.AppCompatDelegate
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.NightModeHelper


/**
 * Escapepods.class
 */
class Escapepods: Application () {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(Escapepods::class.java)


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()

        // set Day / Night theme state
        if (Build.VERSION.SDK_INT >= 28) {
            // Android P might introduce a system wide theme option - in that case: follow system (28 = Build.VERSION_CODES.P)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            // try to get last state the user chose
            NightModeHelper.restoreSavedState(this);
        }
        LogHelper.v(TAG, "Escapepods application started.")
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        LogHelper.v(TAG, "Escapepods application terminated.")
    }

}