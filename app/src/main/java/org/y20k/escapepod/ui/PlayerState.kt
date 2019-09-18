/*
 * PlayerState.kt
 * Implements the PlayerState class
 * A PlayerState holds parameters describing the state of the player part of the UI
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.ui

import android.os.Parcelable
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import org.y20k.escapepod.Keys


/*
 * PlayerState class
 */
@Parcelize
data class PlayerState (@Expose var episodeMediaId: String = String(),
                        @Expose var playbackState: Int = PlaybackStateCompat.STATE_STOPPED,
                        @Expose var playbackPosition: Long = 0,
                        @Expose var playbackSpeed: Float = 1f,
                        @Expose var upNextEpisodeMediaId: String = String(),
                        @Expose var bottomSheetState: Int = BottomSheetBehavior.STATE_HIDDEN,
                        @Expose var sleepTimerState: Int = Keys.STATE_SLEEP_TIMER_STOPPED): Parcelable {
}
