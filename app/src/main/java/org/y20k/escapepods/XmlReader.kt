/*
 * XmlReader.kt
 * Implements the XmlReader class
 * A XmlReader reads and parses podcast rss feeds
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
import android.support.v4.media.MediaBrowserCompat
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.XmlHelper
import java.io.IOException
import java.io.InputStream
import java.util.*


/*
 * XmlReader class
 */
class XmlReader: AsyncTask<InputStream, Void, List<*>>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)


    /* Mail class variables */
    private val nameSpace: String? = null
    private var podcastName: String = ""
    private var podcastDescription: String = ""
    private var episodes: TreeMap<String, MediaBrowserCompat.MediaItem> = TreeMap<String, MediaBrowserCompat.MediaItem>()


    /* Implements doInBackground */
    override fun doInBackground(vararg params: InputStream): List<*> {
//    override fun doInBackground(vararg params: InputStream): Podcast {
        val xmlStream: InputStream = params[0]
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(xmlStream, null)
            parser.nextTag();

            return readFeed(parser)
        } finally {
            xmlStream.close()
        }
    }


    /* Implements onPostExecute */
    override fun onPostExecute(result: List<*>) {
//    override fun onPostExecute(result: Podcast) {
        super.onPostExecute(result)
        // todo implement a callback to calling class
    }


    /* Reads whole feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): List<*> {
        val entries: ArrayList<Episode> = ArrayList()

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
        return entries
    }


    /* Reads podcast element - within feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPodcast(parser: XmlPullParser): List<*> {
        val entries: ArrayList<Episode> = ArrayList()

        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found a podcast name
                Keys.RSS_PODCAST_NAME -> LogHelper.e(TAG, "TITLE: ${XmlHelper.readPodcastTitle(parser, nameSpace)}")
                // found a podcast description
                Keys.RSS_PODCAST_DESCRIPTION -> LogHelper.e(TAG, "DESCRIPTION: ${XmlHelper.readPodcastDescription(parser, nameSpace)}")
                // found an episode
                Keys.RSS_EPISODE -> entries.add(readEpisode(parser))
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }

        LogHelper.e(TAG, "SIZE: ${entries.size}")
        for (entry in entries) {
            LogHelper.e(TAG, "${entry.title} \n ${entry.description} \n ${entry.link} ")
        }

        return entries
    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser): Episode {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE)
        var title: String = ""
        var description: String = ""
        var link: String = ""
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
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> link = XmlHelper.readEpisodeAudioLink(parser, nameSpace)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return Episode(title, description, link)
    }


    // inner class // todo replace with MediaItemCompat
    class Episode (val title: String?, val description: String?, val link: String?)


}