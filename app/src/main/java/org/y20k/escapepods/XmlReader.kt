/*
 * XmlReader.kt
 * Implements the XmlReader class
 * A XmlReader reads and parses podcast RSS feeds
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.XmlHelper
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.experimental.suspendCoroutine


/*
 * XmlReader class
 */
class XmlReader() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)


    /* Main class variables */
    private val nameSpace: String? = null
    private var podcast: Podcast = Podcast()


    /* Read feed from given input stream - async using coroutine */
    suspend fun read(context: Context, localFileUri: Uri, remotePodcastFeedLocation: String): Podcast {
        return suspendCoroutine {cont ->
            // store remote feed location
            podcast.remotePodcastFeedLocation = remotePodcastFeedLocation

            // try parsing
            val stream: InputStream = FileHelper().getTextFileStream(context, localFileUri)
            try {
                // create XmlPullParser for InputStream
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(stream, null)
                parser.nextTag();
                // start reading rss feed
                podcast = parseFeed(parser)
            } catch (exception : Exception) {
                exception.printStackTrace()
            } finally {
                stream.close()
            }

            // sort episodes - newest episode first
            podcast.episodes.sortByDescending { it.publicationDate }

            // return parsing result
            cont.resume(podcast)
        }
    }


    /* Parses whole feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseFeed(parser: XmlPullParser): Podcast {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_RSS)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
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
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found a podcast name
                Keys.RSS_PODCAST_NAME -> podcast.name = XmlHelper.readPodcastName(parser, nameSpace)
                // found a podcast description
                Keys.RSS_PODCAST_DESCRIPTION -> podcast.description = XmlHelper.readPodcastDescription(parser,nameSpace)
                // found a podcast remoteImageFileLocation
                Keys.RSS_PODCAST_COVER -> podcast.remoteImageFileLocation = XmlHelper.readPodcastImage(parser, nameSpace)
                // found an episode
                Keys.RSS_EPISODE -> {
                    val episode: Episode = readEpisode(parser)
                    podcast.episodes.add(episode)
                }
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }

    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser): Episode {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE)

        // variables needed for MediaMetadata builder
        var episode: Episode = Episode()

        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found episode title
                Keys.RSS_EPISODE_GUID -> episode.guid = XmlHelper.readEpisodeGuid(parser, nameSpace)
                // found episode title
                Keys.RSS_EPISODE_TITLE -> episode.title = XmlHelper.readEpisodeTitle(parser, nameSpace)
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION -> episode.description = XmlHelper.readEpisodeDescription(parser, nameSpace)
                // found episode publication date
                Keys.RSS_EPISODE_PUBLICATION_DATE -> episode.publicationDate = XmlHelper.readEpisodePublicationDate(parser, nameSpace)
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> episode.remoteAudioFileLocation = XmlHelper.readEpisodeAudioLink(parser, nameSpace)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return episode
    }

}