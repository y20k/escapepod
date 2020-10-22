/*
 * GpodderResult.kt
 * Implements the GpodderResult class
 * A GpodderResult is the search result of a request to the gpodder.net search API
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.search

import com.google.gson.annotations.Expose


/*
 * GpodderResult class
 */
data class SearchResult (@Expose val url: String,
                         @Expose val title: String,
                         @Expose val description: String)

// Documentation of format for GpodderResult - see:
// https://gpoddernet.readthedocs.io/en/latest/api/reference/general.html#formats