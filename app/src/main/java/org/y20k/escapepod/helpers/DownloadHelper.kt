/*
 * DownloadHelper.kt
 * Implements the DownloadHelper object
 * A DownloadHelper provides helper methods for downloading files
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
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
import kotlinx.coroutines.*
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import org.y20k.escapepod.extensions.copy
import org.y20k.escapepod.xml.RssHelper
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * DownloadHelper object
 */
object DownloadHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Main class variables */
    private lateinit var collectionDatabase: CollectionDatabase
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
        GlobalScope.launch {
            val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
            if (episode != null) {
                // mark as manually downloaded if necessary
                if (manuallyDownloaded) {
                    collectionDatabase.episodeDao().upsert(Episode(episode, manuallyDownloaded = true))
                }
                // enqueue episode
                val uris = Array(1) { episode.remoteAudioFileLocation.toUri() }
                enqueueDownload(context, uris, Keys.FILE_TYPE_AUDIO, ignoreWifiRestriction)
            }
        }
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
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_message_error_refreshing_cover), Toast.LENGTH_LONG).show()
        }
    }


    /* Updates podcast collection */
    fun updateCollection(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        GlobalScope.launch {
            // re-download all podcast xml episode lists
            PreferencesHelper.saveLastUpdateCollection(context)
            val podcasts: List<Podcast> = collectionDatabase.podcastDao().getAll()
            val uris: Array<Uri> = Array(podcasts.size) { it ->
                podcasts[it].remotePodcastFeedLocation.toUri()
            }
            // enqueue downloads to DownloadManager
            enqueueDownloadSuspended(context, uris, Keys.FILE_TYPE_RSS)
        }
    }


    /* Updates all podcast covers */
    fun updateCovers(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        // re-download all podcast covers
        GlobalScope.launch {
            PreferencesHelper.saveLastUpdateCollection(context)
            val podcasts: List<Podcast> = collectionDatabase.podcastDao().getAll()
            val uris: Array<Uri> = Array(podcasts.size) { it ->
                podcasts[it].remoteImageFileLocation.toUri()
            }
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
            LogHelper.i(TAG, "Updating all covers.")
        }
    }


    /* Processes a given download ID */
    fun processDownload(context: Context, downloadId: Long) {
        // initialize main class variables, if necessary
        initialize(context)
        // get local Uri in content://downloads/all_downloads/ for download ID
        val downloadResult: Uri? = downloadManager.getUriForDownloadedFile(downloadId)
        if (downloadResult == null) {
            val downloadErrorCode: Int = getDownloadError(downloadId)
            val downloadErrorFileName: String = getDownloadFileName(downloadManager, downloadId)
            Toast.makeText(context, "${context.getString(R.string.toast_message_error_download_error)}: $downloadErrorFileName ($downloadErrorCode)", Toast.LENGTH_LONG).show()
            LogHelper.w(TAG, "Download not successful: File name = $downloadErrorFileName Error code = $downloadErrorCode")
            removeFromActiveDownloads(context, arrayOf(downloadId), deleteDownload = true)
            return
        } else {
            val localFileUri: Uri = downloadResult
            // get unresolved original URL that has been stored in download request Description column
            val remoteFileLocation: String = getDownloadDescription(downloadManager, downloadId)
            // determine what to do
            val fileType = FileHelper.getContentType(context, localFileUri)
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
        if (!this::collectionDatabase.isInitialized) {
            collectionDatabase = CollectionDatabase.getInstance(context)
        }
        if (!this::downloadManager.isInitialized) {
            FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        if (!this::activeDownloads.isInitialized) {
            activeDownloads = getActiveDownloads(context)
        }
    }


    /* Enqueues an Array of file Uris in DownloadManager */
    private fun enqueueDownload(context: Context, uris: Array<Uri>, type: Int, ignoreWifiRestriction: Boolean = false) {
        val backgroundJob = Job()
        val ioScope = CoroutineScope(Dispatchers.IO + backgroundJob)
        ioScope.launch {
            // determine allowed network types
            val allowedNetworkTypes: Int = determineAllowedNetworkTypes(context, type, ignoreWifiRestriction)
            // enqueue downloads
            val newIds = LongArray(uris.size)
            for (i in uris.indices) {
                // async: resolve url redirects
                val deferred: Deferred<String> = async { NetworkHelper.resolveRedirectsSuspended(uris[i].toString()) }
                val resolvedUri: Uri = deferred.await().toUri()
                LogHelper.v(TAG, "DownloadManager enqueue: $resolvedUri")
                // check if valid url and prevent double download
                val scheme: String = resolvedUri.scheme ?: String()
                val pathSegments: List<String> = resolvedUri.pathSegments
                if (scheme.startsWith("http") && isNotInDownloadQueue(resolvedUri) && pathSegments.isNotEmpty()) {
                    val fileName: String = pathSegments.last()
                    val request: DownloadManager.Request = DownloadManager.Request(resolvedUri)
                            .setAllowedNetworkTypes(allowedNetworkTypes)
                            .setTitle(fileName)
                            .setDescription(uris[i].toString()) // store the unresolved URL
                            .setDestinationInExternalFilesDir(context, Keys.FOLDER_TEMP, fileName)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    newIds[i] = downloadManager.enqueue(request)
                    activeDownloads.add(newIds[i])
                }
            }
            setActiveDownloads(context, activeDownloads)
            backgroundJob.cancel()
        }
    }


    /* Suspend function: Wrapper for enqueueDownload */
    suspend fun enqueueDownloadSuspended(context: Context, uris: Array<Uri>, type: Int, podcastName: String = String(), ignoreWifiRestriction: Boolean = false) {
        return suspendCoroutine { cont ->
            cont.resume(enqueueDownload(context, uris, type, ignoreWifiRestriction))
        }
    }


    /* Enqueue episode and podcast cover */
    private fun enqueuePodcastMediaFiles(context: Context, podcast: Podcast, episodes: List<Episode>, isNewPodcast: Boolean, ignoreWifiRestriction: Boolean = false) {
        // new podcast: first download the cover
        LogHelper.e(TAG, "DING => new $isNewPodcast ++ empty ${podcast.remoteImageFileLocation.isNotEmpty()}") // todo remove
        if (isNewPodcast && podcast.remoteImageFileLocation.isNotEmpty()) {
            CollectionHelper.clearImagesFolder(context, podcast)
            val coverUris: Array<Uri> = Array(1) { podcast.remoteImageFileLocation.toUri() }
            enqueueDownload(context, coverUris, Keys.FILE_TYPE_IMAGE)
        }
        // download audio files only when connected to wifi - or if user chose otherwise
        if (ignoreWifiRestriction || PreferencesHelper.loadEpisodeDownloadOverMobile(context) || NetworkHelper.isConnectedToWifi(context)) {
            // download only if podcast has episodes
            if (episodes.isNotEmpty()) {
                // delete oldest audio file
                if (episodes.size >= Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP) {
                    val oldestEpisode: Episode = episodes[Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP - 1]
                    if (oldestEpisode.audio.isNotBlank()) {
                        // delete audio file and update database
                        collectionDatabase.episodeDao().update(CollectionHelper.deleteEpisodeAudioFile(episode = oldestEpisode, manuallyDeleted = false))
                    }
                }
                // start download of latest episode audio file
                val episodeUris: Array<Uri> = Array(1) { episodes[0].remoteAudioFileLocation.toUri() }
                enqueueDownload(context, episodeUris, Keys.FILE_TYPE_AUDIO)
            }
        }
    }


    /* Adds podcast and episodes to podcast collection - use within co-routine */
    private fun addPodcast(context: Context, rssPodcast: RssHelper.RssPodcast) {
        // extract database objects from rss podcast
        val podcastWithAllEpisodes: PodcastWithAllEpisodesWrapper = PodcastWithAllEpisodesWrapper(rssPodcast)
        val podcast: Podcast
        val episodes: List<Episode>
        val podcastDescription: PodcastDescription = PodcastDescription(rssPodcast)
        val episodeDescriptions: List<EpisodeDescription> = rssPodcast.episodes.map { EpisodeDescription(it) }
        // store old podcast
        val oldPodcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(podcastWithAllEpisodes.data.remotePodcastFeedLocation)
        val isNewPodcast: Boolean
        if (oldPodcast != null) {
            // CASE: existing podcast
            isNewPodcast = false
            podcast = Podcast(podcastWithAllEpisodes.data, cover = oldPodcast.cover, smallCover = oldPodcast.smallCover)
            val oldEpisodes: List<Episode> = collectionDatabase.episodeDao().findByEpisodeRemotePodcastFeedLocation(podcastWithAllEpisodes.data.remotePodcastFeedLocation)
            episodes = CollectionHelper.updateEpisodeList(podcast = podcast, oldEpisodes = oldEpisodes, newEpisodes = podcastWithAllEpisodes.episodes).sortedByDescending { it.publicationDate }

        } else {
            // CASE: new podcast
            isNewPodcast = true
            episodes = podcastWithAllEpisodes.episodes.sortedByDescending { it.publicationDate }
            podcast = podcastWithAllEpisodes.data
        }
        // update/insert podcast
        collectionDatabase.podcastDao().upsert(podcast)
        collectionDatabase.podcastDescriptionDao().upsert(podcastDescription)
        // update/insert episodes
        collectionDatabase.episodeDao().upsertAll(episodes)
        collectionDatabase.episodeDescriptionDao().upsertAll(episodeDescriptions)
        // enqueue audio files and cover
        val hasDownloadableEpisodes: Boolean = CollectionHelper.hasDownloadableEpisodes(episodes)
        if (isNewPodcast || hasDownloadableEpisodes) {
            enqueuePodcastMediaFiles(context, podcast, episodes, isNewPodcast)
        }
    }


    /* Sets podcast cover */
    private fun setPodcastImage(context: Context, tempFileUri: Uri, remoteFileLocation: String) {
        GlobalScope.launch {
            // save cover
            val podcastData: Podcast? = collectionDatabase.podcastDao().findByRemoteImageFileLocation(remoteFileLocation)
            if (podcastData != null) {
                val smallCover: String = FileHelper.saveCover(context, podcastData.name, tempFileUri.toString(), Keys.SIZE_COVER_PODCAST_CARD, Keys.PODCAST_SMALL_COVER_FILE).toString()
                val cover: String = FileHelper.saveCover(context, podcastData.name, tempFileUri.toString(), Keys.SIZE_COVER_MAXIMUM, Keys.PODCAST_COVER_FILE).toString()
                // update podcast cover
                collectionDatabase.podcastDao().updateCover(remoteImageFileLocation = remoteFileLocation, cover = cover, smallCover = smallCover)
                // update covers for all episodes
                collectionDatabase.episodeDao().updateCover(episodeRemotePodcastFeedLocation = podcastData.remotePodcastFeedLocation, cover = cover, smallCover = smallCover)
            }
        }
    }


    /* Sets Media Uri in episode */
    private fun setEpisodeMediaUri(context: Context, tempFileUri: Uri, remoteAudioFileLocation: String) {
        GlobalScope.launch {
            // save file and update audio reference and duration
            val episode: Episode? = collectionDatabase.episodeDao().findByRemoteAudioFileLocation(remoteAudioFileLocation)
            if (episode != null) {
                val audio: String = FileHelper.saveCopyOfFile(context, episode.podcastName, tempFileUri, Keys.FILE_TYPE_AUDIO, FileHelper.getFileName(context, tempFileUri), async = true).toString()
                val duration = AudioHelper.getDuration(context, tempFileUri)
                val updatedEpisodesCount: Int = collectionDatabase.episodeDao().updateAudioRemoteAudioFileLocation(remoteAudioFileLocation = remoteAudioFileLocation, audio = audio, duration = duration)

                // no matching episode updated - try matching filename only (second run) - a hack that should prevent a ton of network requests for potential redirects (e.g. feedburner links)
                if (updatedEpisodesCount <= 0) {
                    val localFileName: String = FileHelper.getFileName(context, tempFileUri)
                    collectionDatabase.episodeDao().updateAudioByFileName(localFileName = localFileName, audio = audio, duration = duration)
                }

                // remove unused audio references from collection
                val podcast: PodcastWithAllEpisodesWrapper? = collectionDatabase.podcastDao().getWithRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
                if (podcast != null) {
                    // delete un-needed audio files
                    val updatedEpisodes: List<Episode> = CollectionHelper.deleteUnneededAudioFiles(context, podcast)
                    // update episodes in database
                    collectionDatabase.episodeDao().upsertAll(updatedEpisodes)
                }
            }
        }
    }


    /* Checks if a file is not yet in download queue */
    private fun isNotInDownloadQueue(remoteFileLocationUri: Uri): Boolean {
        val activeDownloadsCopy = activeDownloads.copy()
        activeDownloadsCopy.forEach { downloadId ->
            if (getRemoteFileLocation(downloadManager, downloadId) == remoteFileLocationUri.toString()) {
                LogHelper.w(TAG, "File is already in download queue: $remoteFileLocationUri")
                return false
            }
        }
        LogHelper.v(TAG, "File is not in download queue.")
        return true
    }


    /* Safely remove given download IDs from activeDownloads and delete download if requested */
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


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeed(context: Context, localFileUri: Uri, remoteFileLocation: String) {
        GlobalScope.launch {
            LogHelper.v(TAG, "Reading podcast RSS file ($remoteFileLocation) - Thread: ${Thread.currentThread().name}")
            // async: readSuspended xml
            val deferred: Deferred<RssHelper.RssPodcast> = async { RssHelper().readSuspended(context, localFileUri, remoteFileLocation) }
            // wait for result and create podcast
            val rssPodcast: RssHelper.RssPodcast = deferred.await()
            // validate podcast
            when (CollectionHelper.validateRssPodcast(rssPodcast.remoteImageFileLocation.isEmpty(), rssPodcast.episodes.isEmpty())) {
                Keys.PODCAST_VALIDATION_SUCESS -> {
                    addPodcast(context, rssPodcast)
                }
                Keys.PODCAST_VALIDATION_MISSING_COVER -> {
                    addPodcast(context, rssPodcast)
                    Toast.makeText(context, context.getString(R.string.toast_message_error_validation_missing_cover), Toast.LENGTH_LONG).show()
                }
                Keys.PODCAST_VALIDATION_NO_VALID_EPISODES -> {
                    Toast.makeText(context, context.getString(R.string.toast_message_error_validation_no_valid_episodes), Toast.LENGTH_LONG).show()
                }
            }
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


    /* Determines the remote file location (URL given to DownloadManager.request) */
    private fun getRemoteFileLocation(downloadManager: DownloadManager, downloadId: Long): String {
        var remoteFileLocation: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
        }
        return remoteFileLocation
    }


    /* Determines the file name for given download id */
    private fun getDownloadFileName(downloadManager: DownloadManager, downloadId: Long): String {
        var downloadFileName: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadFileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
        }
        return downloadFileName
    }


    /* Determines the description for given download id */
    private fun getDownloadDescription(downloadManager: DownloadManager, downloadId: Long): String {
        var downloadDescription: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadDescription = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION))
        }
        return downloadDescription
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
            val downloadOverMobileAllowed: Boolean = PreferencesHelper.loadEpisodeDownloadOverMobile(context)
            if (!ignoreWifiRestriction && !downloadOverMobileAllowed) {
                allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
            }
        }
        return allowedNetworkTypes
    }

}