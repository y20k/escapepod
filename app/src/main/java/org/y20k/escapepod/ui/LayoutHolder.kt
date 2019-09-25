/*
 * LayoutHolder.kt
 * Implements the LayoutHolder class
 * A LayoutHolder hold references to the main views
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Vibrator
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.helpers.ImageHelper
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.helpers.UiHelper


/*
 * LayoutHolder class
 */
data class LayoutHolder(var activity: Activity) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(LayoutHolder::class.java)


    /* Main class variables */
    var swipeRefreshLayout: SwipeRefreshLayout
    var recyclerView: RecyclerView
    private var bottomSheet: ConstraintLayout
    private var playerViews: Group
    private var upNextViews: Group
    private var topButtonViews: Group
    var sleepTimerRunningViews: Group
    private var coverView: ImageView
    private var podcastNameView: TextView
    private var episodeTitleView: TextView
    var playButtonView: ImageView
    private var sheetCoverView: ImageView
    var sheetProgressBarView: SeekBar
    private var sheetTimePlayedView: TextView
    private var sheetDurationView: TextView
    private var sheetEpisodeTitleView: TextView
    var sheetPlayButtonView: ImageView
    var sheetSkipBackButtonView: ImageView
    var sheetSkipForwardButtonView: ImageView
    var sheetSleepTimerStartButtonView: ImageView
    var sheetSleepTimerCancelButtonView: ImageView
    private var sheetSleepTimerRemainingTimeView: TextView
    var sheetDebugToggleButtonView: ImageView
    var sheetPlaybackSpeedButtonView: TextView
    var sheetUpNextName: TextView
    var sheetUpNextClearButton: ImageView
    private var onboardingLayout: ConstraintLayout
    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>


    /* Init block */
    init {
        // find views
        activity.setContentView(R.layout.activity_podcast_player)
        swipeRefreshLayout = activity.findViewById(R.id.swipe_refresh_layout)
        recyclerView = activity.findViewById(R.id.podcast_list)
        bottomSheet = activity.findViewById(R.id.bottom_sheet)
        playerViews = activity.findViewById(R.id.player_views)
        upNextViews = activity.findViewById(R.id.up_next_views)
        topButtonViews = activity.findViewById(R.id.top_button_views)
        sleepTimerRunningViews = activity.findViewById(R.id.sleep_timer_running_views)
        coverView = activity.findViewById(R.id.player_podcast_cover)
        podcastNameView = activity.findViewById(R.id.player_podcast_name)
        episodeTitleView = activity.findViewById(R.id.player_episode_title)
        playButtonView = activity.findViewById(R.id.player_play_button)
        sheetCoverView = activity.findViewById(R.id.sheet_large_podcast_cover)
        sheetProgressBarView = activity.findViewById(R.id.sheet_playback_seek_bar)
        sheetTimePlayedView = activity.findViewById(R.id.sheet_time_played_view)
        sheetDurationView = activity.findViewById(R.id.sheet_duration_view)
        sheetEpisodeTitleView = activity.findViewById(R.id.sheet_episode_title)
        sheetPlayButtonView = activity.findViewById(R.id.sheet_play_button)
        sheetSkipBackButtonView = activity.findViewById(R.id.sheet_skip_back_button)
        sheetSkipForwardButtonView = activity.findViewById(R.id.sheet_skip_forward_button)
        sheetSleepTimerStartButtonView = activity.findViewById(R.id.sleep_timer_start_button)
        sheetSleepTimerCancelButtonView = activity.findViewById(R.id.sleep_timer_cancel_button)
        sheetSleepTimerRemainingTimeView = activity.findViewById(R.id.sleep_timer_remaining_time)
        sheetDebugToggleButtonView = activity.findViewById(R.id.debug_log_toggle_button)
        sheetPlaybackSpeedButtonView = activity.findViewById(R.id.playback_speed_button)
        sheetUpNextName = activity.findViewById(R.id.sheet_up_next_name)
        sheetUpNextClearButton = activity.findViewById(R.id.sheet_up_next_clear_button)
        onboardingLayout = activity.findViewById(R.id.onboarding_layout)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)


        // set layouts for list and player
        setupRecyclerView()
        setupBottomSheet()
    }


    /* Updates the player views */
    fun updatePlayerViews(context: Context, episode: Episode) {
        val coverUri = Uri.parse(episode.cover)
        coverView.setImageBitmap(ImageHelper.getPodcastCover(context,coverUri, Keys.SIZE_COVER_PLAYER))
        coverView.clipToOutline = true // apply rounded corner mask to covers
        coverView.contentDescription = "${context.getString(R.string.descr_player_podcast_cover)}: ${episode.podcastName}"
        podcastNameView.text = episode.podcastName
        episodeTitleView.text = episode.title
        sheetCoverView.setImageURI(coverUri)
        sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
        sheetCoverView.contentDescription = "${context.getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.podcastName}"
        sheetEpisodeTitleView.text = episode.title
        sheetDurationView.text = DateTimeHelper.convertToMinutesAndSeconds(episode.duration)
        sheetProgressBarView.max = episode.duration.toInt()

        // update click listeners
        sheetCoverView.setOnClickListener{
            ShowNotesDialog().show(context, episode)
        }
        sheetEpisodeTitleView.setOnClickListener {
            ShowNotesDialog().show(context, episode)
        }
        podcastNameView.setOnLongClickListener{ view ->
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            ShowNotesDialog().show(context, episode)
            return@setOnLongClickListener true
        }
        episodeTitleView.setOnLongClickListener{
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            ShowNotesDialog().show(context, episode)
            return@setOnLongClickListener true
        }
    }


    /* Updates the progress bar */
    fun updateProgressbar(position: Long) {
        sheetTimePlayedView.text = DateTimeHelper.convertToMinutesAndSeconds(position)
        sheetProgressBarView.progress = position.toInt()
    }


    /* Updates the playback speed view */
    fun updatePlaybackSpeedView(speed: Float = 1f) {
        val playbackSpeedButtonText: String = "$speed x"
        sheetPlaybackSpeedButtonView.text = playbackSpeedButtonText
    }


    /* Updates the Up Next views */
    fun updateUpNextViews(upNextEpisode: Episode) {
        when (upNextEpisode.getMediaId().isNotEmpty()) {
            true -> {
                // show the up next queue if queue is not empty
                upNextViews.visibility = View.GONE // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965)
                upNextViews.visibility = View.VISIBLE
                // update up next view
                val upNextName = "${upNextEpisode.podcastName} - ${upNextEpisode.title}"
                sheetUpNextName.text = upNextName
            }
            false -> {
                // hide the up next queue if queue is empty
                upNextViews.visibility = View.GONE // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965)
                upNextViews.visibility = View.INVISIBLE

            }
        }
    }


    /* Updates sleep timer views */
    fun updateSleepTimer(timeRemaining: Long = 0L) {
        when (timeRemaining) {
            0L -> {
                sleepTimerRunningViews.visibility = View.GONE
            }
            else -> {
                if (topButtonViews.visibility == View.VISIBLE) {
                    sleepTimerRunningViews.visibility = View.VISIBLE
                    sheetSleepTimerRemainingTimeView.text = DateTimeHelper.convertToMinutesAndSeconds(timeRemaining)
                }
            }
        }
    }


    /* Toggles play/pause buttons */
    fun togglePlayButtons(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                playButtonView.setImageResource(R.drawable.ic_pause_symbol_white_36dp)
                sheetPlayButtonView.setImageResource(R.drawable.ic_pause_symbol_white_54dp)
            }
            else -> {
                playButtonView.setImageResource(R.drawable.ic_play_symbol_white_36dp)
                sheetPlayButtonView.setImageResource(R.drawable.ic_play_symbol_white_54dp)
            }
        }
    }


    /* Toggles visibility of player depending on playback state - hiding it when playback is stopped (not paused or playing) */
    fun togglePlayerVisibility(context: Context, playbackState: Int): Boolean {
        when (playbackState) {
            PlaybackStateCompat.STATE_STOPPED -> return hidePlayer(context)
            PlaybackStateCompat.STATE_NONE -> return hidePlayer(context)
            PlaybackStateCompat.STATE_ERROR -> return hidePlayer(context)
            else -> return showPlayer(context)
        }
    }


    fun toggleOnboarding(context: Context, collectionSize: Int) {
        if (collectionSize == 0) {
            onboardingLayout.visibility = View.VISIBLE
            hidePlayer(context)
        } else {
            onboardingLayout.visibility = View.GONE
        }
    }



    /* Initiates the rotation animation of the play button  */
    fun animatePlaybackButtonStateTransition(context: Context, playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                val rotateClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButtonView.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButtonView.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_SETTLING -> togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_HIDDEN -> togglePlayButtons(playbackState)
                }
            }

            else -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButtonView.startAnimation(rotateCounterClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButtonView.startAnimation(rotateCounterClockwise)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_SETTLING -> togglePlayButtons(playbackState)
                    BottomSheetBehavior.STATE_HIDDEN -> togglePlayButtons(playbackState)
                }
            }

        }
    }


    /* Shows player */
    private fun showPlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, swipeRefreshLayout, 0,0,0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        return true
    }


    /* Hides player */
    private fun hidePlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, swipeRefreshLayout, 0,0,0, 0)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }


    /* Creates AnimationListener for play button */
    private fun createAnimationListener(playbackState: Int): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // set up button symbol and playback indicator afterwards
                togglePlayButtons(playbackState)
            }
            override fun onAnimationRepeat(animation: Animation) {}
        }
    }



    /* Sets up list of podcasts (RecyclerView) */
    private fun setupRecyclerView() {
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(activity) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()
    }


    /* Sets up the player (BottomSheet) */
    private fun setupBottomSheet() {
        // show / hide the small player
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.bottomSheetCallback = object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
                if (slideOffset < 0.25f) {
                    showPlayerViews()
                } else {
                    hidePlayerViews()
                }
            }
            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> showPlayerViews()
                    BottomSheetBehavior.STATE_DRAGGING -> Unit // do nothing
                    BottomSheetBehavior.STATE_EXPANDED -> hidePlayerViews()
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  Unit // do nothing
                    BottomSheetBehavior.STATE_SETTLING -> Unit // do nothing
                    BottomSheetBehavior.STATE_HIDDEN -> showPlayerViews()
                }
            }
        }
        // toggle collapsed state on tap
        bottomSheet.setOnClickListener {
            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }


    /* Shows player views and hides of the top button views */
    private fun showPlayerViews() {
        playerViews.visibility = View.VISIBLE
        topButtonViews.visibility = View.GONE
    }


    /* Hides player views in favor of the top button views */
    private fun hidePlayerViews() {
        playerViews.visibility = View.GONE
        topButtonViews.visibility = View.VISIBLE
    }

}