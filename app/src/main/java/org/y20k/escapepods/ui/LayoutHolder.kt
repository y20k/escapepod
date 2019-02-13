/*
 * LayoutHolder.kt
 * Implements the LayoutHolder class
 * A LayoutHolder hold references to the main views
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.y20k.escapepods.Keys
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.UiHelper


/*
 * LayoutHolder class
 */
data class LayoutHolder(var activity: Activity) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(LayoutHolder::class.java)


    /* Main class variables */
    var swipeRefreshLayout: SwipeRefreshLayout
    var recyclerView: RecyclerView
    var bottomSheet: ConstraintLayout
    var playerViews: Group
    var upNextViews: Group
    var coverView: ImageView
    var podcastNameView: TextView
    var episodeTitleView: TextView
    var playButtonView: ImageView
    var sheetCoverView: ImageView
    var sheetEpisodeTitleView: TextView
    var sheetPlayButtonView: ImageView
    var sheetSleepButtonView: ImageView
    var sheetUpNextName: TextView
    var sheetUpNextClearButton: ImageView
    var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>


    /* Init block */
    init {
        // find views
        activity.setContentView(R.layout.activity_podcast_player)
        swipeRefreshLayout = activity.findViewById(R.id.layout_swipe_refresh)
        recyclerView = activity.findViewById(R.id.recyclerview_list)
        bottomSheet = activity.findViewById(R.id.bottom_sheet)
        playerViews = activity.findViewById(R.id.player_views)
        upNextViews = activity.findViewById(R.id.up_next_views)
        coverView = activity.findViewById(R.id.player_podcast_cover)
        podcastNameView = activity.findViewById(R.id.player_podcast_name)
        episodeTitleView = activity.findViewById(R.id.player_episode_title)
        playButtonView = activity.findViewById(R.id.player_play_button)
        sheetCoverView = activity.findViewById(R.id.sheet_large_podcast_cover)
        sheetEpisodeTitleView = activity.findViewById(R.id.sheet_episode_title)
        sheetPlayButtonView = activity.findViewById(R.id.sheet_play_button)
        sheetSleepButtonView = activity.findViewById(R.id.sleep_timer_button)
        sheetUpNextName = activity.findViewById(R.id.player_sheet_up_next_name)
        sheetUpNextClearButton = activity.findViewById(R.id.player_sheet_up_next_clear_button)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)


        // set layouts for list and player
        setupRecyclerView()
        setupBottomSheet()
    }


    /* Updates the player views */
    fun updatePlayerViews(context: Context, episode: Episode) {
        val coverUri = Uri.parse(episode.cover)
        coverView.setImageURI(coverUri)
        coverView.clipToOutline = true // apply rounded corner mask to covers
        coverView.contentDescription = "${context.getString(R.string.descr_player_podcast_cover)}: ${episode.podcastName}"
        podcastNameView.text = episode.podcastName
        episodeTitleView.text = episode.title
        sheetCoverView.setImageURI(coverUri)
        sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
        sheetCoverView.contentDescription = "${context.getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.podcastName}"
        sheetEpisodeTitleView.text = episode.title
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
    fun togglePlayerVisibility(context: Context, playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_STOPPED -> hidePlayer(context)
            PlaybackStateCompat.STATE_NONE -> hidePlayer(context)
            PlaybackStateCompat.STATE_ERROR -> hidePlayer(context)
            else -> showPlayer(context)
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
    private fun showPlayer(context: Context) {
        UiHelper.setViewMargins(context, swipeRefreshLayout, 0,0,0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }


    /* Hides player */
    private fun hidePlayer(context: Context) {
        UiHelper.setViewMargins(context, swipeRefreshLayout, 0,0,0, 0)
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
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
        recyclerView.setLayoutManager(layoutManager)
        recyclerView.setItemAnimator(DefaultItemAnimator())
    }


    /* Sets up the player (BottomSheet) */
    private fun setupBottomSheet() {
        // show / hide the small player
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.setBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
                if (slideOffset < 0.25f) {
                    playerViews.setVisibility(View.VISIBLE);
                } else {
                    playerViews.setVisibility(View.GONE);
                }
            }
            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playerViews.setVisibility(View.VISIBLE)
                    BottomSheetBehavior.STATE_DRAGGING -> Unit // do nothing
                    BottomSheetBehavior.STATE_EXPANDED -> playerViews.setVisibility(View.GONE)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  Unit // do nothing
                    BottomSheetBehavior.STATE_SETTLING -> Unit // do nothing
                    BottomSheetBehavior.STATE_HIDDEN -> playerViews.setVisibility(View.VISIBLE)
                }
            }
        })
        // toggle collapsed state on tap
        bottomSheet.setOnClickListener {
            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }


}