/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import org.y20k.escapepod.Keys
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NetworkHelper::class.java)


    /* Data class: holder for content type information */
    data class ContentType(var type: String = String(), var charset: String = String())


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


    /* Checks if the active network connection is over VPN */
    fun isConnectedToVpn(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a VPN connection is active
                result = capabilities.hasTransport(TRANSPORT_VPN)
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
            val contentType: ContentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
            val connection: HttpURLConnection? = createConnection(urlString)
            if (connection != null) {
                val contentTypeHeader: String = connection.contentType ?: String()
                val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
                contentTypeHeaderParts.forEachIndexed { index, part ->
                    if (index == 0 && part.isNotEmpty()) {
                        contentType.type = part
                    } else if (part.contains("charset=")) {
                        contentType.charset = part.substringAfter("charset=")
                    }
                }
                connection.disconnect()
            }
            LogHelper.i(TAG, "content type: ${contentType.type} | character set: ${contentType.charset}")
            cont.resume(contentType)
        }
    }


    /* Creates a http connection from given url string */
    private fun createConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection? {
        var connection: HttpURLConnection? = null

        try {
            // try to open connection and get status
            LogHelper.i(TAG, "Opening http connection.")
            connection = URL(urlString).openConnection() as HttpURLConnection
            val status = connection.responseCode

            // CHECK for non-HTTP_OK status
            if (status != HttpURLConnection.HTTP_OK) {
                // CHECK for redirect status
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    val redirectUrl: String = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectCount < 5) {
                        LogHelper.i(TAG, "Following redirect to $redirectUrl")
                        connection = createConnection(redirectUrl, redirectCount + 1)
                    } else {
                        connection = null
                        LogHelper.e(TAG, "Too many redirects.")
                    }
                }
            }

        } catch (e: Exception) {
            LogHelper.e(TAG, "Unable to open http connection.")
            e.printStackTrace()
        }

        return connection
    }


}