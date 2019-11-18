/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import org.y20k.escapepod.Keys
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NetworkHelper::class.java)


    /* Data class: holder for content type information */
    data class ContentType(var type: String = String(), var charset: String = String()) {    }


    /* Checks if the active network connection is over Wifi */
    fun isConnectedToWifi(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a Wifi connection is active
                result = capabilities.hasTransport(TRANSPORT_WIFI)
            }
        }
        return result
    }


    /* Checks if the active network connection is over Cellular */
    fun isConnectedToCellular(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a cellular connection is active
                result = capabilities.hasTransport(TRANSPORT_CELLULAR)
            }
        }
        return result
    }


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }


    /* Suspend function: Detects content type (mime type) from given URL string - async using coroutine */
    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        return suspendCoroutine { cont ->
            LogHelper.v(TAG, "Determining content type - Thread: ${Thread.currentThread().name}")
            val CONTENT_TYPE_PATTERN:  Pattern  = Pattern.compile("([^;]*)(; ?charset=([^;]+))?")
            val contentType: ContentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
            var connection: HttpURLConnection? = null
            try {
                // try to open connection
                try {
                    LogHelper.i(TAG, "Opening http connection.")
                    connection = URL(urlString).openConnection() as HttpURLConnection
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Unable to open http connection.")
                    e.printStackTrace()
                }

                // get content type if connection has been established
                if (connection != null) {
                    // handle redirects
                    var redirect: Boolean = false
                    val status: Int = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                            redirect = true
                        }
                    }
                    if (redirect) {
                        // get redirect url from "location" header field
                        LogHelper.i(TAG, "Following a redirect.")
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as HttpURLConnection
                    }

                    // extract content type from connection
                    val contentTypeHeader: String = connection.contentType
                    val matcher = CONTENT_TYPE_PATTERN.matcher(contentTypeHeader.trim().toLowerCase(Locale.ENGLISH))
                    if (matcher.matches()) {
                        val contentTypeString: String = matcher.group (1) ?: Keys.MIME_TYPE_UNSUPPORTED
                        val charsetString: String = matcher.group (3) ?: Keys.CHARSET_UNDEFINDED
                        contentType.type = contentTypeString.trim()
                        contentType.charset = charsetString.trim()
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            } finally {
                connection?.disconnect()
            }
            LogHelper.i(TAG, "content type: ${contentType.type} / character set: ${contentType.charset}")
            cont.resume(contentType)
        }
    }


}