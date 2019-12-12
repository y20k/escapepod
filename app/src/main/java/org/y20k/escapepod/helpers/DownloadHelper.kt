/*
 * DownloadHelper.kt
 * Implements the DownloadHelper object
 * A DownloadHelper provides helper methods for downloading files
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.core.Podcast
import org.y20k.escapepod.extensions.copy
import org.y20k.escapepod.xml.RssHelper
import java.util.*


/*
 * DownloadHelper object
 */
object DownloadHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Main class variables */
    private lateinit var collection: Collection
    private lateinit var downloadManager: DownloadManager
    private lateinit var activeDownloads: ArrayList<Long>
    private lateinit var modificationDate: Date


    /* Download a podcast */
    fun downloadPodcasts(context: Context, podcastUrlStrings: Array<String>) {
        // initialize main class variables, if necessary
        initialize(context)
        // convert array
        val uris: Array<Uri> = Array<Uri>(podcastUrlStrings.size) { index -> Uri.parse(podcastUrlStrings[index]) }
        // enqueue podcast
        enqueueDownload(context, uris, Keys.FILE_TYPE_RSS)
    }


    /* Download an episode */
    fun downloadEpisode(context: Context, mediaId: String, ignoreWifiRestriction: Boolean, manuallyDownloaded: Boolean = false) {
        // initialize main class variables, if necessary
        initialize(context)
        // get episode
        val episode: Episode = CollectionHelper.getEpisode(collection, mediaId)
        // mark as manually downloaded if necessary
        if (manuallyDownloaded) {
            collection = CollectionHelper.setManuallyDownloaded(collection, mediaId, manuallyDownloaded = true)
            saveCollection(context)
        }
        // enqueue episode
        val uris = Array(1) { episode.remoteAudioFileLocation.toUri() }
        enqueueDownload(context, uris, Keys.FILE_TYPE_AUDIO, episode.podcastName, ignoreWifiRestriction)
    }


    /* Refresh cover of given podcast */
    fun refreshCover(context: Context, podcast: Podcast) {
        // initialize main class variables, if necessary
        initialize(context)
        // check if feed has a cover
        if (podcast.remoteImageFileLocation.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_message_refreshing_cover), Toast.LENGTH_LONG).show()
            CollectionHelper.clearImagesFolder(context, podcast)
            val uris: Array<Uri> = Array(1) { podcast.remoteImageFileLocation.toUri() }
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE, podcast.name)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_message_error_refreshing_cover), Toast.LENGTH_LONG).show()
        }
    }


    /* Updates podcast collection */
    fun updateCollection(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        // re-download all podcast xml episode lists
        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(context)) {
            PreferencesHelper.saveLastUpdateCollection(context)
            val uris: Array<Uri> = Array(collection.podcasts.size) { it ->
                collection.podcasts[it].remotePodcastFeedLocation.toUri()
            }
            enqueueDownload(context, uris, Keys.FILE_TYPE_RSS)
        } else {
            LogHelper.v(TAG, "Update not initiated: not enough time has passed since last update.")
        }
    }


    /* Updates all podcast covers */
    fun updateCovers(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        // re-download all podcast covers
        PreferencesHelper.saveLastUpdateCollection(context)
        val uris: Array<Uri> = Array(collection.podcasts.size) { it ->
            collection.podcasts[it].remoteImageFileLocation.toUri()
        }
        enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
        LogHelper.i(TAG, "Updating all covers.")
        LogHelper.save(context, TAG, "Updating all covers.") // todo remove
    }


    /* Processes a given download ID */
    fun processDownload(context: Context, downloadId: Long) {
        // initialize main class variables, if necessary
        initialize(context)
        // get local Uri in content://downloads/all_downloads/ for download ID
        val downloadResult: Uri? = downloadManager.getUriForDownloadedFile(downloadId)
        if (downloadResult == null) {
            LogHelper.w(TAG, "Download not successful. Error code = ${getDownloadError(downloadId)}")
            removeFromActiveDownloads(context, arrayOf(downloadId), deleteDownload = true)
            return
        } else {
            val localFileUri: Uri = downloadResult
            // get remote URL for download ID
            val remoteFileLocation: String = getRemoteFileLocation(downloadManager, downloadId)
            // determine what to
            val fileType = FileHelper.getFileType(context, localFileUri)
            // Log completed download // todo remove
            LogHelper.v(TAG, "Download complete: ${FileHelper.getFileName(context, localFileUri)} | ${FileHelper.getReadableByteCount(FileHelper.getFileSize(context, localFileUri))} | $fileType") // todo remove
            LogHelper.save(context, TAG, "Download complete: ${FileHelper.getFileName(context, localFileUri)} | ${FileHelper.getReadableByteCount(FileHelper.getFileSize(context, localFileUri))} | $fileType") // todo remove
            if (fileType in Keys.MIME_TYPES_RSS) readPodcastFeed(context, localFileUri, remoteFileLocation)
            if (fileType in Keys.MIME_TYPES_ATOM) Toast.makeText(context, context.getString(R.string.toast_message_error_feed_not_supported), Toast.LENGTH_LONG).show()
            if (fileType in Keys.MIME_TYPES_AUDIO) setEpisodeMediaUri(context, localFileUri, remoteFileLocation)
            if (fileType in Keys.MIME_TYPES_IMAGE) setPodcastImage(context, localFileUri, remoteFileLocation)
            // remove ID from active downloads
            removeFromActiveDownloads(context, arrayOf(downloadId))
        }
    }


    /* Initializes main class variables of DownloadHelper, if necessary */
    private fun initialize(context: Context) {
        if (!this::modificationDate.isInitialized) {
            modificationDate = PreferencesHelper.loadCollectionModificationDate(context)
        }
        if (!this::collection.isInitialized || CollectionHelper.isNewerCollectionAvailable(context, modificationDate)) {
            collection = FileHelper.readCollection(context) // todo make async
            modificationDate = PreferencesHelper.loadCollectionModificationDate(context)
        }
        if (!this::downloadManager.isInitialized) {
            FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        if (!this::activeDownloads.isInitialized) {
            activeDownloads = getActiveDownloads(context)
        }
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun enqueueDownload(context: Context, uris: Array<Uri>, type: Int, podcastName: String = String(), ignoreWifiRestriction: Boolean = false) {
        // determine allowed network types
        val allowedNetworkTypes: Int = determineAllowedNetworkTypes(context, type, ignoreWifiRestriction)
        // enqueue downloads
        val newIds = LongArray(uris.size)
        for (i in uris.indices) {
            LogHelper.v(TAG, "DownloadManager enqueue: ${uris[i]}")
            LogHelper.save(context, TAG, "DownloadManager enqueue: ${uris[i]}") // todo remove
            // check if valid url and prevent double download
            val scheme: String = uris[i].scheme ?: String()
            if (scheme.startsWith("http") && isNotInDownloadQueue(uris[i].toString())) {
                val fileName: String = uris[i].pathSegments.last() ?: String()
                val request: DownloadManager.Request = DownloadManager.Request(uris[i])
                        .setAllowedNetworkTypes(allowedNetworkTypes)
                        .setTitle(fileName)
                        .setDestinationInExternalFilesDir(context, Keys.FOLDER_TEMP, fileName)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                newIds[i] = downloadManager.enqueue(request)
                activeDownloads.add(newIds[i])
            }
        }
        setActiveDownloads(context, activeDownloads)
    }


    /*  episode and podcast cover */
    private fun enqueuePodcastMediaFiles(context: Context, podcast: Podcast, isNew: Boolean, ignoreWifiRestriction: Boolean = false) {
        // new podcast: first download the cover
        if (isNew && podcast.remoteImageFileLocation.isNotEmpty()) {
            CollectionHelper.clearImagesFolder(context, podcast)
            val coverUris: Array<Uri> = Array(1) { podcast.remoteImageFileLocation.toUri() }
            enqueueDownload(context, coverUris, Keys.FILE_TYPE_IMAGE, podcast.name)
        }
        // download audio files only when connected to wifi
        if (ignoreWifiRestriction || NetworkHelper.isConnectedToWifi(context)) {
            // download only if pocast has episodes
            if (podcast.episodes.isNotEmpty()) {
                // delete oldest audio file
                if (podcast.episodes.size >= Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP) {
                    val oldestEpisode: Episode = podcast.episodes[Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP - 1]
                    if (oldestEpisode.audio.isNotBlank()) { collection = CollectionHelper.deleteEpisodeAudioFile(context, collection, oldestEpisode.getMediaId()) }
                }
                // start download of latest episode audio file
                val episodeUris: Array<Uri> = Array(1) { podcast.episodes[0].remoteAudioFileLocation.toUri() }
                enqueueDownload(context, episodeUris, Keys.FILE_TYPE_AUDIO, podcast.name)
            }
        }
    }


    /* Adds podcast to podcast collection*/
    private fun addPodcast(context: Context, podcast: Podcast) {
        when (CollectionHelper.checkPodcastState(collection, podcast)) {
            Keys.PODCAST_STATE_NEW_PODCAST -> {
                collection = CollectionHelper.addPodcast(collection, podcast)
                saveCollection(context, opmlExport = true)
                enqueuePodcastMediaFiles(context, podcast, isNew = true)
            }
            (Keys.PODCAST_STATE_HAS_NEW_EPISODES) -> {
                collection = CollectionHelper.updatePodcast(collection, podcast)
                saveCollection(context, opmlExport = false)
                enqueuePodcastMediaFiles(context, podcast, isNew = false)
            }
        }
    }


    /* Sets podcast cover */
    private fun setPodcastImage(context: Context, tempFileUri: Uri, remoteFileLocation: String) {
        collection.podcasts.forEach { podcast ->
            if (podcast.remoteImageFileLocation == remoteFileLocation) {
                podcast.smallCover = FileHelper.saveSmallCover(context, podcast.name, tempFileUri).toString()
                podcast.cover = FileHelper.saveCopyOfFile(context, podcast.name, tempFileUri, Keys.FILE_TYPE_IMAGE, Keys.PODCAST_COVER_FILE).toString()
                podcast.episodes.forEach { episode ->
                    episode.cover = podcast.cover
                    episode.smallCover = podcast.smallCover
                }
            }
        }
        // save collection
        saveCollection(context)
    }


    /* Sets Media Uri in Episode */
    private fun setEpisodeMediaUri(context: Context, tempFileUri: Uri, remoteAudioFileLocation: String) {
        var matchingEpisodeFound = false
        // compare remoteFileLocations
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.remoteAudioFileLocation == remoteAudioFileLocation) {
                    matchingEpisodeFound = true
                    episode.audio = FileHelper.saveCopyOfFile(context, podcast.name, tempFileUri, Keys.FILE_TYPE_AUDIO, FileHelper.getFileName(context, tempFileUri), async = true).toString()
                    episode.duration = AudioHelper.getDuration(context, tempFileUri)
                }
            }
        }
        // no matching episode found - try matching filename only (second run) - a hack that should prevent a ton of network requests for potential redirects (e.g. feedburner links)
        if (!matchingEpisodeFound) {
            val localFileName: String = FileHelper.getFileName(context, tempFileUri)
            collection.podcasts.forEach { podcast ->
                podcast.episodes.forEach { episode ->
                    // compare file names
                    val url: String = episode.remoteAudioFileLocation
                     if (localFileName == url.substring(url.lastIndexOf('/')+1, url.length)) {
                         episode.audio = FileHelper.saveCopyOfFile(context, podcast.name, tempFileUri, Keys.FILE_TYPE_AUDIO, FileHelper.getFileName(context, tempFileUri), async = true).toString()
                         episode.duration = AudioHelper.getDuration(context, tempFileUri)
                    }
                }
            }
        }
        // remove unused audio references from collection
        collection = CollectionHelper.deleteUnneededAudioFiles(context, collection)
        // update player state if necessary
        PreferencesHelper.updatePlayerState(context, collection)
        // save collection
        saveCollection(context)
    }


    /* Checks if a file is not yet in download queue */
    private fun isNotInDownloadQueue(remoteFileLocation: String): Boolean {
        val activeDownloadsCopy = activeDownloads.copy()
        activeDownloadsCopy.forEach { downloadId ->
            if (getRemoteFileLocation(downloadManager, downloadId) == remoteFileLocation) {
                LogHelper.w(TAG, "File is already in download queue: $remoteFileLocation")
                return false
            }
        }
        LogHelper.v(TAG, "File is not in download queue.")
        return true
    }


    /* Savely remove given download IDs from activeDownloads and delete download if requested */
    private fun removeFromActiveDownloads(context: Context, downloadIds: Array<Long>, deleteDownload: Boolean = false): Boolean {
        // remove download ids from activeDownloads
        val success: Boolean = activeDownloads.removeAll { downloadId -> downloadIds.contains(downloadId) }
        if (success) {
            setActiveDownloads(context, activeDownloads)
        }
        // optionally: delete download
        if (deleteDownload) {
            downloadIds.forEach { downloadId -> downloadManager.remove(downloadId) }
        }
        return success
    }


    /* Saves podcast collection to storage */
    private fun saveCollection(context: Context, opmlExport: Boolean = false) {
        // save collection (not async) - and store modification date
        modificationDate = CollectionHelper.saveCollection(context, collection, async = false)
        // export as OPML, if requested
        if (opmlExport) {CollectionHelper.exportCollection(context, collection)}
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeed(context: Context, localFileUri: Uri, remoteFileLocation: String) {
        val backgroundJob = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
        uiScope.launch() {
            LogHelper.v(TAG, "Reading podcast RSS file ($remoteFileLocation) - Thread: ${Thread.currentThread().name}")
            LogHelper.save(context, TAG, "Reading podcast RSS file ($remoteFileLocation) - Thread: ${Thread.currentThread().name}") // todo remove
            // async: readSuspended xml
            val deferred: Deferred<Podcast> = async { RssHelper().readSuspended(context, localFileUri, remoteFileLocation) }
            // wait for result and create podcast
            val podcast: Podcast = deferred.await()
            when (CollectionHelper.validatePodcast(podcast)) {
                Keys.PODCAST_VALIDATION_SUCESS -> {
                    addPodcast(context, podcast)
                }
                Keys.PODCAST_VALIDATION_MISSING_COVER -> {
                    addPodcast(context, podcast)
                    Toast.makeText(context, context.getString(R.string.toast_message_error_validation_missing_cover), Toast.LENGTH_LONG).show()
                }
                Keys.PODCAST_VALIDATION_NO_AUDIO_FILES -> {
                    Toast.makeText(context, context.getString(R.string.toast_message_error_validation_audio_references), Toast.LENGTH_LONG).show()
                }
            }
            CollectionHelper.trimPodcastEpisodeLists(context, collection)
            backgroundJob.cancel()
        }
    }


    /* Saves active downloads (IntArray) to shared preferences */
    private fun setActiveDownloads(context: Context, activeDownloads: ArrayList<Long>) {
        val builder = StringBuilder()
        for (i in activeDownloads.indices) {
            builder.append(activeDownloads[i]).append(",")
        }
        var activeDownloadsString: String = builder.toString()
        if (activeDownloadsString.isEmpty()) {
            activeDownloadsString = Keys.ACTIVE_DOWNLOADS_EMPTY
        }
        PreferencesHelper.saveActiveDownloads(context, activeDownloadsString)
    }


    /* Loads active downloads (IntArray) from shared preferences */
    private fun getActiveDownloads(context: Context): ArrayList<Long> {
        var inactiveDownloadsFound: Boolean = false
        val activeDownloadsList: ArrayList<Long> = arrayListOf<Long>()
        val activeDownloadsString: String = PreferencesHelper.loadActiveDownloads(context)
        val count = activeDownloadsString.split(",").size - 1
        val tokenizer = StringTokenizer(activeDownloadsString, ",")
        repeat(count) {
            val token = tokenizer.nextToken().toLong()
            when (isDownloadActive(token)) {
                true -> activeDownloadsList.add(token)
                false -> inactiveDownloadsFound = true
            }
        }
        if (inactiveDownloadsFound) setActiveDownloads(context, activeDownloadsList)
        return activeDownloadsList
    }


    /* Determines the remote file location (the original URL) */
    private fun getRemoteFileLocation(downloadManager: DownloadManager, downloadId: Long): String {
        var remoteFileLocation: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
        }
        return remoteFileLocation
    }


    /* Checks if a given download ID represents a finished download */
    private fun isDownloadFinished(downloadId: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus == DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Checks if a given download ID represents a finished download */
    private fun isDownloadActive(downloadId: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return downloadStatus == DownloadManager.STATUS_RUNNING
    }


    /* Retrieves reason of download error - returns http error codes plus error codes found here check: https://developer.android.com/reference/android/app/DownloadManager */
    private fun getDownloadError(downloadId: Long): Int {
        var reason: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            val downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (downloadStatus == DownloadManager.STATUS_FAILED) {
                reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            }
        }
        return reason
    }


    /* Determine allowed network type */
    private fun determineAllowedNetworkTypes(context: Context, type: Int, ignoreWifiRestriction: Boolean): Int {
        var allowedNetworkTypes: Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        // restrict download of audio files to WiFi if necessary
        if (type == Keys.FILE_TYPE_AUDIO) {
            val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_DOWNLOAD_OVER_MOBILE)
            if (!ignoreWifiRestriction && !downloadOverMobile) {
                allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
            }
        }
        return allowedNetworkTypes
    }

}