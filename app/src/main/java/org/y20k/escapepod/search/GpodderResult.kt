/*
 * GpodderResult.kt
 * Implements the GpodderResult class
 * A GpodderResult is the search result of a request to the gpodder.net search API
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.search

import com.google.gson.annotations.Expose


/*
 * GpodderResult class
 */
data class GpodderResult (@Expose val url: String,
                          @Expose val title: String,
                          @Expose val description: String,
                          @Expose val subscribers: Int,
                          @Expose val subscribers_last_week: Int,
                          @Expose val logo_url: String,
                          @Expose val scaled_logo_url: String,
                          @Expose val website: String,
                          @Expose val mygpo_link: String) {

    // Documentation of format for GpodderResult - see:
    // https://gpoddernet.readthedocs.io/en/latest/api/reference/general.html#formats


    /* Converts GpodderResult to SearchResult */
    fun toSearchResult(): SearchResult = SearchResult(url = url, title = title, description = description)

}




