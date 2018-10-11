/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NetworkHelper::class.java)


    /* Checks if the active network connection is over Wifi */
    fun isConnectedToWifi(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork == null){
            return false
        } else {
            return connMgr.getNetworkCapabilities(activeNetwork).hasTransport(TRANSPORT_WIFI)
        }
    }


    /* Checks if the active network connection is over Cellular */
    fun isConnectedToCellular(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork == null){
            return false
        } else {
            return connMgr.getNetworkCapabilities(activeNetwork).hasTransport(TRANSPORT_CELLULAR)
        }
    }

}