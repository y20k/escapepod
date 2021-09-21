/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import android.net.Uri
import org.y20k.escapepod.BuildConfig
import org.y20k.escapepod.Keys
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
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


    /* Creates header for podcastindex.org request */
    fun createPodcastIndexRequestHeader(): HashMap<String, String> {
        val params = HashMap<String, String>()

        // preparation
        val currentDate: Date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).time
        val secondsSinceEpoch: Long = currentDate.time / 1000L
        val apiHeaderTime: String = "$secondsSinceEpoch"

        // create authentication hash
        val data4Hash = Keys.PODCASTINDEX_API_KEY + StringHelper.decrypt(Keys.PODCASTINDEX_API_KEY2) + apiHeaderTime
        val hashString: String = StringHelper.createSha1(data4Hash)

        // set up header parameters
        params["X-Auth-Date"] = apiHeaderTime
        params["X-Auth-Key"] = Keys.PODCASTINDEX_API_KEY
        params["Authorization"] = hashString
        params["User-Agent"] = "$Keys.APPLICATION_NAME ${BuildConfig.VERSION_NAME}"

        return params
    }


    /* Creates header for gpodder.net request */
    fun createGpodderRequestHeader(): HashMap<String, String> {
        // set up header parameters
        val params = HashMap<String, String>()
        params["User-Agent"] = "$Keys.APPLICATION_NAME ${BuildConfig.VERSION_NAME}"

        return params
    }


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }


    /* Suspend function: Detect content type (mime type) - async using coroutine */
    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        return suspendCoroutine { cont ->
            LogHelper.v(TAG, "Determining content type - Thread: ${Thread.currentThread().name}")
            cont.resume(detectContentType(urlString))
        }
    }


    /* Suspend function: get redirected (real) URL - async using coroutine */
    suspend fun resolveRedirectsSuspended(urlString: String): String {
        return suspendCoroutine { cont ->
            LogHelper.v(TAG, "Resolving redirects - Thread: ${Thread.currentThread().name}")
            cont.resume(resolveRedirects(urlString))
        }
    }


    /* Detect content type (mime type) from given URL string  */
    private fun detectContentType(urlString: String): ContentType {
        val contentType: ContentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
        val connection: HttpURLConnection? = createConnection(urlString)
        if (connection != null) {
            val contentTypeHeader: String = connection.contentType ?: String()
            LogHelper.v(TAG, "Raw content type header: $contentTypeHeader")
            val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
            contentTypeHeaderParts.forEachIndexed { index, part ->
                if (index == 0 && part.isNotEmpty()) {
                    contentType.type = part.trim()
                } else if (part.contains("charset=")) {
                    contentType.charset = part.substringAfter("charset=").trim()
                }
            }
            connection.disconnect()
        }
        LogHelper.i(TAG, "content type: ${contentType.type} | character set: ${contentType.charset}")
        return contentType
    }


    /* Checks if content type of remote file is supported */
    private fun checkContentTypeSupported(context: Context, uri: Uri): Boolean {
        val contentType: ContentType = detectContentType(uri.toString())
        val supportedContentTypes = Keys.MIME_TYPES_RSS + Keys.MIME_TYPES_AUDIO + Keys.MIME_TYPES_IMAGE
        return contentType.type in supportedContentTypes
    }


    /* Get redirected (real) URL */
    private fun resolveRedirects(urlString: String): String {
        var redirectedURL: String = urlString
        val connection: HttpURLConnection? = createConnection(urlString) // createConnection() resolves redirects
        if (connection != null) {
            redirectedURL = connection.url.toString()
            connection.disconnect()
        }
        LogHelper.i(TAG, "Resolved URL: $redirectedURL")
        return redirectedURL
    }


    /* Creates a http connection from given url string */
    private fun createConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection? {
        var connection: HttpURLConnection? = null

        try {
            // try to open connection and get status
            LogHelper.i(TAG, "Opening http connection: $urlString")
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