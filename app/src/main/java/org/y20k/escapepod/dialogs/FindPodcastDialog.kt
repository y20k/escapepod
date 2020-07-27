/*
 * FindPodcastDialog.kt
 * Implements the FindPodcastDialog class
 * A FindPodcastDialog shows a dialog with search box and list of results
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.*
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.gson.GsonBuilder
import org.json.JSONArray
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.search.GpodderResult
import org.y20k.escapepod.search.GpodderResultAdapter


/*
 * FindPodcastDialog class
 */
class FindPodcastDialog (private var context: Context, private var listener: FindPodcastDialogListener): GpodderResultAdapter.GpodderResultAdapterListener {

    /* Interface used to communicate back to activity */
    interface FindPodcastDialogListener {
        fun onFindPodcastDialog(remotePodcastFeedLocation: String) {
        }
    }

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(FindPodcastDialog::class.java.simpleName)


    /* Main class variables */
    private lateinit var dialog: AlertDialog
    private lateinit var podcastSearchBoxView: SearchView
    private lateinit var searchRequestProgressIndicator: ProgressBar
    private lateinit var noSearchResultsTextView: MaterialTextView
    private lateinit var podcastSearchResultList: RecyclerView
    private lateinit var searchResultAdapter: GpodderResultAdapter
    private lateinit var requestQueue: RequestQueue
    private var currentSearchString: String = String()
    private var result: Array<GpodderResult> = arrayOf()
    private val handler: Handler = Handler()
    private var podcastFeedLocation: String = String()


    /* Overrides onSearchResultTapped from GpodderResultAdapterListener */
    override fun onSearchResultTapped(url: String) {
        activateAddButton(url)
    }


    /* Construct and show dialog */
    fun show() {

        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_find_podcast_title)

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_find_podcast, null)
        podcastSearchBoxView = view.findViewById(R.id.podcast_search_box_view)
        searchRequestProgressIndicator = view.findViewById(R.id.search_request_progress_indicator)
        podcastSearchResultList = view.findViewById(R.id.podcast_search_result_list)
        noSearchResultsTextView = view.findViewById(R.id.no_results_text_view)
        noSearchResultsTextView.visibility = View.GONE

        // set up list of search results
        setupRecyclerView(context)

        // add okay ("import") button
        builder.setPositiveButton(R.string.dialog_find_podcast_button_add) { _, _ ->
            // listen for click on add button
            (listener).onFindPodcastDialog(podcastFeedLocation)
        }
        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            if (this::requestQueue.isInitialized) {
                requestQueue.stop()
            }
        }
        // handle outside-click as "no"
        builder.setOnCancelListener {
            if (this::requestQueue.isInitialized) {
                requestQueue.stop()
            }
        }

        // listen for input
        podcastSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearchBoxLiveInput(context, query)
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearchBoxInput(context, query)
                return true
            }
        })


        // set dialog view
        builder.setView(view)

        // create and display dialog
        dialog = builder.create()
        dialog.show()

        // initially disable "Add" button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

    }


    /* Sets up list of results (RecyclerView) */
    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = GpodderResultAdapter(this, result)
        podcastSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        podcastSearchResultList.layoutManager = layoutManager
        podcastSearchResultList.itemAnimator = DefaultItemAnimator()
    }



    /* Handles user input into search box - user has to submit the search */
    private fun handleSearchBoxInput(context: Context, query: String) {
        when {
            // handle empty search box input
            query.isEmpty() -> resetLayout(clearAdapter = true)
            // handle direct URL input
            query.startsWith("http") -> activateAddButton(query)
            // handle search string input
            else -> {
                showProgressIndicator()
                search(context, query)
            }
        }
    }


    /* Handles live user input into search box */
    private fun handleSearchBoxLiveInput(context: Context, query: String) {
        currentSearchString = query
        if (query.startsWith("http")) {
            // handle direct URL input
            activateAddButton(query)
        } else if (query.contains(" ") || query.length > 4) {
            // show progress indicator
            showProgressIndicator()
            // handle search string input - delay request to manage server load (not sure if necessary)
            handler.postDelayed({
                // only start search if query is the same as one second ago
                if (currentSearchString == query) search(context, query)
            }, 1000)
        } else if (query.isEmpty()) {
            resetLayout(clearAdapter = true)
        }
    }


    /* Makes the "Add" button clickable */
    private fun activateAddButton(query: String) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        searchRequestProgressIndicator.visibility = View.GONE
        noSearchResultsTextView.visibility = View.GONE
        podcastFeedLocation = query
        val imm: InputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(podcastSearchBoxView.windowToken, 0)
    }


    /* Resets the dialog layout to default state */
    private fun resetLayout(clearAdapter: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.visibility = View.GONE
        noSearchResultsTextView.visibility = View.GONE
        searchResultAdapter.resetSelection(clearAdapter)
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showNoResultsError() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.visibility = View.GONE
        noSearchResultsTextView.visibility = View.VISIBLE
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showProgressIndicator() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.visibility = View.VISIBLE
        noSearchResultsTextView.visibility = View.GONE
    }


    /* Initiates podcast search on gpodder.net */
    private fun search(context: Context, query: String) {
        LogHelper.v(TAG, "Search - Querying gpodder.net for: $query")
        // create queue and request
        requestQueue = Volley.newRequestQueue(context)
        val requestUrl = "https://gpodder.net/search.json?q=${query.replace(" ", "+")}"

        // request data from request URL
        val jsonArrayRequest = object: JsonArrayRequest(Method.GET, requestUrl, null, responseListener, errorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["User-Agent"] = "$Keys.APPLICATION_NAME ${BuildConfig.VERSION_NAME}"
                return params
            }
        }

        // override retry policy
        jsonArrayRequest.retryPolicy = object : RetryPolicy {
            override fun getCurrentTimeout(): Int {
                return 30000
            }
            override fun getCurrentRetryCount(): Int {
                return 30000
            }
            @Throws(VolleyError::class)
            override fun retry(error: VolleyError) {
                LogHelper.w(TAG, "Error: $error")
            }
        }

        // add to RequestQueue.
        requestQueue.add(jsonArrayRequest)
    }


    /* Listens for (positive) server responses to search requests */
    private val responseListener: Response.Listener<JSONArray> = Response.Listener<JSONArray> { response ->
        if (response != null) {
            result = createGpodderResult(response.toString())
            if (result.isNotEmpty()) {
                searchResultAdapter.searchResults = result
                searchResultAdapter.notifyDataSetChanged()
                resetLayout(clearAdapter = false)
            } else {
                showNoResultsError()
            }
        }
    }


    /* Listens for error response from server */
    private val errorListener: Response.ErrorListener = Response.ErrorListener { error ->
        LogHelper.w(TAG, "Error: $error")
    }


    /* Converts search result JSON string */
    private fun createGpodderResult(result: String): Array<GpodderResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<GpodderResult>::class.java)
    }


}