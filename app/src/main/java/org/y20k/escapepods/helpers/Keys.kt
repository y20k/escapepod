/*
 * Keys.kt
 * Implements the keys used throughout the app
 * This object hosts all keys used to control Escapepods state
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers


/**
 * Keys object
 */
object Keys {

    // preferences
    val PREF_DOWNLOAD_OVER_MOBILE : String = "DOWNLOAD_OVER_MOBILE"

    // file types
    val RSS : Int = 1
    val AUDIO : Int  = 2
    val IMAGE : Int  = 3

    // folder names
    val AUDIO_FOLDER : String = "audio"
    val IMAGE_FOLDER : String  = "images"
    val TEMP_FOLDER : String  = "temp"

    // intent actions and extras
    val ACTION_DOWNLOAD_PROGRESS_UPDATE : String  = "DOWNLOAD_PROGRESS_UPDATE"
    val EXTRA_DOWNLOAD_ID : String  = "DOWNLOAD_ID"
    val EXTRA_DOWNLOAD_PROGRESS : String  = "DOWNLOAD_PROGRESS"

    // values
    val ONE_SECOND_IN_MILLISECONDS : Long = 1000L
}
