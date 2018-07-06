/*
 * Keys.kt
 * Implements the keys used throughout the app
 * This object hosts all keys used to control Escapepods state
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers


/*
 * Keys object
 */
object Keys {

    // version numbers
    val CURRENT_COLLECTION_CLASS_VERSION_NUMBER: Int = 0

    // preferences
    val PREF_DOWNLOAD_OVER_MOBILE: String = "DOWNLOAD_OVER_MOBILE"
    val PREF_NIGHT_MODE_STATE: String = "NIGHT_MODE_STATE"

    // file types
    val FILE_TYPE_RSS: Int = 1
    val FILE_TYPE_AUDIO: Int  = 2
    val FILE_TYPE_IMAGE: Int  = 3

    // mime types
    val MIME_TYPE_JPG = "cover/jpeg"
    val MIME_TYPE_PNG = "cover/png"
    val MIME_TYPE_MP3 = "audio/mpeg3"
    val MIME_TYPE_XML = "text/xml"
    val MIME_TYPE_UNSUPPORTED = "unsupported"

    // folder names
    val FOLDER_COLLECTION: String = "collection"
    val FOLDER_AUDIO: String = "audio"
    val FOLDER_IMAGES: String  = "images"
    val FOLDER_TEMP: String  = "temp"
    val NO_SUB_DIRECTORY: String = ""

    // file names
    val COLLECTION_FILE: String = "collection.json"

    // intent actions and extras
    val ACTION_DOWNLOAD_PROGRESS_UPDATE: String  = "DOWNLOAD_PROGRESS_UPDATE"
    val EXTRA_DOWNLOAD_ID: String  = "DOWNLOAD_ID"
    val EXTRA_DOWNLOAD_PROGRESS: String  = "DOWNLOAD_PROGRESS"

    // values
    val ONE_SECOND_IN_MILLISECONDS: Long = 1000L
    val FIVE_MINUTES_IN_MILLISECONDS: Long = 300000L

    // rss tags
    val RSS_RSS = "rss"
    val RSS_PODCAST = "channel"
    val RSS_PODCAST_NAME = "title"
    val RSS_PODCAST_DESCRIPTION = "description"
    val RSS_PODCAST_COVER = "image"
    val RSS_PODCAST_COVER_URL = "url"
    val RSS_EPISODE = "item"
    val RSS_EPISODE_TITLE = "title"
    val RSS_EPISODE_DESCRIPTION = "description"
    val RSS_EPISODE_PUBLICATION_DATE = "pubDate"
    val RSS_EPISODE_AUDIO_LINK = "enclosure"
    val RSS_EPISODE_AUDIO_LINK_TYPE = "type"
    val RSS_EPISODE_AUDIO_LINK_URL = "url"

    // custom MediaMetadata keys for episodes
    val METADATA_CUSTOM_KEY_DESCRIPTION = "CUSTOM_KEY_DESCRIPTION"
    val METADATA_CUSTOM_KEY_PUBLICATION_DATE = "CUSTOM_KEY_PUBLICATION_DATE"
    val METADATA_CUSTOM_KEY_AUDIO_LINK_URL = "CUSTOM_KEY_PUBLICATION_DATE_URL"
    val METADATA_CUSTOM_KEY_IMAGE_LINK_URL = "CUSTOM_KEY_IMAGE_LINK_URL"
    val METADATA_CUSTOM_KEY_PLAYBACK_PROGRESS = "CUSTOM_KEY_PLAYBACK_PROGRESS"
}
