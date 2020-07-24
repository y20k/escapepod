/*
 * Keys.kt
 * Implements the keys used throughout the app
 * This object hosts all keys used to control Escapepod state
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import java.util.*


/*
 * Keys object
 */
object Keys {

    // application name
    const val APPLICATION_NAME: String = "Escapepod"

    // version numbers
    const val CURRENT_COLLECTION_CLASS_VERSION_NUMBER: Int = 0

    // time values
    const val UPDATE_REPEAT_INTERVAL: Long = 4L             // every 4 hours
    const val MINIMUM_TIME_BETWEEN_UPDATES: Long = 180000L  // 3 minutes in milliseconds
    const val SKIP_BACK_TIME_SPAN: Long = 10000L            // 10 seconds in milliseconds
    const val SKIP_FORWARD_TIME_SPAN: Long = 30000L         // 30 seconds in milliseconds
    const val SLEEP_TIMER_DURATION: Long = 900000L          // 15 minutes in  milliseconds
    const val SLEEP_TIMER_INTERVAL: Long = 1000L            // 1 second in milliseconds

    // ranges
    val PLAYBACK_SPEEDS = arrayOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

    // notification
    const val NOTIFICATION_NOW_PLAYING_ID: Int = 42
    const val NOTIFICATION_NOW_PLAYING_CHANNEL: String = "notificationChannelIdPlaybackChannel"

    // intent actions
    const val ACTION_SHOW_PLAYER: String = "org.y20k.escapepod.action.SHOW_PLAYER"
    const val ACTION_COLLECTION_CHANGED: String = "org.y20k.escapepod.action.COLLECTION_CHANGED"
    const val ACTION_STOP: String = "org.y20k.escapepod.action.STOP"

    // intent extras
    const val EXTRA_COLLECTION_MODIFICATION_DATE: String = "COLLECTION_MODIFICATION_DATE"

    // keys
    const val KEY_DOWNLOAD_WORK_REQUEST: String = "DOWNLOAD_WORK_REQUEST"
    const val KEY_SAVE_INSTANCE_STATE_PODCAST_LIST: String = "SAVE_INSTANCE_STATE_PODCAST_LIST"

    // custom MediaController commands
    const val CMD_RELOAD_PLAYER_STATE: String = "RELOAD_PLAYER_STATE"
    const val CMD_REQUEST_PERIODIC_PROGRESS_UPDATE: String = "REQUEST_PERIODIC_PROGRESS_UPDATE"
    const val CMD_START_SLEEP_TIMER: String = "START_SLEEP_TIMER"
    const val CMD_CANCEL_SLEEP_TIMER: String = "CANCEL_SLEEP_TIMER"
    const val CMD_CHANGE_PLAYBACK_SPEED: String = "CHANGE_PLAYBACK_SPEED"
    const val CMD_RESET_PLAYBACK_SPEED: String = "RESET_PLAYBACK_SPEED"

    // preferences
    const val PREF_ONE_TIME_HOUSEKEEPING_NECESSARY = "ONE_TIME_HOUSEKEEPING_NECESSARY_VERSIONCODE_26" // increment to current app version code to trigger housekeeping that runs only once
    const val PREF_THEME_SELECTION: String= "THEME_SELECTION"
    const val PREF_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val PREF_COLLECTION_SIZE: String = "COLLECTION_SIZE"
    const val PREF_COLLECTION_MODIFICATION_DATE: String = "COLLECTION_MODIFICATION_DATE"
    const val PREF_CURRENT_MEDIA_ID: String = "CURRENT_MEDIA_ID"
    const val PREF_CURRENT_PLAYBACK_STATE: String = "CURRENT_PLAYBACK_STATE"
    const val PREF_UP_NEXT_MEDIA_ID: String = "UP_NEXT_MEDIA_ID"
    const val PREF_ACTIVE_DOWNLOADS: String = "ACTIVE_DOWNLOADS"
    const val PREF_BACKGROUND_DOWNLOAD: String = "BACKGROUND_DOWNLOAD"
    const val PREF_BACKGROUND_REFRESH: String = "BACKGROUND_REFRESH"
    const val PREF_EPISODE_DOWNLOAD_OVER_MOBILE: String = "EPISODE_DOWNLOAD_OVER_MOBILE"
    const val PREF_NUMBER_OF_EPISODES_TO_KEEP: String = "NUMBER_OF_EPISODES_TO_KEEP"
    const val PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP: String = "NUMBER_OF_AUDIO_FILES_TO_KEEP"
    const val PREF_KEEP_DEBUG_LOG: String = "KEEP_DEBUG_LOG"
    const val PREF_PLAYER_STATE_EPISODE_MEDIA_ID: String = "PLAYER_STATE_EPISODE_MEDIA_ID"
    const val PREF_PLAYER_STATE_PLAYBACK_STATE: String = "PLAYER_STATE_PLAYBACK_STATE"
    const val PREF_PLAYER_STATE_PLAYBACK_POSITION: String = "PLAYER_STATE_PLAYBACK_POSITION"
    const val PREF_PLAYER_STATE_EPISODE_DURATION: String = "PLAYER_STATE_EPISODE_DURATION"
    const val PREF_PLAYER_STATE_PLAYBACK_SPEED: String = "PLAYER_STATE_PLAYBACK_SPEED"
    const val PREF_PLAYER_STATE_BOTTOM_SHEET_STATE: String = "PLAYER_STATE_BOTTOM_SHEET_STATE"
    const val PREF_PLAYER_STATE_SLEEP_TIMER_STATE: String = "PLAYER_STATE_SLEEP_TIMER_STATE"

    // states
    const val STATE_SLEEP_TIMER_STOPPED: Int = 0

    // default const values
    const val DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP: Int = 2
    const val DEFAULT_NUMBER_OF_EPISODES_TO_KEEP: Int = 5
    const val ACTIVE_DOWNLOADS_EMPTY: String = "zero"
    const val DEFAULT_BACKGROUND_REFRESH_MODE: Boolean = true
    const val DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE: Boolean = false

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
    const val HOLDER_UPDATE_PLAYBACK_PROGRESS: Int = 4

    // dialog types
    const val DIALOG_UPDATE_COLLECTION: Int = 1
    const val DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI: Int = 2
    const val DIALOG_REMOVE_PODCAST: Int = 3
    const val DIALOG_DELETE_EPISODE: Int = 4
    const val DIALOG_ADD_UP_NEXT: Int = 5
    const val DIALOG_DELETE_DOWNLOADS: Int = 6
    const val DIALOG_UPDATE_COVERS: Int = 7
    const val DIALOG_MARK_EPISODE_PLAYED: Int = 8

    // dialog results
    const val DIALOG_RESULT_DEFAULT: Int = -1
    const val DIALOG_EMPTY_PAYLOAD_STRING: String = ""
    const val DIALOG_EMPTY_PAYLOAD_INT: Int = -1

    // file types
    const val FILE_TYPE_DEFAULT: Int = 0
    const val FILE_TYPE_RSS: Int = 1
    const val FILE_TYPE_AUDIO: Int  = 2
    const val FILE_TYPE_IMAGE: Int  = 3

    // mime types and charsets and file extensions
    const val CHARSET_UNDEFINDED = "undefined"
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_MP3 = "audio/mpeg"
    const val MIME_TYPE_XML = "text/xml"
    const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
    const val MIME_TYPE_UNSUPPORTED = "unsupported"
    val MIME_TYPES_IMAGE = arrayOf("image/png", "image/jpeg")
    val MIME_TYPES_AUDIO = arrayOf("audio/mpeg", "audio/mpeg3", "audio/mp3")
    val MIME_TYPES_RSS = arrayOf("text/xml", "application/rss+xml", "application/xml")
    val MIME_TYPES_ATOM = arrayOf("application/atom+xml")
    val FILE_EXTENSIONS_AUDIO = arrayOf("mp3")
    val FILE_EXTENSIONS_IMAGE = arrayOf("png", "jpeg", "jpg")

    // folder names
    const val FOLDER_COLLECTION: String = "collection"
    const val FOLDER_AUDIO: String = "audio"
    const val FOLDER_IMAGES: String  = "images"
    const val FOLDER_TEMP: String  = "temp"

    // file names
    const val COLLECTION_FILE: String = "collection.json"
    const val COLLECTION_OPML_FILE: String = "collection_opml.xml"
    const val PODCAST_COVER_FILE: String = "cover.jpg"
    const val PODCAST_SMALL_COVER_FILE: String = "cover-small.jpg"
    const val DEBUG_LOG_FILE: String = "log-can-be-deleted.txt"

    // locations
    const val LOCATION_DEFAULT_COVER: String = "android.resource://org.y20k.escapepod/drawable/ic_default_cover_rss_icon_24dp"

    // sizes (in dp)
    const val SIZE_COVER_MAXIMUM: Int = 640 // used to create cover.jpg
    const val SIZE_COVER_PODCAST_CARD: Int = 96 // used to create cover-small.jpg
    const val SIZE_COVER_NOTIFICATION_LARGE_ICON: Int = 256
    const val SIZE_COVER_LOCK_SCREEN: Int = 320
    const val BOTTOM_SHEET_PEEK_HEIGHT: Int = 72

    // default values
    val DEFAULT_DATE: Date = Date(0L)
    const val DEFAULT_RFC2822_DATE: String = "Thu, 01 Jan 1970 01:00:00 +0100" // --> Date(0)
    const val EMPTY_STRING_RESOURCE: Int = 0

    // requests
    const val REQUEST_UPDATE_COLLECTION: Int = 1
    const val REQUEST_SAVE_OPML: Int = 2
    const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: Int = 23

    // results
    const val RESULT_DATA_SLEEP_TIMER_REMAINING: String = "DATA_SLEEP_TIMER_REMAINING"
    const val RESULT_CODE_PERIODIC_PROGRESS_UPDATE: Int = 1
    const val RESULT_DATA_PLAYBACK_PROGRESS: String = "DATA_PLAYBACK_PROGRESS"
    const val RESULT_CODE_PLAYBACK_SPEED: Int = 2
    const val RESULT_DATA_PLAYBACK_SPEED: String = "DATA_PLAYBACK_SPEED"

    // podcast validation states
    const val PODCAST_VALIDATION_SUCESS: Int = 0
    const val PODCAST_VALIDATION_MISSING_COVER: Int = 1
    const val PODCAST_VALIDATION_NO_VALID_EPISODES: Int = 2

    // podcast states
    const val PODCAST_STATE_NEW_PODCAST: Int = 0
    const val PODCAST_STATE_HAS_NEW_EPISODES: Int = 1
    const val PODCAST_STATE_PODCAST_UNCHANGED: Int = 2

    // theme states
    const val STATE_THEME_FOLLOW_SYSTEM: String = "stateFollowSystem"
    const val STATE_THEME_LIGHT_MODE: String = "stateLightMode"
    const val STATE_THEME_DARK_MODE: String = "stateDarkMode"

    // background download options
    const val BACKGROUND_DOWNLOAD_DEFAULT: String = "backgroundDownloadDefault"
    const val BACKGROUND_DOWNLOAD_MANUAL: String = "backgroundDownloadManual"
    const val BACKGROUND_DOWNLOAD_UNRESTRICTED: String = "backgroundDownloadUnrestricted"

    // unique names
    const val NAME_PERIODIC_COLLECTION_UPDATE_WORK: String = "PERIODIC_COLLECTION_UPDATE_WORK"

    // rss tags
    val XML_NAME_SPACE: String? = null
    const val RSS_RSS = "rss"
    const val RSS_PODCAST = "channel"
    const val RSS_PODCAST_NAME = "title"
    const val RSS_PODCAST_DESCRIPTION = "description"
    const val RSS_PODCAST_WEBSITE = "link"
    const val RSS_PODCAST_COVER = "image"
    const val RSS_PODCAST_COVER_URL = "url"
    const val RSS_PODCAST_COVER_ITUNES = "itunes:image"
    const val RSS_PODCAST_COVER_ITUNES_URL = "href"
    const val RSS_EPISODE = "item"
    const val RSS_EPISODE_GUID = "guid"
    const val RSS_EPISODE_TITLE = "title"
    const val RSS_EPISODE_DESCRIPTION = "description"
    const val RSS_EPISODE_DESCRIPTION_ITUNES= "itunes:summary"
    const val RSS_EPISODE_PUBLICATION_DATE = "pubDate"
    const val RSS_EPISODE_AUDIO_LINK = "enclosure"
    const val RSS_EPISODE_AUDIO_LINK_TYPE = "type"
    const val RSS_EPISODE_AUDIO_LINK_URL = "url"
    const val OPML_OPML = "opml"
    const val OPML_BODY = "body"
    const val OPML_OUTLINE = "outline"
    const val OPML_OUTLINE_TEXT = "text"
    const val OPML_OUTLINE_TEXT_FEEDS = "feeds"
    const val OPML_OUTLINE_TYPE = "type"
    const val OPML_OUTLINE_TYPE_RSS = "rss"
    const val OPML_OUTLINE_XML_URL = "xmlUrl"

}
