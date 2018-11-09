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

    // application name
    const val APPLICATION_NAME: String = "Escapepods"

    // version numbers
    const val CURRENT_COLLECTION_CLASS_VERSION_NUMBER: Int = 0

    // notification
    const val NOTIFICATION_NOW_PLAYING_ID: Int = 42
    const val NOTIFICATION_NOW_PLAYING_CHANNEL = "notificationChannelIdPlaybackChannel"

    // intent actions
    const val ACTION_PLAY = "org.y20k.escapepods.action.PLAY"
    const val ACTION_PAUSE = "org.y20k.escapepods.action.PAUSE"
    const val ACTION_FORWARD = "org.y20k.escapepods.action.FORWARD"
    const val ACTION_REPLAY = "org.y20k.escapepods.action.REPLAY"
    const val ACTION_DISMISS = "org.y20k.escapepods.action.DISMISS"
    const val ACTION_PLAYBACK_STATE_CHANGED = "org.y20k.escapepods.action.PLAYBACK_STATE_CHANGED"
    const val ACTION_METADATA_CHANGED = "org.y20k.escapepods.action.METADATA_CHANGED"
    const val ACTION_SHOW_PLAYER = "org.y20k.escapepods.action.HOW_PLAYER"
    const val ACTION_TIMER_RUNNING = "org.y20k.escapepods.action.TIMER_RUNNING"
    const val ACTION_TIMER_START = "org.y20k.escapepods.action.TIMER_START"
    const val ACTION_TIMER_STOP = "org.y20k.escapepods.action.TIMER_STOP"
    const val ACTION_COLLECTION_CHANGED: String = "org.y20k.escapepods.action.COLLECTION_CHANGED"
    const val ACTION_UPDATE_COLLECTION: String = "org.y20k.escapepods.action.UPDATE_COLLECTION"
    const val ACTION_DOWNLOAD_PROGRESS_UPDATE: String  = "org.y20k.escapepods.action.DOWNLOAD_PROGRESS_UPDATE"

    // intent extras
    const val EXTRA_COLLECTION: String = "COLLECTION"
    const val EXTRA_PODCAST: String = "PODCAST"
    const val EXTRA_EPISODE: String = "EPISODE"
    const val EXTRA_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val EXTRA_DOWNLOAD_ID: String  = "DOWNLOAD_ID"
    const val EXTRA_DOWNLOAD_PROGRESS: String  = "DOWNLOAD_PROGRESS"

    // preferences
    const val PREF_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val PREF_ACTIVE_DOWNLOADS: String = "ACTIVE_DOWNLOADS"
    const val PREF_DOWNLOAD_OVER_MOBILE: String = "DOWNLOAD_OVER_MOBILE"
    const val PREF_NUMBER_OF_EPISODES_TO_KEEP: String = "NUMBER_OF_EPISODES_TO_KEEP"
    const val PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP: String = "NUMBER_OF_AUDIO_FILES_TO_KEEP"
    const val PREF_NIGHT_MODE_STATE: String = "NIGHT_MODE_STATE"

    // default const values
    const val DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP: Int = 2
    const val DEFAULT_NUMBER_OF_EPISODES_TO_KEEP: Int = 5
    const val DEFAULT_DOWNLOAD_OVER_MOBILE: Boolean = false

    // media browser
    const val MEDIA_ID_ROOT = "__ROOT__"
    const val MEDIA_ID_EMPTY_ROOT = "__EMPTY__"

    // view types
    const val VIEW_TYPE_ADD_NEW: Int = 1
    const val VIEW_TYPE_PODCAST: Int = 2

    // view holder update types
    const val HOLDER_UPDATE_COVER: Int = 0
    const val HOLDER_UPDATE_NAME: Int = 1
    const val HOLDER_UPDATE_PLAYBACK_STATE: Int = 2
    const val HOLDER_UPDATE_DOWNLOAD_STATE: Int = 3

    // dialog types
    const val DIALOG_UPDATE_WITHOUT_WIFI: Int = 1
    const val DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI: Int = 2
    const val DIALOG_REMOVE_PODCAST: Int = 3

    // dialog results
    const val DIALOG_RESULT_DEFAULT: Int = -1

    // file types
    const val FILE_TYPE_RSS: Int = 1
    const val FILE_TYPE_AUDIO: Int  = 2
    const val FILE_TYPE_IMAGE: Int  = 3

    // options
    const val OPTION_REMOVE_FOLDER: Int = -1

    // mime types
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_MP3 = "audio/mpeg3"
    const val MIME_TYPE_XML = "text/xml"
    const val MIME_TYPE_UNSUPPORTED = "unsupported"

    // media genre
    const val MEDIA_GENRE = "Podcast"

    // folder names
    const val FOLDER_COLLECTION: String = "collection"
    const val FOLDER_AUDIO: String = "audio"
    const val FOLDER_IMAGES: String  = "images"
    const val FOLDER_TEMP: String  = "temp"
    const val NO_SUB_DIRECTORY: String = ""

    // file names
    const val COLLECTION_FILE: String = "collection.json"
    const val COLLECTION_OPML_FILE: String = "collection_opml.xml"

    // locations
    const val LOCATION_DEFAULT_COVER: String = "android.resource://org.y20k.escapepods/drawable/ic_default_cover_rss_icon_24dp"

    // sizes (in dp)
    const val SIZE_COVER_SMALL: Int = 96

    // keys
    const val KEY_DOWNLOAD_WORK_REQUEST: String = "DOWNLOAD_WORK_REQUEST"
    const val KEY_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val KEY_NEW_PODCAST_URL: String = "NEW_PODCAST_URL"
    const val KEY_RESULT_NEW_COLLECTION = "RESULT_NEW_COLLECTION"

    // requests
    const val REQUEST_UPDATE_COLLECTION: Int = 1
    const val REQUEST_ADD_PODCAST: Int = 2

    // unique names
    const val NAME_PERIODIC_COLLECTION_UPDATE_WORK: String = "PERIODIC_COLLECTION_UPDATE_WORK"
    const val NAME_ONE_TIME_COLLECTION_UPDATE_WORK: String = "ONE_TIME_COLLECTION_UPDATE_WORK"

    // const values
    const val ONE_SECOND_IN_MILLISECONDS: Long = 1000L
    const val FIVE_MINUTES_IN_MILLISECONDS: Long = 300000L
    const val ONE_MINUTE_IN_MILLISECONDS: Long = 60000L

    // rss tags
    val XML_NAME_SPACE: String? = null
    const val RSS_RSS = "rss"
    const val RSS_PODCAST = "channel"
    const val RSS_PODCAST_NAME = "title"
    const val RSS_PODCAST_DESCRIPTION = "description"
    const val RSS_PODCAST_COVER = "image"
    const val RSS_PODCAST_COVER_URL = "url"
    const val RSS_PODCAST_COVER_ITUNES = "itunes:image"
    const val RSS_PODCAST_COVER_ITUNES_URL = "href"
    const val RSS_EPISODE = "item"
    const val RSS_EPISODE_GUID = "guid"
    const val RSS_EPISODE_TITLE = "title"
    const val RSS_EPISODE_DESCRIPTION = "description"
    const val RSS_EPISODE_PUBLICATION_DATE = "pubDate"
    const val RSS_EPISODE_AUDIO_LINK = "enclosure"
    const val RSS_EPISODE_AUDIO_LINK_TYPE = "type"
    const val RSS_EPISODE_AUDIO_LINK_URL = "url"
    const val OPML_OPML = "opml"
    const val OPML_BODY = "body"
    const val OPML_OUTLINE = "outline"
    const val OPML_OUTLINE_PODCAST = "outline"
    const val OPML_OUTLINE_PODCAST_TYPE = "type"
    const val OPML_OUTLINE_PODCAST_TYPE_RSS = "rss"
    const val OPML_OUTLINE_PODCAST_URL = "xmlUrl"

    // custom MediaMetadata keys for episodes
    const val METADATA_CUSTOM_KEY_DESCRIPTION = "CUSTOM_KEY_DESCRIPTION"
    const val METADATA_CUSTOM_KEY_PUBLICATION_DATE = "CUSTOM_KEY_PUBLICATION_DATE"
    const val METADATA_CUSTOM_KEY_AUDIO_LINK_URL = "CUSTOM_KEY_PUBLICATION_DATE_URL"
    const val METADATA_CUSTOM_KEY_IMAGE_LINK_URL = "CUSTOM_KEY_IMAGE_LINK_URL"
    const val METADATA_CUSTOM_KEY_PLAYBACK_PROGRESS = "CUSTOM_KEY_PLAYBACK_PROGRESS"
}
