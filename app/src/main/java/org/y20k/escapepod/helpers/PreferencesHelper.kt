/*
 * PreferencesHelper.kt
 * Implements the PreferencesHelper object
 * A PreferencesHelper provides helper methods for the saving and loading values from shared preferences
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.ui.PlayerState
import java.util.*


/*
 * PreferencesHelper object
 */
object PreferencesHelper {

    /* The sharedPreferences object to be initialized */
    private lateinit var sharedPreferences: SharedPreferences

    /* Initialize a single sharedPreferences object when the app is launched */
    fun Context.initPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PreferencesHelper::class.java)


    /* Loads mediaId of current episode from shared preferences */
    fun loadCurrentMediaId(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of current episode to shared preferences */
    fun saveCurrentMediaId(mediaId: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, mediaId)
        }
    }


    /* Loads mediaId of next episode in Up Next queue from shared preferences */
    fun loadUpNextMediaId(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of next episode in Up Next queue  to shared preferences */
    fun saveUpNextMediaId(mediaId: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, mediaId)
        }
    }


    /* Load feed location of the podcast in the podcast list which is currently expanded */
    fun loadPodcastListExpandedFeedLocation(): String {
        return sharedPreferences.getString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, String()) ?: String()
    }


    /* Save feed location of the podcast in the podcast list which is currently expanded */
    fun savePodcastListExpandedFeedLocation(podcastFeedLocation: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, podcastFeedLocation)
        }
    }


    /* Loads keepDebugLog true or false */
    fun loadKeepDebugLog(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_KEEP_DEBUG_LOG, false)
    }


    /* Saves keepDebugLog true or false */
    fun saveKeepDebugLog(keepDebugLog: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_KEEP_DEBUG_LOG, keepDebugLog)
        }
    }


    /* Loads state of playback for PlayerService from shared preferences */
    fun loadPlayerPlaybackState(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
    }


    /* Saves state of playback for player to shared preferences */
    fun saveIsPlaying(isPlaying: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, isPlaying)
        }
    }


    /* Loads state of playback for player / PlayerService from shared preferences */
    fun loadPlayerPlaybackSpeed(): Float {
        return sharedPreferences.getFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
    }


    /* Saves state of playback for player / PlayerService to shared preferences */
    fun savePlayerPlaybackSpeed(playbackSpeed: Float) {
        sharedPreferences.edit {
            putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, playbackSpeed)
        }
    }


    /* Loads state sleep timer from shared preferences */
    fun loadSleepTimerRunning(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
    }


    /* Saves state of sleep timer to shared preferences */
    fun saveSleepTimerRunning(isRunning: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, isRunning)
        }
    }

    /* Loads last update from shared preferences */
    fun loadLastUpdateCollection(): Date {
        val lastSaveString: String = sharedPreferences.getString(Keys.PREF_LAST_UPDATE_COLLECTION, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(lastSaveString)
    }


    /* Saves last update to shared preferences */
    fun saveLastUpdateCollection(lastUpdate: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateTimeHelper.convertToRfc2822(lastUpdate))
        }
    }


    /* Loads size of collection from shared preferences */
    fun loadCollectionSize(): Int {
        return sharedPreferences.getInt(Keys.PREF_COLLECTION_SIZE, -1)
    }


    /* Saves site of collection to shared preferences */
    fun saveCollectionSize(size: Int) {
        sharedPreferences.edit {
            putInt(Keys.PREF_COLLECTION_SIZE, size)
        }
    }


    /* Loads currently selected podcast search index */
    fun loadCurrentPodcastSearchIndex(): String {
        return sharedPreferences.getString(Keys.PREF_PODCAST_SEARCH_PROVIDER, Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX) ?: Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX
    }


    /* Saves currently selected podcast search index */
    fun saveCurrentPodcastSearchIndex(currentSearchIndex: String) {
        sharedPreferences.edit {
            putString(Keys.PREF_PODCAST_SEARCH_PROVIDER, currentSearchIndex)
        }
    }


    /* Loads date of last save operation from shared preferences */
    fun loadCollectionModificationDate(): Date {
        val modificationDateString: String = sharedPreferences.getString(Keys.PREF_COLLECTION_MODIFICATION_DATE, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(modificationDateString)
    }


    /* Saves date of last save operation to shared preferences */
    fun saveCollectionModificationDate(lastSave: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_COLLECTION_MODIFICATION_DATE, DateTimeHelper.convertToRfc2822(lastSave))
        }
    }


    /* Returns if background refresh is allowed */
    fun loadBackgroundRefresh(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_BACKGROUND_REFRESH, Keys.DEFAULT_BACKGROUND_REFRESH_MODE)
    }


    /* Returns if download over mobile network is allowed */
    fun loadEpisodeDownloadOverMobile(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EPISODE_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE)
    }


    /* Loads active downloads from shared preferences */
    fun loadActiveDownloads(): String {
        val activeDownloadsString: String = sharedPreferences.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY) ?: Keys.ACTIVE_DOWNLOADS_EMPTY
        LogHelper.v(TAG, "IDs of active downloads: $activeDownloadsString")
        return activeDownloadsString
    }


    /* Saves active downloads to shared preferences */
    fun saveActiveDownloads(activeDownloadsString: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        }
    }


    /* Loads state of player user interface from shared preferences */
    fun loadPlayerState(): PlayerState {
        return PlayerState().apply {
            currentEpisodeMediaId = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
            isPlaying = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            streaming = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_STREAMING, false)
            playbackSpeed = sharedPreferences.getFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
            sleepTimerRunning = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
            upNextEpisodeMediaId = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
        }
    }


    /* Saves state of player user interface to shared preferences */
    fun savePlayerState(playerState: PlayerState) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, playerState.currentEpisodeMediaId)
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, playerState.isPlaying)
            putBoolean(Keys.PREF_PLAYER_STATE_STREAMING, playerState.streaming)
            putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, playerState.playbackSpeed)
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, playerState.sleepTimerRunning)
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, playerState.upNextEpisodeMediaId)
        }
    }


    /* Resets state of player user interface */
    fun resetPlayerState(keepUpNextMediaId: Boolean = true) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String())
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            putBoolean(Keys.PREF_PLAYER_STATE_STREAMING, false)
            putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
            // reset Up Next only if requested
            if (!keepUpNextMediaId) putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String())
        }
    }


    /* Start watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }


    /* Stop watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }


    /* Checks if housekeeping work needs to be done - used usually in DownloadWorker "REQUEST_UPDATE_COLLECTION" */
    fun isHouseKeepingNecessary(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, true)
    }


    /* Saves state of housekeeping */
    fun saveHouseKeepingNecessaryState(state: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, state)
        }
    }


    /* Load currently selected app theme */
    fun loadThemeSelection(): String {
        return sharedPreferences.getString(Keys.PREF_THEME_SELECTION, Keys.STATE_THEME_FOLLOW_SYSTEM) ?: Keys.STATE_THEME_FOLLOW_SYSTEM
    }


    /* Returns a readable String for currently selected App Theme */
    fun getCurrentTheme(context: Context): String {
        return when (loadThemeSelection()) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }

    /* Returns a readable String for currently selected podcast search provider */
    fun getCurrentPodcastSearchProvider(context: Context): String {
        return when (loadCurrentPodcastSearchIndex()) {
            Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX -> context.getString(R.string.pref_search_provider_selection_podcastindex)
            else -> context.getString(R.string.pref_search_provider_selection_gpodder)
        }
    }

    /* Return the set number of audio files to keep */
    fun numberOfAudioFilesToKeep(): Int {
        return sharedPreferences.getInt(
            Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP
        )
    }
}
