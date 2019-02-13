/*
 * PreferencesHelper.kt
 * Implements the PreferencesHelper object
 * A PreferencesHelper provides helper methods for the saving and loading values from shared preferences
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.preference.PreferenceManager
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.y20k.escapepods.Keys
import org.y20k.escapepods.ui.PlayerState
import java.util.*


/*
 * PreferencesHelper object
 */
object PreferencesHelper {


    /* Loads mediaId of current episode from shared preferences */
    fun loadCurrentMediaId(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_CURRENT_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of current episode to shared preferences */
    fun saveCurrentMediaId(context: Context, mediaId: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(Keys.PREF_CURRENT_MEDIA_ID, mediaId)
        editor.apply()
    }


    /* Loads mediaId of next episode in up next queue from shared preferences */
    fun loadUpNextMediaId(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(Keys.PREF_UP_NEXT_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of next episode in up next queue  to shared preferences */
    fun saveUpNextMediaId(context: Context, mediaId: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(Keys.PREF_UP_NEXT_MEDIA_ID, mediaId)
        editor.apply()
    }


    /* Loads state of playback for player / PlayerService from shared preferences */
    fun loadPlayerPlayBackState(context: Context): Int {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getInt(Keys.PREF_CURRENT_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
    }


    /* Saves state of playback for player / PlayerService to shared preferences */
    fun savePlayerPlayBackState(context: Context, playBackState: Int) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putInt(Keys.PREF_CURRENT_PLAYBACK_STATE, playBackState)
        editor.apply()
    }


    /* Loads last update from shared preferences */
    fun loadLastUpdateCollection(context: Context): Date {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val lastUpdateString: String = settings.getString(Keys.PREF_LAST_UPDATE_COLLECTION, "") ?: String()
        return DateHelper.convertFromRfc2822(lastUpdateString)
    }


    /* Saves last update to shared preferences */
    fun saveLastUpdateCollection(context: Context, lastUpdate: Date = Calendar.getInstance().time) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateHelper.convertToRfc2822(lastUpdate))
        editor.apply()
    }


    /* Loads active downloads from shared preferences */
    fun loadActiveDownloads(context: Context): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val activeDownloadsString: String = settings.getString(Keys.PREF_ACTIVE_DOWNLOADS, "") ?: String()
        return activeDownloadsString
    }


    /* Saves active downloads to shared preferences */
    fun saveActiveDownloads(context: Context, activeDownloadsString: String = String()) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        editor.apply()
    }



    /* Loads state of player user interface from shared preferences */
    fun loadPlayerState(context: Context): PlayerState {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val playerState: PlayerState = PlayerState()
        playerState.episodeMediaId = settings.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
        playerState.playbackState = settings.getInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackStateCompat.STATE_STOPPED)
        playerState.playbackPosition = settings.getLong(Keys.PREF_PLAYER_STATE_PLAYBACK_POSITION, 0)
        playerState.upNextEpisodeMediaId = settings.getString(Keys.PREF_UP_NEXT_MEDIA_ID, String()) ?: String()
        playerState.bottomSheetState = settings.getInt(Keys.PREF_PLAYER_STATE_BOTTOM_SHEET_STATE, BottomSheetBehavior.STATE_HIDDEN)
        playerState.sleepTimerState = settings.getInt(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_STATE, Keys.STATE_SLEEP_TIMER_STOPPED)
        return playerState
    }


    /* Saves state of player user interface to shared preferences */
    fun savePlayerState(context: Context, playerState: PlayerState) {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, playerState.episodeMediaId)
        editor.putInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, playerState.playbackState)
        editor.putLong(Keys.PREF_PLAYER_STATE_PLAYBACK_POSITION, playerState.playbackPosition)
        editor.putString(Keys.PREF_UP_NEXT_MEDIA_ID, playerState.upNextEpisodeMediaId)
        editor.putInt(Keys.PREF_PLAYER_STATE_BOTTOM_SHEET_STATE, playerState.bottomSheetState)
        editor.putInt(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_STATE, playerState.sleepTimerState)
        editor.apply()
    }


}