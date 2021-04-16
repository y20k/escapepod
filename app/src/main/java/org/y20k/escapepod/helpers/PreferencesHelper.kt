/*
 * PreferencesHelper.kt
 * Implements the PreferencesHelper object
 * A PreferencesHelper provides helper methods for the saving and loading values from shared preferences
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
import android.content.SharedPreferences
import android.support.v4.media.session.PlaybackStateCompat
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

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PreferencesHelper::class.java)


    /* Loads mediaId of current episode from shared preferences */
    fun loadCurrentMediaId(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of current episode to shared preferences */
    fun saveCurrentMediaId(context: Context, mediaId: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, mediaId)
        }
    }


    /* Loads mediaId of next episode in up next queue from shared preferences */
    fun loadUpNextMediaId(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of next episode in up next queue  to shared preferences */
    fun saveUpNextMediaId(context: Context, mediaId: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, mediaId)
        }
    }


    /* Load feed location of the podcast in the podcast list which is currently expanded */
    fun loadPodcastListExpandedFeedLocation(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, String()) ?: String()
    }


    /* Save feed location of the podcast in the podcast list which is currently expanded */
    fun savePodcastListExpandedFeedLocation(context: Context, podcastFeedLocation: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, podcastFeedLocation)
        }
    }


    /* Loads keepDebugLog true or false */
    fun loadKeepDebugLog(context: Context): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(Keys.PREF_KEEP_DEBUG_LOG, false)
    }


    /* Saves keepDebugLog true or false */
    fun saveKeepDebugLog(context: Context, keepDebugLog: Boolean = false) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putBoolean(Keys.PREF_KEEP_DEBUG_LOG, keepDebugLog)
        }
    }


    /* Loads state of playback for player / PlayerService from shared preferences */
    fun loadPlayerPlaybackState(context: Context): Int {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
    }


    /* Saves state of playback for player / PlayerService to shared preferences */
    fun savePlayerPlaybackState(context: Context, playbackState: Int) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, playbackState)
        }
    }


    /* Loads state of playback for player / PlayerService from shared preferences */
    fun loadPlayerPlaybackSpeed(context: Context): Float {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
    }


    /* Saves state of playback for player / PlayerService to shared preferences */
    fun savePlayerPlaybackSpeed(context: Context, playbackSpeed: Float) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, playbackSpeed)
        }
    }


    /* Loads last update from shared preferences */
    fun loadLastUpdateCollection(context: Context): Date {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val lastSaveString: String = settings.getString(Keys.PREF_LAST_UPDATE_COLLECTION, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(lastSaveString)
    }


    /* Saves last update to shared preferences */
    fun saveLastUpdateCollection(context: Context, lastUpdate: Date = Calendar.getInstance().time) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateTimeHelper.convertToRfc2822(lastUpdate))
        }
    }


    /* Loads size of collection from shared preferences */
    fun loadCollectionSize(context: Context): Int {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getInt(Keys.PREF_COLLECTION_SIZE, -1)
    }


    /* Saves site of collection to shared preferences */
    fun saveCollectionSize(context: Context, size: Int) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putInt(Keys.PREF_COLLECTION_SIZE, size)
        }
    }


    /* Loads currently selected podcast search index */
    fun loadCurrentPodcastSearchIndex(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_PODCAST_SEARCH_PROVIDER, Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX) ?: Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX
    }


    /* Saves currently selected podcast search index */
    fun saveCurrentPodcastSearchIndex(context: Context, currentSearchIndex: String) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_PODCAST_SEARCH_PROVIDER, currentSearchIndex)
        }
    }


    /* Loads date of last save operation from shared preferences */
    fun loadCollectionModificationDate(context: Context): Date {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val modificationDateString: String = settings.getString(Keys.PREF_COLLECTION_MODIFICATION_DATE, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(modificationDateString)
    }


    /* Saves date of last save operation to shared preferences */
    fun saveCollectionModificationDate(context: Context, lastSave: Date = Calendar.getInstance().time) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_COLLECTION_MODIFICATION_DATE, DateTimeHelper.convertToRfc2822(lastSave))
        }
    }


    /* Returns if background refresh is allowed */
    fun loadBackgroundRefresh(context: Context): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(Keys.PREF_BACKGROUND_REFRESH, Keys.DEFAULT_BACKGROUND_REFRESH_MODE)
    }


    /* Returns if download over mobile network is allowed */
    fun loadEpisodeDownloadOverMobile(context: Context): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(Keys.PREF_EPISODE_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE)
    }


    /* Loads active downloads from shared preferences */
    fun loadActiveDownloads(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val activeDownloadsString: String = settings.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY) ?: Keys.ACTIVE_DOWNLOADS_EMPTY
        LogHelper.v(TAG, "IDs of active downloads: $activeDownloadsString")
        return activeDownloadsString
    }


    /* Saves active downloads to shared preferences */
    fun saveActiveDownloads(context: Context, activeDownloadsString: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        }
    }


    /* Loads state of player user interface from shared preferences */
    fun loadPlayerState(context: Context): PlayerState {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val playerState: PlayerState = PlayerState()
        playerState.episodeMediaId = settings.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
        playerState.playbackState = settings.getInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
        playerState.playbackSpeed = settings.getFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
        playerState.upNextEpisodeMediaId = settings.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
        return playerState
    }


    /* Saves state of player user interface to shared preferences */
    fun savePlayerState(context: Context, playerState: PlayerState) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, playerState.episodeMediaId)
            putInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, playerState.playbackState)
            putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, playerState.playbackSpeed)
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, playerState.upNextEpisodeMediaId)
        }
    }


    /* Resets state of player user interface */
    fun resetPlayerState(context: Context, keepUpNextMediaId: Boolean = true) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        when (keepUpNextMediaId) {
            true -> {
                // reset player state - keep up next
                settings.edit {
                    putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String())
                    putInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
                    putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
                }
            }
            false -> {
                // reset player state - also reset up next
                settings.edit {
                    putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String())
                    putInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
                    putFloat(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED, 1f)
                    putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String())
                }
            }
        }
    }


    /* Start watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun registerPreferenceChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(listener)
    }


    /* Stop watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun unregisterPreferenceChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(listener)
    }


    /* Checks if housekeeping work needs to be done - used usually in DownloadWorker "REQUEST_UPDATE_COLLECTION" */
    fun isHouseKeepingNecessary(context: Context): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, true)
    }


    /* Saves state of housekeeping */
    fun saveHouseKeepingNecessaryState(context: Context, state: Boolean = false) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit {
            putBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, state)
        }
    }


    /* Load currently selected app theme */
    fun loadThemeSelection(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_THEME_SELECTION, Keys.STATE_THEME_FOLLOW_SYSTEM) ?: Keys.STATE_THEME_FOLLOW_SYSTEM
    }


    /* Returns a readable String for currently selected App Theme */
    fun getCurrentTheme(context: Context): String {
        return when (loadThemeSelection(context)) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }

    /* Returns a readable String for currently selected podcast search provider */
    fun getCurrentPodcastSearchProvider(context: Context): String {
        return when (loadCurrentPodcastSearchIndex(context)) {
            Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX -> context.getString(R.string.pref_search_provider_selection_podcastindex)
            else -> context.getString(R.string.pref_search_provider_selection_gpodder)
        }
    }


}
