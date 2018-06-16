package org.y20k.escapepods.helpers

import android.app.DownloadManager
import android.content.Context
import android.net.Uri

class DownloadHelper (private val downloadManager : DownloadManager) {

    fun download (context: Context, uri: Uri, type: Int) : Long {
        val folder : String
        when (type) {
            Keys.RSS -> folder = Keys.TEMP_FOLDER
            Keys.AUDIO -> folder = Keys.AUDIO_FOLDER
            Keys.IMAGE -> folder = Keys.IMAGE_FOLDER
            else -> folder = "/"
        }
        return downloadManager.enqueue(DownloadManager.Request(uri)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(uri.lastPathSegment)
                .setDestinationInExternalFilesDir(context, folder, uri.lastPathSegment)
        )
    }

}