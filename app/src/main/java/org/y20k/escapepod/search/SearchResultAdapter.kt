/*
 * SearchResultAdapter.kt
 * Implements the SearchResultAdapter class
 * A SearchResultAdapter is a custom adapter providing search result views for a RecyclerView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import org.y20k.escapepod.R
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.search.results.SearchResult


/*
 * SearchResultAdapter class
 */
class SearchResultAdapter(private val listener: SearchResultAdapterListener, var searchResults: Array<SearchResult>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(SearchResultAdapter::class.java)


    /* Main class variables */
    private var selectedPosition: Int = RecyclerView.NO_POSITION


    /* Listener Interface */
    interface SearchResultAdapterListener {
        fun onSearchResultTapped(url: String)
    }


    init {
        setHasStableIds(true)
    }


    /* Overrides onCreateViewHolder from RecyclerView.Adapter */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.element_search_result, parent, false)
        return SearchResultViewHolder(v)
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemCount(): Int {
        return searchResults.size
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemId(position: Int): Long = position.toLong()


    /* Overrides onBindViewHolder from RecyclerView.Adapter */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // get reference to ViewHolder
        val searchResultViewHolder: SearchResultViewHolder = holder as SearchResultViewHolder
        val searchResult: SearchResult = searchResults[position]
        // update text
        searchResultViewHolder.podcastNameView.text = searchResult.title
        searchResultViewHolder.podcastDescriptionView.text = searchResult.description
        // mark selected if necessary
        searchResultViewHolder.searchResultLayout.isSelected = selectedPosition == position
        // toggle text scrolling (marquee) if necessary
        searchResultViewHolder.podcastNameView.isSelected = selectedPosition == position
        searchResultViewHolder.podcastDescriptionView.isSelected = selectedPosition == position
        // attach touch listener
        searchResultViewHolder.searchResultLayout.setOnClickListener {
            // move marked position
            notifyItemChanged(selectedPosition)
            selectedPosition = position
            notifyItemChanged(selectedPosition)
            // hand over url
            listener.onSearchResultTapped(searchResult.url)
        }
    }


    /* Resets the selected position */
    fun resetSelection(clearAdapter: Boolean) {
        val currentlySelected: Int = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (clearAdapter) {
            searchResults = arrayOf()
            notifyDataSetChanged()
        } else {
            notifyItemChanged(currentlySelected)
        }
    }



    /*
     * Inner class: ViewHolder for a podcast search result
     */
    private inner class SearchResultViewHolder (var searchResultLayout: View): RecyclerView.ViewHolder(searchResultLayout) {
        val podcastNameView: MaterialTextView = searchResultLayout.findViewById(R.id.podcast_name)
        val podcastDescriptionView: MaterialTextView = searchResultLayout.findViewById(R.id.result_podcast_description)
    }
    /*
     * End of inner class
     */

}
