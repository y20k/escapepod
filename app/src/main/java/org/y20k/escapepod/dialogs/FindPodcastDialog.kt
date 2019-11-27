package org.y20k.escapepod.dialogs

import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.*
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.gson.GsonBuilder
import org.y20k.escapepod.R
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.search.GpodderResult
import org.y20k.escapepod.search.GpodderResultAdapter

//import kotlin.collections.HashMap


/*
 * FindPodcastDialog class
 */
class FindPodcastDialog (private var findPodcastDialogListener: FindPodcastDialogListener): GpodderResultAdapter.GpodderResultAdapterListener {

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
    private lateinit var searchRequestProgressBar: ProgressBar
    private lateinit var noSearchResultsTextView: MaterialTextView
    private lateinit var podcastSearchResultList: RecyclerView
    private lateinit var searchResultAdapter: GpodderResultAdapter
    private lateinit var requestQueue: RequestQueue
    private var result: Array<GpodderResult> = arrayOf()
    private val handler: Handler = Handler()
    private var podcastFeedLocation: String = String()


    /* Overrides onSearchResultTapped from GpodderResultAdapterListener */
    override fun onSearchResultTapped(url: String) {
        activateAddButton(url)
    }


    /* Construct and show dialog */
    fun show(context: Context) {

        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_find_podcast_title)

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_find_podcast, null)
        podcastSearchBoxView = view.findViewById(R.id.podcast_search_box_view)
        searchRequestProgressBar = view.findViewById(R.id.search_request_progress_bar)
        podcastSearchResultList = view.findViewById(R.id.podcast_search_result_list)
        noSearchResultsTextView = view.findViewById(R.id.no_results_text_view)
        noSearchResultsTextView.visibility = View.GONE

        // set up list of search results
        setupRecyclerView(context)

        // add okay ("import") button
        builder.setPositiveButton(R.string.dialog_find_podcast_button_add) { _, _ ->
            // listen for click on add button
            findPodcastDialogListener.onFindPodcastDialog(podcastFeedLocation)
        }
        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            if (this::requestQueue.isInitialized) {
                requestQueue.stop()
            }
        }
        // handle outside-click as "no"
        builder.setOnCancelListener(){
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



    private fun handleSearchBoxInput(context: Context, query: String) {
        when {
            query.isEmpty() -> resetLayout()
            query.startsWith("http") -> activateAddButton(query)
            else -> search(context, query)
        }
    }


    private fun handleSearchBoxLiveInput(context: Context, query: String) {
        if (query.startsWith("http")) {
            activateAddButton(query)
        } else if (query.contains(" ") || query.length > 4) {
            handler.postDelayed(object : Runnable {
                override fun run() { search(context, query) }
            }, 250)
        } else {
            resetLayout()
        }
    }


    private fun activateAddButton(query: String) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        searchRequestProgressBar.visibility = View.GONE
        noSearchResultsTextView.visibility = View.GONE
        podcastFeedLocation = query
    }


    private fun resetLayout() {
        LogHelper.v(TAG, "resetLayout") // todo remove
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressBar.visibility = View.GONE
        noSearchResultsTextView.visibility = View.GONE
    }


    private fun showNoResultsError() {
        LogHelper.v(TAG, "showNoResultsError") // todo remove
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressBar.visibility = View.GONE
        noSearchResultsTextView.visibility = View.VISIBLE
    }


    private fun showProgressIndicator() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressBar.visibility = View.VISIBLE
        noSearchResultsTextView.visibility = View.GONE
    }


    private fun search(context: Context, query: String) {
        // create queue and request
        requestQueue = Volley.newRequestQueue(context)
        val requestUrl = "https://gpodder.net/search.json?q=${query.replace(" ", "+")}"

        // request data from request URL
        val stringRequest = object: StringRequest(Request.Method.GET, requestUrl, responseListener, errorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["User-Agent"] = context.getString(R.string.app_name)
                return params
            }
        }

        // override retry policy
        stringRequest.retryPolicy = object : RetryPolicy {
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
        requestQueue.add(stringRequest)

        // show progress indicator
        showProgressIndicator()
    }


    private fun createGpodderResult(result: String): Array<GpodderResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<GpodderResult>::class.java)
    }


    private val responseListener: Response.Listener<String> = Response.Listener<String> { response ->
        if (response != null && response.isNotBlank()) {
            result = createGpodderResult(response)
            if (result.isNotEmpty()) {
                searchResultAdapter.searchResults = result
                searchResultAdapter.notifyDataSetChanged()
                resetLayout()
            } else if (searchResultAdapter.searchResults.isEmpty()) {
                showNoResultsError()
            }
        }
    }


    private val errorListener: Response.ErrorListener = Response.ErrorListener { error ->
        if (searchResultAdapter.searchResults.isEmpty()) {
            showNoResultsError()
        }
        LogHelper.w(TAG, "Error: $error")
    }



}