/*
 * PodcastindexResult.kt
 * Implements the PodcastindexResult class
 * A PodcastindexResult is the search result of a request to the podcastindex.org search API
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.search.results

import androidx.annotation.Keep
import com.google.gson.annotations.Expose


/*
 * PodcastindexResult class
 * Documentation of format for PodcastindexResult - see: https://podcastindex-org.github.io/docs-api/
 */
// NOTE: This class needs to be excepted from Obfuscation to work with GSON
// therefore it is added to proguard-rules.pro with "-keep public class ..."
@Keep
data class PodcastindexResult (@Expose val status: Boolean,
                               @Expose val feeds: List<Feed>,
                               @Expose val count: Int,
                               @Expose val query: String,
                               @Expose val description: String) {

    inner class Feed (@Expose val id: Int,
                     @Expose val title: String,
                     @Expose val url: String,
                     @Expose val originalUrl: String,
                     @Expose val link: String,
                     @Expose val description: String,
                     @Expose val author: String,
                     @Expose val ownerName: String,
                     @Expose val image: String,
                     @Expose val artwork: String,
                     @Expose val lastUpdateTime: Long,
                     @Expose val lastCrawlTime: Long,
                     @Expose val lastParseTime: Long,
                     @Expose val lastGoodHttpStatusTime: Long,
                     @Expose val lastHttpStatus: Int,
                     @Expose val contentType: String,
                     @Expose val itunesId: Long,
                     @Expose val generator: String,
                     @Expose val language: String,
                     @Expose val type: Int,
                     @Expose val dead: Int,
                     @Expose val crawlErrors: Int,
                     @Expose val parseErrors: Int) {

//        inner class Categories (@Expose) {
//            @Expose val
//        }


        /* Converts Feed to SearchResult */
        fun toSearchResult(): SearchResult = SearchResult(url = url, title = title, description = description)
    }


}

/* Example result:

{
    "status": "true",
    "feeds": [
    {
        "id": 75075,
        "title": "Batman University",
        "url": "https:\/\/feeds.theincomparable.com\/batmanuniversity",
        "originalUrl": "https:\/\/feeds.theincomparable.com\/batmanuniversity",
        "link": "https:\/\/www.theincomparable.com\/batmanuniversity\/",
        "description": "Batman University is a seasonal podcast about you know who...",
        "author": "Tony Sindelar",
        "ownerName": "The Incomparable",
        "image": "https:\/\/www.theincomparable.com\/imgs\/logos\/logo-batmanuniversity-3x.jpg",
        "artwork": "https:\/\/www.theincomparable.com\/imgs\/logos\/logo-batmanuniversity-3x.jpg",
        "lastUpdateTime": 1546399813,
        "lastCrawlTime": 1599328949,
        "lastParseTime": 1599012694,
        "lastGoodHttpStatusTime": 1599328949,
        "lastHttpStatus": 200,
        "contentType": "application\/x-rss+xml",
        "itunesId": 1441923632,
        "generator": null,
        "language" : "en-us",
        "type": 0,
        "dead": 0,
        "crawlErrors": 0,
        "parseErrors": 0,
        "categories": {
            "104": "TV",
            "105": "Film",
            "107": "Reviews"
        }
    }
    ],
    "count": 1,
    "query": "batman university",
    "description": "Found matching feeds."
}
 */
