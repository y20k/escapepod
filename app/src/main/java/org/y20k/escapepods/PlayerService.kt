/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PodcastPlayerActivity is Escapepod's foreground service that plays podcast audio and handles playback controls
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.AudioAttributesCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.helpers.*
import java.util.*
import kotlin.coroutines.CoroutineContext


/*
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat(), CoroutineScope {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var backgroundJob: Job
    private var collection: Collection = Collection()
    private var collectionProvider: CollectionProvider = CollectionProvider()
    private var episode: Episode = Episode()
    private var isForegroundService = false
    private lateinit var packageValidator: PackageValidator
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var userAgent: String
    private lateinit var collectionChangedReceiver: BroadcastReceiver
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var becomingNoisyReceiver: BecomingNoisyReceiver

    /* Overrides coroutineContext variable */
    override val coroutineContext: CoroutineContext get() = backgroundJob + Dispatchers.Main


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()

        // initialize background job
        backgroundJob = Job()

        // set user agent
        userAgent = Util.getUserAgent(this, Keys.APPLICATION_NAME)

        // get the package validator // todo can be local?
        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)

        // create a new MediaSession
        mediaSession = createMediaSession()
        sessionToken = mediaSession.sessionToken

        // because ExoPlayer will manage the MediaSession, add the service as a callback for state changes
        mediaController = MediaControllerCompat(this, mediaSession).also {
            it.registerCallback(MediaControllerCallback())
        }

        // initialize notification helper and notification manager
        notificationHelper = NotificationHelper(this)
        notificationManager = NotificationManagerCompat.from(this)

        // initialize media controller
        mediaController = MediaControllerCompat(applicationContext, mediaSession.sessionToken)

        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))

        // initialize listener for headphone unplug
        becomingNoisyReceiver = BecomingNoisyReceiver(this, mediaSession.sessionToken)

        // load collection
        loadCollection(this)
    }


    /* Overrides onStartCommand */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return Service.START_STICKY
//        return super.onStartCommand(intent, flags, startId)
    }


    /* Overrides onTaskRemoved */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }


    /* Overrides onDestroy */
    override fun onDestroy() {
        stopPlayback()
        // release media session
        mediaSession.run {
            isActive = false
            release()
        }
        becomingNoisyReceiver.unregister()
        // cancel background job
        backgroundJob.cancel()
    }


    /* Overrides onGetRoot */
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
        // LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints")
        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin:
        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // request comes from an untrusted package
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_EMPTY_ROOT, null)
        } else {
            return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_ROOT, null)
        }
    }


    /* Overrides onLoadChildren */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (!collectionProvider.isInitialized()) {
            // use result.detach to allow calling result.sendResult from another thread:
            result.detach()
            collectionProvider.retrieveMedia(this, collection, object: CollectionProvider.CollectionProviderCallback {
                override fun onEpisodeListReady(success: Boolean) {
                    if (success) {
                        loadChildren(parentId, result)
                    }
                }
            })
        } else {
            // if music catalog is already loaded/cached, load them into result immediately
            loadChildren(parentId, result)
        }
    }


    /* Creates a new MediaSession */
    private fun createMediaSession(): MediaSessionCompat {
        val initialPlaybackState: Int = PreferencesHelper.loadPlayerPlayBackState(this)
        val position: Long = 0
        val sessionIntent = packageManager?.getLaunchIntentForPackage(packageName)
        val sessionActivityPendingIntent = PendingIntent.getActivity(this, 0, sessionIntent, 0)
        return MediaSessionCompat(this, TAG)
                .apply {
                    setSessionActivity(sessionActivityPendingIntent)
                    setCallback(mediaSessionCallback)
                    setPlaybackState(createPlaybackState(initialPlaybackState, position))
                    setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                    isActive = true
                }
    }


    /* Starts playback */
    private fun startPlayback() {
        if (episode.audio.isNotBlank()) {
            LogHelper.d(TAG, "Starting Playback")
            collection = CollectionHelper.savePlaybackState(this, collection, episode, PlaybackStateCompat.STATE_PLAYING)
            mediaSession.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PLAYING,0))
            mediaSession.setActive(true)
        }
    }


    /* Pauses playback - make notification swipeable */
    private fun pausePlayback() {
        if (episode.audio.isNotEmpty()) {
            LogHelper.d(TAG, "Pausing Playback")
            collection = CollectionHelper.savePlaybackState(this, collection, episode, PlaybackStateCompat.STATE_PAUSED)
            mediaSession.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PAUSED, 0))
            mediaSession.setActive(true)
        } else {
            stopPlayback()
        }
    }


    /* Stops playback - remove notification */
    private fun stopPlayback() {
        LogHelper.d(TAG, "Stopping Playback")
        collection = CollectionHelper.savePlaybackState(this, collection, episode, PlaybackStateCompat.STATE_STOPPED)
        mediaSession.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_STOPPED, 0))
        mediaSession.setActive(false)
    }


    /* Skips playback forward */
    private fun skipForwardPlayback() {
        LogHelper.d(TAG, "Skipping forward")
    }


    /* Skips playback back */
    private fun skipBackPlayback() {
        LogHelper.d(TAG, "Skipping back")
    }


//    /* Creates media session */
//    private fun createMediaSession(): MediaSessionCompat {
//        val session = MediaSessionCompat(this, TAG)
//        session.setCallback(mediaSessionCallback)
//        LogHelper.e(TAG, "after setCallback") // todo remove
//        session.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_STOPPED))
//        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
//        setSessionToken(session.getSessionToken())
//        return session
//    }


    /* Creates playback state - actions for playback state to be used in media session callback */
    private fun createPlaybackState(state: Int, position: Long): PlaybackStateCompat {
        val skipActions: Long = PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND
        when(state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, position, 1f)
                        .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or skipActions)
                        .build()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, position, 0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
            }
            else -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, position, 0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
            }
        }
    }


    /* Loads media items into result - assumes that collectionProvider is initialized */
    private fun loadChildren(parentId: String, result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            Keys.MEDIA_ID_ROOT -> {
                // mediaItems
                for (track in collectionProvider.getAllEpisodes()) {
                    val item = MediaBrowserCompat.MediaItem(track.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    mediaItems.add(item)
                }
            }
            Keys.MEDIA_ID_EMPTY_ROOT -> {
                // do nothing
            }
            else -> {
                // log error
                LogHelper.w(TAG, "Skipping unmatched parentId: $parentId")
            }
        }
        result.sendResult(mediaItems)
    }


    /* Creates the collectionChangedReceiver - handles Keys.ACTION_COLLECTION_CHANGED */
    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION)) {
                    val lastUpdate: Date = DateHelper.convertFromRfc2822(intent.getStringExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION))
                    // check if reload is necessary
                    if (lastUpdate.after(collection.lastUpdate)) {
                        loadCollection(context)
                    }
                }
            }
        }
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection(context: Context) {
        LogHelper.v(TAG, "Loading podcast collection from storage")
        launch {
            // load collection on background thread
            val deferred: Deferred<Collection> = async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
            // wait for result and update collection
            collection = deferred.await()
            LogHelper.e(TAG, collection.toString()) // todo remove
        }
    }


    /*
     * Wrap a SimpleExoPlayer with a decorator to handle audio focus
     */
    private val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributesCompat.Builder()
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .build()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioFocusExoPlayerDecorator(audioAttributes,
                audioManager,
                ExoPlayerFactory.newSimpleInstance(
                        DefaultRenderersFactory(this),
                        DefaultTrackSelector(),
                        DefaultLoadControl()))
    }
    /*
     * End of declaration
     */



    /*
     * Defines callbacks for active media session
     */
    private var mediaSessionCallback = object: MediaSessionCompat.Callback() {
        override fun onPlay() {
            // start playback
            LogHelper.e(TAG, "MediaSessionCallback onPlay") // todo remove
            startPlayback()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            LogHelper.e(TAG, "MediaSessionCallback onPlayFromMediaId") // todo remove
            episode = CollectionHelper.getEpisode(collection, mediaId ?: "")
            startPlayback()
        }

        override fun onPause() {
            // pause playback - make notification swipeable
            LogHelper.e(TAG, "MediaSessionCallback onPause") // todo remove
            pausePlayback()
        }

        override fun onStop() {
            // stop playback - remove notification
            LogHelper.e(TAG, "MediaSessionCallback onStop") // todo remove
            stopPlayback()
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            // handle requests to begin playback from a search query (eg. Assistant, Android Auto, etc.)
            LogHelper.i(TAG, "playFromSearch  query=$query extras=$extras")

            if (TextUtils.isEmpty(query)) {
                // user provided generic string e.g. 'Play music'
                // mStation = Station(mStationListProvider.getFirstStation())
            } else {
                // try to match station name and voice query
//                for (stationMetadata in mStationListProvider.getAllStations()) {
//                    val words = query!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//                    for (word in words) {
//                        if (stationMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE).toLowerCase().contains(word.toLowerCase())) {
//                            mStation = Station(stationMetadata)
//                        }
//                    }
//                }
            }
            // start playback
            startPlayback()
        }

        override fun onFastForward() {
            super.onFastForward()
            LogHelper.e(TAG, "MediaSessionCallback onFastForward") // todo remove
            skipForwardPlayback()
        }

        override fun onRewind() {
            super.onRewind()
            LogHelper.e(TAG, "MediaSessionCallback onRewind") // todo remove
            skipBackPlayback()
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
            // todo react to command given at PodcastPlayerActivity -> mediaController.sendCommand(...)
        }

    }
    /*
     * End of callback
     */


    /*
     * Inner class: Class to receive callbacks about state changes to the MediaSessionCompat - handles notification
     */
    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.e(TAG, "onMetadataChanged called") // todo remove
            mediaController.playbackState?.let { updateNotification(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            LogHelper.e(TAG, "onPlaybackStateChanged called") // todo remove
            state?.let { updateNotification(it) }
        }

        private fun updateNotification(state: PlaybackStateCompat) {
            LogHelper.e(TAG, "updateNotification called") // todo remove
            val updatedState = state.state
            if (mediaController.metadata == null) {
                return
            }
            // skip building a notification when state is "none".
            val notification = if (updatedState != PlaybackStateCompat.STATE_NONE) {
                notificationHelper.buildNotification(mediaSession.sessionToken, episode)
            } else {
                null
            }
            when (updatedState) {
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_PLAYING -> {
                    becomingNoisyReceiver.register()
                    if (!isForegroundService) {
                        startService(Intent(applicationContext, this@PlayerService.javaClass))
                        startForeground(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                        isForegroundService = true
                    } else if (notification != null) {
                        notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                    }
                }
                else -> {
                    becomingNoisyReceiver.unregister()
                    if (isForegroundService) {
                        stopForeground(false)
                        isForegroundService = false
                        // If playback has ended, also stop the service.
                        if (updatedState == PlaybackStateCompat.STATE_NONE) {
                            stopSelf()
                        }
                        if (notification != null) {
                            notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                        } else {
                            // remove notification
                            stopForeground(true)
                        }
                    }
                }
            }
        }
    }



    /*
     * Inner class: listening for when headphones are unplugged ("ACTION_AUDIO_BECOMING_NOISY")
     */
    private inner class BecomingNoisyReceiver(private val context: Context, sessionToken: MediaSessionCompat.Token): BroadcastReceiver() {

        private val noisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        private val controller = MediaControllerCompat(context, sessionToken)

        private var registered = false

        fun register() {
            if (!registered) {
                context.registerReceiver(this, noisyIntentFilter)
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                context.unregisterReceiver(this)
                registered = false
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                controller.transportControls.pause()
            }
        }
    }
    /*
     * End of inner class
     */

}