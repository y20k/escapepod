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
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import org.y20k.escapepods.collection.CollectionProvider
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.helpers.*
import org.y20k.escapepods.ui.PlayerState
import java.util.*
import kotlin.coroutines.CoroutineContext


/*
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat(), Player.EventListener, CoroutineScope {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()
    private var collectionProvider: CollectionProvider = CollectionProvider()
    private var episode: Episode = Episode()
    private var isForegroundService: Boolean = false
    private lateinit var player: SimpleExoPlayer
    private lateinit var playerState: PlayerState
    private lateinit var backgroundJob: Job
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

        // fetch the player state
        playerState = PreferencesHelper.loadPlayerState(this)

        // create player
        player = createPlayer()

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

        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))

        // initialize listener for unplugging of headphones
        becomingNoisyReceiver = BecomingNoisyReceiver(this, mediaSession.sessionToken)

        // load collection
        loadCollection(this)
    }


    /* Overrides onStartCommand */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return Service.START_STICKY
    }


    /* Overrides onTaskRemoved */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }


    /* Overrides onDestroy */
    override fun onDestroy() {
        // stop playback if necessary
        pausePlayback()

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
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
        // LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints")
        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin:
        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // request comes from an untrusted package
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return BrowserRoot(Keys.MEDIA_ID_EMPTY_ROOT, null)
        } else {
            return BrowserRoot(Keys.MEDIA_ID_ROOT, null)
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


    /* Overrides onPlayerStateChanged (Player.EventListener) */
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        // handle up-next queue
        when (playbackState) {
            Player.STATE_ENDED -> {
                if (playerState.upNextEpisodeMediaId.isEmpty() || playerState.upNextEpisodeMediaId == episode.getMediaId()) {
                    // clear up-next id in shared preferences
                    playerState.upNextEpisodeMediaId = String()
                    // stop playback
                    pausePlayback()
                } else {
                    // get up next episode
                    episode = CollectionHelper.getEpisode(collection, playerState.upNextEpisodeMediaId)
                    // clear up next media id
                    playerState.upNextEpisodeMediaId = String()
                    // start playback
                    startPlayback()
                }
            }
        }
        if (playWhenReady && playbackState == Player.STATE_READY) {
            LogHelper.e(TAG, "onPlayerStateChanged: Active playback.") // todo remove
            // Active playback.
            // update media session and save state
            handlePlaybackChange(PlaybackStateCompat.STATE_PLAYING)
        } else if (playWhenReady) {
            LogHelper.e(TAG, "onPlayerStateChanged: Not playing because playback ended, the player is buffering, stopped or failed. Check playbackState and player.getPlaybackError for details.") // todo remove
            // Not playing because playback ended, the player is buffering, stopped or
            // failed. Check playbackState and player.getPlaybackError for details.
        } else if (!playWhenReady && playbackState == Player.STATE_READY) {
            LogHelper.e(TAG, "onPlayerStateChanged: Paused by app.") // todo remove
            // Paused by app.
            // update media session and save state
            handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
        } else if (!playWhenReady && playbackState == Player.STATE_IDLE) {
            LogHelper.e(TAG, "onPlayerStateChanged:  STATE_IDLE.") // todo remove
            // update media session and save state
            handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED)

            // todo check if eposide.isfinshed - remove notification

        }
    }


    /* Creates a new MediaSession */
    private fun createMediaSession(): MediaSessionCompat {
        val initialPlaybackState: Int = PreferencesHelper.loadPlayerPlayBackState(this)
        val sessionActivityPendingIntent =
                packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                    PendingIntent.getActivity(this, 0, sessionIntent, 0)
                }
        return MediaSessionCompat(this, TAG)
                .apply {
                    setSessionActivity(sessionActivityPendingIntent)
                    setCallback(mediaSessionCallback)
                    setPlaybackState(createPlaybackState(initialPlaybackState, 0))
                }
    }


    /* Creates a simple exo player */
    private fun createPlayer(): SimpleExoPlayer {
        val player = ExoPlayerFactory.newSimpleInstance(this).apply { addListener(this@PlayerService) }
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
        player.setAudioAttributes(audioAttributes, true)
        return player
    }


    /* Prepares player with media source created from current episode */
    private fun preparePlayer() {
        // todo only prepare if not already prepared
        // create MediaSource
        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this, userAgent)).createMediaSource(Uri.parse(episode.audio))
        // prepare player with source
        player.prepare(mediaSource);
        // set player position
        player.seekTo(episode.playbackPosition)
    }


    /* Starts playback */
    private fun startPlayback() {
        if (episode.audio.isNotBlank()) {
            LogHelper.d(TAG, "Starting Playback. Position: ${episode.playbackPosition}. Duration: ${episode.duration}")
            // reset playback position if necessary
            if (episode.isFinished()) {
                episode.playbackPosition = 0L
            }
//            // update media session and save state
//            handlePlaybackChange(PlaybackStateCompat.STATE_PLAYING)
            // prepare player and start playback
            preparePlayer();
            player.playWhenReady = true
        }
    }


    /* Pauses playback - make notification swipeable */
    private fun pausePlayback() {
        if (episode.audio.isNotEmpty()) {
            LogHelper.d(TAG, "Pausing Playback")
            // update playback position
            episode.playbackPosition = player.contentPosition
            // stop playback
            player.playWhenReady = false
        }
    }


    /* Skips playback forward */
    private fun skipForwardPlayback() {
        LogHelper.d(TAG, "Skipping forward")
        var position: Long = player.currentPosition + Keys.SKIP_FORWARD_TIME_SPAN
        if (position > episode.duration) {
            position =  episode.duration
        }
        player.seekTo(position)
        sendPlaybackPositionBroadcast(position)
    }


    /* Skips playback back */
    private fun skipBackPlayback() {
        LogHelper.d(TAG, "Skipping back")
        var position: Long = player.currentPosition - Keys.SKIP_BACK_TIME_SPAN
        if (position < 0L) {
            position = 0L
        }
        player.seekTo(position)
        sendPlaybackPositionBroadcast(position)
    }


    /* Updates media session and save state */
    private fun handlePlaybackChange(playbackState: Int) {
        // save collection state and player state
        collection = CollectionHelper.savePlaybackState(this, collection, episode, playbackState)
        updatePlayerState(episode, playbackState)
        // update media session
        mediaSession.setPlaybackState(createPlaybackState(playbackState, episode.playbackPosition))
        mediaSession.isActive = playbackState != PlaybackStateCompat.STATE_STOPPED
    }


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
                    val item = MediaBrowserCompat.MediaItem(track.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
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
                    val lastUpdate: Date = DateTimeHelper.convertFromRfc2822(intent.getStringExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION))
                    // check if reload is necessary
                    if (lastUpdate.after(collection.lastUpdate)) {
                        LogHelper.w(TAG, "PlayerService - reload collection after broadcast received.") // todo remove
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
        }
    }


    /* Updates and saves the state of the player ui */
    private fun updatePlayerState (episode: Episode, playbackState: Int) {
        playerState.episodeMediaId = episode.getMediaId()
        playerState.playbackPosition = episode.playbackPosition
        playerState.playbackState = playbackState
        PreferencesHelper.savePlayerState(this, playerState)
    }


    /* Sends a broadcast containing the current playback position as parcel */
    private fun sendPlaybackPositionBroadcast(position: Long) {
        val playbackPositionIntent = Intent()
        playbackPositionIntent.action = Keys.ACTION_PLAYBACK_POSITION_CHANGED
        playbackPositionIntent.putExtra(Keys.EXTRA_CURRENT_PLAYBACK_POSITION, position)
        LocalBroadcastManager.getInstance(this).sendBroadcast(playbackPositionIntent)
    }


    /*
     * Callback: Defines callbacks for active media session
     */
    private var mediaSessionCallback = object: MediaSessionCompat.Callback() {
        override fun onPlay() {
            // start playback
            startPlayback()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            episode = CollectionHelper.getEpisode(collection, mediaId ?: "")
            startPlayback()
        }

        override fun onPause() {
            pausePlayback()
        }

        override fun onStop() {
            pausePlayback()
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
            skipForwardPlayback()
        }

        override fun onRewind() {
            super.onRewind()
            skipBackPlayback()
        }

        override fun onSeekTo(posistion: Long) {
            episode.playbackPosition = posistion
            player.seekTo(posistion)
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            when (command) {
                Keys.CMD_RELOAD_PLAYER_STATE -> {
                    playerState = PreferencesHelper.loadPlayerState(this@PlayerService)
                }
                Keys.CMD_REQUEST_PLAYBACK_POSITION -> {
                    if (cb != null) {
                        val bundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_PROGRESS to player.currentPosition)
                        cb.send(Keys.RESULT_CODE_PLAYBACK_PROGRESS, bundle)
                    }
                }
            }
        }

    }
    /*
     * End of callback
     */


    /*
     * Inner class: Class to receive callbacks about state changes to the MediaSessionCompat - handles notification
     * Source: https://github.com/googlesamples/android-UniversalMusicPlayer/blob/master/common/src/main/java/com/example/android/uamp/media/MusicService.kt
     */
    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.d(TAG, "onMetadataChanged called") // todo remove
            mediaController.playbackState?.let { updateNotification(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            LogHelper.d(TAG, "onPlaybackStateChanged called") // todo remove
            state?.let { updateNotification(it) }
        }

        private fun updateNotification(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "updateNotification called") // todo remove
            val updatedState = state.state

            // skip building a notification when state is "none" and metadata is null
            // val notification = if (mediaController.metadata != null && updatedState != PlaybackStateCompat.STATE_NONE) {
            val notification = if (updatedState != PlaybackStateCompat.STATE_NONE) {
                notificationHelper.buildNotification(mediaSession.sessionToken, episode)
            } else {
                null
            }

            when (updatedState) {
                // CASE: Playback has started
                PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_PLAYING -> {
                    // start listening for unplugging of headphone
                    becomingNoisyReceiver.register()

                    /**
                     * This may look strange, but the documentation for [Service.startForeground]
                     * notes that "calling this method does *not* put the service in the started
                     * state itself, even though the name sounds like it."
                     */
                    if (notification != null) {
                        notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                        if (!isForegroundService) {
                            ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, this@PlayerService.javaClass))
                            startForeground(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                            isForegroundService = true
                        }
                    }
                }
                // CASE: Playback has stopped
                else -> {
                    // stop listening for unplugging of headphone
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
                            // removeNowPlayingNotification
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