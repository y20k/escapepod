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

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.preference.PreferenceManager
import androidx.core.content.edit
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.*


/*
 * DownloadHelper class
 */
class DownloadHelper {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Saves active downloads (IntArray) to shared preferences */
    fun saveActiveDownloads(context: Context, activeDownloads: ArrayList<Long>) {
        val builder = StringBuilder()
        for (i in activeDownloads.indices) {
            builder.append(activeDownloads[i]).append(",")
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {putString(Keys.PREF_ACTIVE_DOWNLOADS, builder.toString())}
    }


    /* Loads active downloads (IntArray) from shared preferences */
    fun loadActiveDownloads(context: Context, downloadManager: DownloadManager): ArrayList<Long> {
        val activeDownloadsString: String = PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_ACTIVE_DOWNLOADS, "")
        val count = activeDownloadsString.split(",").size - 1
        val tokenizer = StringTokenizer(activeDownloadsString, ",")
        val activeDownloads: ArrayList<Long> = arrayListOf<Long>()
        repeat(count) {
            val token = tokenizer.nextToken().toLong()
            if (isDownloadActive(downloadManager, token)) {
                activeDownloads.add(token) }
        }
        return activeDownloads
    }


    /* Determines the remote file location (the original URL) */
    fun getRemoteFileLocation(downloadManager: DownloadManager, downloadID: Long): String {
        var remoteFileLocation: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
        }
        return remoteFileLocation
    }


    /* Checks if a given download ID represents a finished download */
    fun isDownloadFinished(downloadManager: DownloadManager, downloadID: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus == DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Checks if a given download ID represents a finished download */
    fun isDownloadActive(downloadManager: DownloadManager, downloadID: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus == DownloadManager.STATUS_RUNNING)
    }


    /* Determine allowed network type */
    fun determineAllowedNetworkTypes(context: Context, type: Int): Int {
        val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_DOWNLOAD_OVER_MOBILE);
        var allowedNetworkTypes:Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        when (type) {
            Keys.FILE_TYPE_AUDIO -> if (!downloadOverMobile) allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
        }
        return allowedNetworkTypes
    }


    /* Checks if given feed string is XML */
    fun determineMimeType(feedUrl: String): String {
        // FIRST check if NOT an URL
        if (!feedUrl.startsWith("http", true)) return Keys.MIME_TYPE_UNSUPPORTED
        if (!feedUrlIsParsable(feedUrl)) return Keys.MIME_TYPE_UNSUPPORTED

        // THEN check for type
        if (feedUrl.endsWith("xml", true)) return Keys.MIME_TYPE_XML
        if (feedUrl.endsWith("rss", true)) return Keys.MIME_TYPE_XML
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


    fun identifyFileTypeUsingUrlConnectionGetContentType(feedUrl: String): String {
        var fileType = "Undetermined"
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection()
            fileType = connection.contentType
        } catch (badUrlEx: MalformedURLException) {
            LogHelper.w("ERROR: Bad URL - $badUrlEx")
        } catch (ioEx: IOException) {
            LogHelper.w("Cannot access URLConnection - $ioEx")
        }
        if (fileType == "application/rss+xml") {
            return Keys.MIME_TYPE_XML
        } else {
            return Keys.MIME_TYPE_UNSUPPORTED
        }
    }


}