/*
 * SearchResult.kt
 * Implements the SearchResult class
 * A SearchResult contains data needed to display the search dialog results
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.search.results

import androidx.annotation.Keep
import com.google.gson.annotations.Expose


/*
 * SearchResult class
 */
// NOTE: This class needs to be excepted from Obfuscation to work with GSON
// therefore it is added to proguard-rules.pro with "-keep public class ..."
@Keep
data class SearchResult (@Expose val url: String,
                         @Expose val title: String,
                         @Expose val description: String)
