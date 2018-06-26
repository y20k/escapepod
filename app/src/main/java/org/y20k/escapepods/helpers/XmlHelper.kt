/*
 * XmlHelper.kt
 * Implements the XmlHelper class
 * A XmlHelper provides helper methods for reading and parsing podcast rss feeds
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


/*
 * XmlHelper class
 */
object XmlHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlHelper::class.java.name)


    /* PODCAST: read title */
    @Throws(XmlPullParserException::class, IOException::class)
    fun readPodcastTitle(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, "title")
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, "title")
        LogHelper.e(TAG, "Podcast title: $title") // todo remove
        return title
    }


    /* EPISODE: read title */
    @Throws(XmlPullParserException::class, IOException::class)
    fun readEpisodeTitle(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, "title")
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, "title")
        LogHelper.e(TAG, "Episode title: $title") // todo remove
        return title
    }


    /* Helper method that reads a text */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

}