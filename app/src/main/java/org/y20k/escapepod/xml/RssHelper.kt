/*
 * RssHelper.kt
 * Implements the RssHelper class
 * A RssHelper reads and parses podcast RSS feeds
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.xml

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepod.Keys
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.core.Podcast
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.helpers.LogHelper
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * RssHelper class
 */
class RssHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(RssHelper::class.java.name)


    /* Main class variables */
    private var podcast: Podcast = Podcast()


    /* Suspend function: Read RSS feed from given input stream - async using coroutine */
    suspend fun readSuspended(context: Context, localFileUri: Uri, remotePodcastFeedLocation: String): Podcast {
        return suspendCoroutine {cont ->
            LogHelper.v(TAG, "Reading RSS feed ($remotePodcastFeedLocation) - Thread: ${Thread.currentThread().name}")
            // store remote feed location
            podcast.remotePodcastFeedLocation = remotePodcastFeedLocation
            // try parsing
            val stream: InputStream? = FileHelper.getTextFileStream(context, localFileUri)
            try {
                // create XmlPullParser for InputStream
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                parser.nextTag()
                // start reading rss feed
                podcast = parseFeed(parser)
            } catch (exception : Exception) {
                exception.printStackTrace()
            } finally {
                stream?.close()
            }

            // sort episodes - newest episode first
            podcast.episodes.sortByDescending { it.publicationDate }

            // return parsing result
            cont.resume(podcast)
        }
    }


    /* Parses whole RSS feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseFeed(parser: XmlPullParser): Podcast {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_RSS)

        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found a podcast
                Keys.RSS_PODCAST -> readPodcast(parser)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return podcast
    }


    /* Reads podcast element - within feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPodcast(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_PODCAST)

        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found a podcast name
                Keys.RSS_PODCAST_NAME -> podcast.name = readPodcastName(parser, Keys.XML_NAME_SPACE)
                // found a podcast description
                Keys.RSS_PODCAST_DESCRIPTION -> podcast.description = readPodcastDescription(parser, Keys.XML_NAME_SPACE)
                // found a podcast remoteImageFileLocation
                Keys.RSS_PODCAST_COVER_ITUNES -> podcast.remoteImageFileLocation = readPodcastCoverItunes(parser, Keys.XML_NAME_SPACE)
                Keys.RSS_PODCAST_COVER -> podcast.remoteImageFileLocation = readPodcastCover(parser, Keys.XML_NAME_SPACE)
                // found an episode
                Keys.RSS_EPISODE -> {
                    val episode: Episode = readEpisode(parser, podcast)
                    if (episode.isValid()) { podcast.episodes.add(episode) }
                }
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }

    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser, podcast: Podcast): Episode {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_EPISODE)

        // initialize episode
        val episode: Episode = Episode()
        episode.podcastName = podcast.name
        episode.podcastFeedLocation = podcast.remotePodcastFeedLocation
        episode.cover = podcast.cover

        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found episode title
                Keys.RSS_EPISODE_GUID -> episode.guid = readEpisodeGuid(parser, Keys.XML_NAME_SPACE)
                // found episode title
                Keys.RSS_EPISODE_TITLE -> episode.title = readEpisodeTitle(parser, Keys.XML_NAME_SPACE)
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION -> episode.description = readEpisodeDescription(parser, Keys.XML_NAME_SPACE)
                // found episode publication date
                Keys.RSS_EPISODE_PUBLICATION_DATE -> episode.publicationDate = readEpisodePublicationDate(parser, Keys.XML_NAME_SPACE)
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> episode.remoteAudioFileLocation = readEpisodeAudioLink(parser, Keys.XML_NAME_SPACE)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return episode
    }


    /* PODCAST: readSuspended name */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastName(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        val name = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        return name
    }


    /* PODCAST: readSuspended description */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        return summary
    }


    /* EPISODE: readSuspended GUID */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeGuid(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_GUID)
        val title = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_GUID)
        return title
    }


    /* EPISODE: readSuspended title */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeTitle(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_TITLE)
        val title = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_TITLE)
        return title
    }


    /* EPISODE: readSuspended description */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        return summary
    }


    /* EPISODE: readSuspended publication date */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodePublicationDate(parser: XmlPullParser, nameSpace: String?): Date {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        val publicationDate = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        return DateTimeHelper.convertFromRfc2822(publicationDate)
    }


    /* PODCAST: readSuspended remoteImageFileLocation - standard tag variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCover(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found episode cover
                Keys.RSS_PODCAST_COVER_URL -> link = readPodcastCoverUrl(parser, nameSpace)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER)
        return link
    }


    /* PODCAST: readSuspended remoteImageFileLocation URL - within remoteImageFileLocation*/
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCoverUrl(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        val link = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        return link
    }


    /* PODCAST: readSuspended remoteImageFileLocation - itunes tag variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCoverItunes(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER_ITUNES)
        val tag = parser.name
        if (tag == Keys.RSS_PODCAST_COVER_ITUNES) {
            link = parser.getAttributeValue(null, Keys.RSS_PODCAST_COVER_ITUNES_URL)
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER_ITUNES)
        return link
    }


    /* EPISODE: readSuspended audio link */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeAudioLink(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_AUDIO_LINK)
        val tag = parser.name
        val type = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_TYPE)
        if (tag == Keys.RSS_EPISODE_AUDIO_LINK) {
            val value: String = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_URL)
            val fileExtension: String = value.substring(value.lastIndexOf(".") + 1)
            if (Keys.MIME_TYPES_AUDIO.contains(type) || Keys.FILE_EXTENSIONS_AUDIO.contains(fileExtension)) {
                link = value
            }
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_AUDIO_LINK)
        return link
    }

}