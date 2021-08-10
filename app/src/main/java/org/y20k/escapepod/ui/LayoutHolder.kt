/*
 * LayoutHolder.kt
 * Implements the LayoutHolder class
 * A LayoutHolder hold references to the main views
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Vibrator
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.*


/*
 * LayoutHolder class
 */
data class LayoutHolder(val rootView: View, val collectionDatabase: CollectionDatabase) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(LayoutHolder::class.java)


    /* Main class variables */
    var swipeRefreshLayout: SwipeRefreshLayout
    var recyclerView: RecyclerView
    val layoutManager: LinearLayoutManager
    private var bottomSheet: ConstraintLayout
    private var playerViews: Group
    private var upNextViews: Group
    private var topButtonViews: Group
    var sleepTimerRunningViews: Group
    private var downloadProgressIndicator: ProgressBar
    private var coverView: ImageView
    private var podcastNameView: TextView
    private var episodeTitleView: TextView
    var playButtonView: ImageView
    private var sheetCoverView: ImageView
    private var sheetShownotesSymbolView: ImageView
    var sheetProgressBarView: SeekBar
    var sheetTimePlayedView: TextView
    var sheetDurationView: TextView
    private var sheetEpisodeTitleView: TextView
    var sheetPlayButtonView: ImageView
    var sheetSkipBackButtonView: ImageView
    var sheetSkipForwardButtonView: ImageView
    var sheetSleepTimerStartButtonView: ImageView
    var sheetSleepTimerCancelButtonView: ImageView
    var sheetSleepTimerRemainingTimeView: TextView
    var sheetDebugToggleButtonView: ImageView
    var sheetPlaybackSpeedButtonView: TextView
    var sheetUpNextName: TextView
    var sheetUpNextClearButton: ImageView
    private var onboardingLayout: ConstraintLayout
    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    var displayTimeRemaining: Boolean


    /* Init block */
    init {
        // find views
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout)
        recyclerView = rootView.findViewById(R.id.podcast_list)
        bottomSheet = rootView.findViewById(R.id.bottom_sheet)
        playerViews = rootView.findViewById(R.id.player_views)
        upNextViews = rootView.findViewById(R.id.up_next_views)
        topButtonViews = rootView.findViewById(R.id.top_button_views)
        sleepTimerRunningViews = rootView.findViewById(R.id.sleep_timer_running_views)
        downloadProgressIndicator = rootView.findViewById(R.id.download_progress_indicator)
        coverView = rootView.findViewById(R.id.player_podcast_cover)
        podcastNameView = rootView.findViewById(R.id.podcast_name)
        episodeTitleView = rootView.findViewById(R.id.player_episode_title)
        playButtonView = rootView.findViewById(R.id.player_play_button)
        sheetCoverView = rootView.findViewById(R.id.sheet_large_podcast_cover)
        sheetShownotesSymbolView = rootView.findViewById(R.id.sheet_cover_shownotes_symbol)
        sheetProgressBarView = rootView.findViewById(R.id.sheet_playback_seek_bar)
        sheetTimePlayedView = rootView.findViewById(R.id.sheet_time_played_view)
        sheetDurationView = rootView.findViewById(R.id.sheet_duration_view)
        sheetEpisodeTitleView = rootView.findViewById(R.id.sheet_episode_title)
        sheetPlayButtonView = rootView.findViewById(R.id.sheet_play_button)
        sheetSkipBackButtonView = rootView.findViewById(R.id.sheet_skip_back_button)
        sheetSkipForwardButtonView = rootView.findViewById(R.id.sheet_skip_forward_button)
        sheetSleepTimerStartButtonView = rootView.findViewById(R.id.sleep_timer_start_button)
        sheetSleepTimerCancelButtonView = rootView.findViewById(R.id.sleep_timer_cancel_button)
        sheetSleepTimerRemainingTimeView = rootView.findViewById(R.id.sleep_timer_remaining_time)
        sheetDebugToggleButtonView = rootView.findViewById(R.id.debug_log_toggle_button)
        sheetPlaybackSpeedButtonView = rootView.findViewById(R.id.playback_speed_button)
        sheetUpNextName = rootView.findViewById(R.id.sheet_up_next_name)
        sheetUpNextClearButton = rootView.findViewById(R.id.sheet_up_next_clear_button)
        onboardingLayout = rootView.findViewById(R.id.onboarding_layout)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        displayTimeRemaining = false

        // set up RecyclerView
        layoutManager = CustomLayoutManager(rootView.context)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()

        // set layout for player
        setupBottomSheet()
    }


    /* Updates the player views */
    @SuppressLint("ClickableViewAccessibility")
    fun updatePlayerViews(context: Context, episode: Episode?) {
        if (episode != null) {
            val duration: String
            if (episode.duration > 0L) duration = DateTimeHelper.convertToMinutesAndSeconds(episode.duration) else duration = "∞"
            coverView.setImageBitmap(ImageHelper.getPodcastCover(context, episode.smallCover))
            coverView.clipToOutline = true // apply rounded corner mask to covers
            coverView.contentDescription = "${context.getString(R.string.descr_player_podcast_cover)}: ${episode.podcastName}"
            podcastNameView.text = episode.podcastName
            episodeTitleView.text = episode.title
            sheetCoverView.setImageBitmap(ImageHelper.getPodcastCover(context, episode.cover))
            sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
            sheetCoverView.contentDescription = "${context.getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.podcastName}"
            sheetEpisodeTitleView.text = episode.title
            sheetDurationView.text = duration
            sheetDurationView.contentDescription = "${context.getString(R.string.descr_expanded_episode_length)}: $duration"
            sheetProgressBarView.max = episode.duration.toInt()

            // setup progress bar
            updateProgressbar(context, episode.playbackPosition, episode.duration)

            // update click listeners
            sheetCoverView.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sheetShownotesSymbolView.visibility = View.VISIBLE
                        return@OnTouchListener true
                    }
                    MotionEvent.ACTION_UP -> {
                        sheetShownotesSymbolView.visibility = View.GONE
                        displayShowNotes(context, episode)
                        return@OnTouchListener true
                    }
                    else -> return@OnTouchListener false
                }
            })
//            sheetCoverView.setOnClickListener{
//                displayShowNotes(context, episode)
//            }
            sheetEpisodeTitleView.setOnClickListener {
                displayShowNotes(context, episode)
            }
            podcastNameView.setOnLongClickListener{ view ->
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(50)
                displayShowNotes(context, episode)
                return@setOnLongClickListener true
            }
            episodeTitleView.setOnLongClickListener{
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(50)
                displayShowNotes(context, episode)
                return@setOnLongClickListener true
            }
        }
    }


    /* Loads and displays show notes */
    private fun displayShowNotes(context: Context, episode: Episode) {
        CoroutineScope(IO).launch {
            val podcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
            val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
            if (episodeDescription != null && podcast != null) {
                withContext(Main) { ShowNotesDialog().show(context, podcast, episode, episodeDescription) }
            }
        }
    }


    /* Updates the progress bar */
    fun updateProgressbar(context: Context, position: Long, duration: Long = 0L) {
        val timePlayed = DateTimeHelper.convertToMinutesAndSeconds(position)
        sheetTimePlayedView.text = timePlayed
        sheetTimePlayedView.contentDescription = "${context.getString(R.string.descr_expanded_player_time_played)}: ${timePlayed}"
        sheetProgressBarView.progress = position.toInt()
        if (displayTimeRemaining) {
            val timeRemaining = DateTimeHelper.convertToMinutesAndSeconds((duration - position), negativeValue = true)
            sheetDurationView.text = timeRemaining
            sheetDurationView.contentDescription = "${context.getString(R.string.descr_expanded_player_time_remaining)}: ${timeRemaining}"
        }
    }


    /* Updates the playback speed view */
    fun updatePlaybackSpeedView(context: Context, speed: Float = 1f) {
        val playbackSpeedButtonText: String = "$speed x"
        sheetPlaybackSpeedButtonView.text = playbackSpeedButtonText
        sheetPlaybackSpeedButtonView.contentDescription = "${playbackSpeedButtonText} - ${context.getString(R.string.descr_expanded_player_playback_speed_button)}"
    }


    /* Updates the Up Next views */
    fun updateUpNextViews(upNextEpisode: Episode?) {
        when (upNextEpisode != null) {
            true -> {
                // show the up next queue if queue is not empty
                upNextViews.isGone = true // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965)
                upNextViews.isVisible = true
                // update up next view
                val upNextName = "${upNextEpisode.podcastName} - ${upNextEpisode.title}"
                sheetUpNextName.text = upNextName
            }
            false -> {
                // hide the up next queue if queue is empty
                upNextViews.isGone = true // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965)
                upNextViews.isVisible = false

            }
        }
    }


    /* Updates sleep timer views */
    fun updateSleepTimer(context: Context, timeRemaining: Long = 0L) {
        when (timeRemaining) {
            0L -> {
                if (!sleepTimerRunningViews.isGone) {
                    sleepTimerRunningViews.isGone = true
                    sheetSleepTimerRemainingTimeView.text = String()
                }
            }
            else -> {
                if (topButtonViews.isVisible) {
                    sleepTimerRunningViews.isVisible = true
                    val sleepTimerTimeRemaining: String = DateTimeHelper.convertToMinutesAndSeconds(timeRemaining)
                    sheetSleepTimerRemainingTimeView.text = sleepTimerTimeRemaining
                    sheetSleepTimerRemainingTimeView.contentDescription = "${context.getString(R.string.descr_expanded_player_sleep_timer_remaining_time)}: ${sleepTimerTimeRemaining}"
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


    /* Minimizes player sheet if expanded */
    fun minimizePlayerIfExpanded(): Boolean {
        return if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
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


    /* Toggles visibility of the download progress indicator */
    fun toggleDownloadProgressIndicator() {
        when (PreferencesHelper.loadActiveDownloads()) {
            Keys.ACTIVE_DOWNLOADS_EMPTY -> downloadProgressIndicator.isGone = true
            else -> downloadProgressIndicator.isVisible = true
        }
    }


    fun toggleOnboarding(context: Context, collectionSize: Int): Boolean {
        if (collectionSize == 0) {
            onboardingLayout.isVisible = true
            hidePlayer(context)
            return true
        } else {
            onboardingLayout.isGone = true
            return false
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
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN || bottomSheetBehavior.state == BottomSheetBehavior.STATE_SETTLING) {
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


    /* Sets up the player (BottomSheet) */
    private fun setupBottomSheet() {
        // show / hide the small player
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
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
                    BottomSheetBehavior.STATE_DRAGGING -> sheetShownotesSymbolView.visibility = View.GONE
                    BottomSheetBehavior.STATE_EXPANDED -> hidePlayerViews()
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  Unit // do nothing
                    BottomSheetBehavior.STATE_SETTLING -> Unit // do nothing
                    BottomSheetBehavior.STATE_HIDDEN -> showPlayerViews()
                }
            }
        })
        // toggle collapsed state on tap
        bottomSheet.setOnClickListener { toggleBottomSheetState() }
        coverView.setOnClickListener { toggleBottomSheetState() }
        podcastNameView.setOnClickListener { toggleBottomSheetState() }
        episodeTitleView.setOnClickListener { toggleBottomSheetState() }
    }


    /* Shows player views and hides of the top button views */
    private fun showPlayerViews() {
        playerViews.isVisible = true
        topButtonViews.isGone = true
    }


    /* Hides player views in favor of the top button views */
    private fun hidePlayerViews() {
        playerViews.isGone = true
        topButtonViews.isVisible = true
        if (sheetSleepTimerRemainingTimeView.text.isEmpty()) {
            sleepTimerRunningViews.isGone = true
        }
    }


    /* Toggle expanded/collapsed state of bottom sheet */
    private fun toggleBottomSheetState() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }


    /*
     * Inner class: Custom LinearLayoutManager
     */
    private inner class CustomLayoutManager(context: Context): LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
    /*
     * End of inner class
     */


}
