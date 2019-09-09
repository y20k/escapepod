/*
 * AudioHelper.kt
 * Implements the AudioHelper object
 * A AudioHelper provides helper methods for handling audio files
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri


/*
 * AudioHelper object
 */
object AudioHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(AudioHelper::class.java)


    /* Extract duration from audio file */
    fun getDuration(context: Context, audioFileUri: Uri): Long {
        val metadataRetriever: MediaMetadataRetriever = MediaMetadataRetriever()
        metadataRetriever.setDataSource(context, audioFileUri)
        val durationString: String = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        return durationString.toLong()
    }

}