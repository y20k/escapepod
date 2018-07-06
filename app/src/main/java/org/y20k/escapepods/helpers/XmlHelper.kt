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


    /* GENERAL: Skips tags that are not needed */
    @Throws(XmlPullParserException::class, IOException::class)
    fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }


    /* PODCAST: read name */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readPodcastName(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        val name = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        return name
    }

    /* PODCAST: read description */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readPodcastDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        val summary = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        return summary
    }


    /* EPISODE: read title */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readEpisodeTitle(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_TITLE)
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace,  Keys.RSS_EPISODE_TITLE)
        return title
    }


    /* EPISODE: read description */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readEpisodeDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        val summary = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        return summary
    }


    /* EPISODE: read publication date */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readEpisodePublicationDate(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        val summary = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        return summary
    }

    /* PODCAST: read remoteImageFileLocation */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readPodcastImage(parser: XmlPullParser, nameSpace: String?): String {
        var link = ""
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found episode cover
                Keys.RSS_PODCAST_COVER_URL -> link = XmlHelper.readPodcastCoverUrl(parser, nameSpace)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, nameSpace,Keys.RSS_PODCAST_COVER)
        return link
    }


    /* PODCAST: read remoteImageFileLocation URL - within remoteImageFileLocation*/
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCoverUrl(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        val link = readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        return link
    }


    /* EPISODE: read audio link */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readEpisodeAudioLink(parser: XmlPullParser, nameSpace: String?): String {
        var link = ""
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_AUDIO_LINK)
        val tag = parser.name
        val type = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_TYPE)
        if (tag == Keys.RSS_EPISODE_AUDIO_LINK) {
            if (type.contains("audio")) {
                link = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_URL)
                parser.nextTag()
            }
        }
        parser.require(XmlPullParser.END_TAG, nameSpace,Keys.RSS_EPISODE_AUDIO_LINK)
        return link
    }
    
    
    /* Reads text */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

}