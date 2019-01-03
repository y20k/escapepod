/*
 * Escapepods.kt
 * Implements the Escapepods class
 * Escapepods is the base Application class that sets up day and night theme
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.app.Application
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
        LogHelper.v(TAG, "Escapepods application started.")
        // set Day / Night theme state
        NightModeHelper.restoreSavedState(this);
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        LogHelper.v(TAG, "Escapepods application terminated.")
    }

}