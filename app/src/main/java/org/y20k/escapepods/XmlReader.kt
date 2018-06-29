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

import android.os.AsyncTask
import android.support.v4.media.MediaMetadataCompat
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.XmlHelper
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


/*
 * XmlReader class
 */
class XmlReader(var xmlReaderListener: XmlReaderListener, val remotePodcastFeedLocation: String): AsyncTask<InputStream, Void, Podcast>() {

    /* Interface used to communicate back to activity */
    interface XmlReaderListener {
        fun onParseResult(podcast: Podcast) {
        }
    }


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)


    /* Main class variables */
    private val nameSpace: String? = null
    private var podcast: Podcast = Podcast()


    /* Implements onPreExecute */
    override fun onPreExecute() {
        podcast.remotePodcastFeedLocation = remotePodcastFeedLocation
    }


    /* Implements doInBackground */
    override fun doInBackground(vararg params: InputStream): Podcast {
        // get InputStream from params
        val stream: InputStream = params[0]
        try {
            // create XmlPullParser for InputStream
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(stream, null)
            parser.nextTag();
            // start reading rss feed
            return readFeed(parser)
        } catch (exception : Exception) {
            exception.printStackTrace()
            return podcast
        } finally {
            stream.close()
        }
    }


    /* Implements onPostExecute */
    override fun onPostExecute(podcast: Podcast) {
        // log success // todo remove
        if (podcast.name.isNotEmpty()) {
            xmlReaderListener.onParseResult(podcast)
            LogHelper.e(TAG, "\n${podcast.toString()}") // todo remove
        } else {
            LogHelper.e(TAG, "The given address is not a podcast")
        }
    }


    /* Reads whole feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): Podcast {
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
                Keys.RSS_PODCAST_IMAGE -> podcast.remoteImageFileLocation = XmlHelper.readPodcastImage(parser, nameSpace)
                // found an episode
                Keys.RSS_EPISODE -> {
                    val episode: MediaMetadataCompat = readEpisode(parser)
                    val key: Date = convertToDate(episode.getString(Keys.METADATA_CUSTOM_KEY_PUBLICATION_DATE))
                    podcast.episodes.add(episode)
                }
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }

    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser): MediaMetadataCompat {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE)

        // variables needed for MediaMetadata builder
        var title: String = ""
        var description: String = ""
        var publicationDate: String = ""
        var audioUrl: String = ""

        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found episode title
                Keys.RSS_EPISODE_TITLE -> title = XmlHelper.readEpisodeTitle(parser, nameSpace)
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION -> description = XmlHelper.readEpisodeDescription(parser, nameSpace)
                // found episode publication date
                Keys.RSS_EPISODE_PUBLICATION_DATE -> publicationDate = XmlHelper.readEpisodePublicationDate(parser, nameSpace)
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> audioUrl = XmlHelper.readEpisodeAudioLink(parser, nameSpace)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, publicationDate+title)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, podcast.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, podcast.image)
                .putString(Keys.METADATA_CUSTOM_KEY_DESCRIPTION, description)
                .putString(Keys.METADATA_CUSTOM_KEY_PUBLICATION_DATE, publicationDate)
                .putString(Keys.METADATA_CUSTOM_KEY_AUDIO_LINK_URL, audioUrl)
                .putString(Keys.METADATA_CUSTOM_KEY_IMAGE_LINK_URL, podcast.remoteImageFileLocation)
                .build()
    }


    /* Converts RFC 2822 string representation of a date to DATE */ // todo move somewhere else
    private fun convertToDate(dateString: String): Date {
        val pattern = "EEE, dd MMM yyyy HH:mm:ss Z"
        val format = SimpleDateFormat(pattern, Locale.ENGLISH)
        return format.parse((dateString))
    }

}