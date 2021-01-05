/*
 * RssHelper.kt
 * Implements the RssHelper class
 * A RssHelper reads and parses podcast RSS feeds
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.xml

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepod.Keys
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
    private var podcast: RssPodcast = RssPodcast()


    /* Suspend function: Read RSS feed from given input stream - async using coroutine */
    suspend fun readSuspended(context: Context, localFileUri: Uri, remotePodcastFeedLocation: String): RssPodcast {
        return suspendCoroutine {cont ->
            LogHelper.v(TAG, "Reading RSS feed ($remotePodcastFeedLocation) - Thread: ${Thread.currentThread().name}")
            // create podcast object and store remote feed location
            podcast = RssPodcast(remotePodcastFeedLocation = remotePodcastFeedLocation)
            // try parsing
            val stream: InputStream? = FileHelper.getTextFileStream(context, localFileUri)
            try {
                // create XmlPullParser for InputStream
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                parser.nextTag()
                // start reading rss feed
                parseFeed(parser)
            } catch (e : Exception) {
                // e.printStackTrace()
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
    private fun parseFeed(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_RSS)

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found a podcast
                Keys.RSS_PODCAST -> readPodcast(parser)
                // skip any other un-needed tag within document
                else -> XmlHelper.skip(parser)
            }
        }
    }


    /* Reads podcast element - within feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPodcast(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_PODCAST)

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found a podcast name
                Keys.RSS_PODCAST_NAME -> podcast.name = readPodcastName(parser, Keys.XML_NAME_SPACE)
                // found a podcast description
                Keys.RSS_PODCAST_DESCRIPTION -> podcast.description = readPodcastDescription(parser, Keys.XML_NAME_SPACE)
                // found a podcast website
                Keys.RSS_PODCAST_WEBSITE -> podcast.website = readPodcastWebsite(parser, Keys.XML_NAME_SPACE)
                // found a podcast remoteImageFileLocation
                Keys.RSS_PODCAST_COVER_ITUNES -> podcast.remoteImageFileLocation = readPodcastCoverItunes(parser, Keys.XML_NAME_SPACE)
                Keys.RSS_PODCAST_COVER -> podcast.remoteImageFileLocation = readPodcastCover(parser, Keys.XML_NAME_SPACE)
                // found an episode
                Keys.RSS_EPISODE -> {
                    val episode: RssEpisode = readEpisode(parser)
                    if (episode.title.isNotEmpty() && episode.remoteAudioFileLocation.isNotEmpty() && episode.publicationDate != Keys.DEFAULT_DATE) {
                        episode.podcastName = podcast.name
                        podcast.episodes.add(episode)
                        if (episode.publicationDate.after(podcast.latestEpisodeDate)) {
                            podcast.latestEpisodeDate = episode.publicationDate
                        }
                    }
                }
                // skip any other un-needed tag within "channel" ( = podcast)
                else -> XmlHelper.skip(parser)
            }
        }
    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser): RssEpisode {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_EPISODE)

        // initialize episode
        val episode: RssEpisode = RssEpisode(episodeRemotePodcastFeedLocation = podcast.remotePodcastFeedLocation)

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found episode title
                Keys.RSS_EPISODE_GUID -> episode.guid = readEpisodeGuid(parser, Keys.XML_NAME_SPACE)
                // found episode title
                Keys.RSS_EPISODE_TITLE -> episode.title = readEpisodeTitle(parser, Keys.XML_NAME_SPACE)
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION -> episode.description = getLongerString(episode.description, readEpisodeDescription(parser, Keys.XML_NAME_SPACE))
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION_ITUNES -> episode.description = getLongerString(episode.description, readEpisodeDescriptionItunes(parser, Keys.XML_NAME_SPACE))
                // found episode publication date
                Keys.RSS_EPISODE_PUBLICATION_DATE -> episode.publicationDate = readEpisodePublicationDate(parser, Keys.XML_NAME_SPACE)
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> episode.remoteAudioFileLocation = readEpisodeAudioLink(parser, Keys.XML_NAME_SPACE)
                // skip any other un-needed tag within "item" ( = episode)
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


    /* PODCAST: readSuspended website */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastWebsite(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_WEBSITE)
        val website = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_WEBSITE)
        return website
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


    /* EPISODE: readSuspended description / summary - iTunes variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeDescriptionItunes(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION_ITUNES)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION_ITUNES)
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
            // read only relevant tags
            when (parser.name) {
                // found episode cover
                Keys.RSS_PODCAST_COVER_URL -> link = readPodcastCoverUrl(parser, nameSpace)
                // skip any other un-needed tag within "image" ( = Cover)
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


    /* Compares the length of two stings and returns the longer string */
    private fun getLongerString(currentString: String, newString: String): String {
        if (currentString.length >= newString.length) {
            return currentString
        } else {
            return newString
        }
    }


    /*
     * Inner class to collect podcast data
     */
    inner class RssPodcast(
            var name: String = String(),
            var description: String = String(),
            var website: String = String(),
            var cover: String = Keys.LOCATION_DEFAULT_COVER,
            var smallCover: String = Keys.LOCATION_DEFAULT_COVER,
            var latestEpisodeDate: Date = Keys.DEFAULT_DATE,
            var remoteImageFileLocation: String = String(),
            var remotePodcastFeedLocation: String = String(),
            var episodes: MutableList<RssEpisode> = mutableListOf())
    /*
     * End of inner class
     */


    /*
     * Inner class to collect episode data
     */
    inner class RssEpisode(
            var guid: String = String(),
            var title: String = String(),
            var description: String = String(),
            var audio: String = String(),
            var cover: String = Keys.LOCATION_DEFAULT_COVER,
            var smallCover: String = Keys.LOCATION_DEFAULT_COVER,
            var publicationDate: Date = Keys.DEFAULT_DATE,
            var playbackState: Int = PlaybackStateCompat.STATE_STOPPED,
            var playbackPosition: Long = 0L,
            var duration: Long = 0L,
            var manuallyDownloaded: Boolean = false,
            var manuallyDeleted: Boolean = false,
            var podcastName: String = String(),
            var remoteCoverFileLocation: String = String(),
            var remoteAudioFileLocation: String = String(),
            var episodeRemotePodcastFeedLocation: String = String())
    /*
     * End of inner class
     */

}