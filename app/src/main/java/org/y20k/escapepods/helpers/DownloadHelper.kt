/*
 * DownloadHelper.kt
 * Implements the DownloadHelper class
 * A DownloadHelper provides helper methods for downloading files
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import java.net.URL


/*
 * DownloadHelper class
 */
class DownloadHelper {

    /* Checks if given feed string is XML */
    fun determineMimeType(feedUrl: String): String {
        // FIRST check if NOT an URL
        if (!feedUrl.startsWith("http", true)) return Keys.MIME_TYPE_UNSUPPORTED
        if (!feedUrlIsParsable(feedUrl)) return Keys.MIME_TYPE_UNSUPPORTED

        // THEN check for type
        if (feedUrl.endsWith("xml", true)) return Keys.MIME_TYPE_XML
        if (feedUrl.endsWith("mp3", true)) return Keys.MIME_TYPE_MP3
        if (feedUrl.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (feedUrl.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (feedUrl.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG

        // todo implement a real mime type check
        // https://developer.android.com/reference/java/net/URLConnection#guessContentTypeFromName(java.lang.String)

        return Keys.MIME_TYPE_UNSUPPORTED
    }


    /* Tries to parse feed URL string as URL */
    private fun feedUrlIsParsable(feedUrl: String): Boolean {
        try {
            URL(feedUrl)
        } catch (e: Exception) {
            return false
        }
        return true
    }
}