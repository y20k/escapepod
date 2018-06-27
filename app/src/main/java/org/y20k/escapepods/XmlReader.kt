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
import org.y20k.escapepods.helpers.LogHelper
import java.io.IOException
import java.io.InputStream
import java.util.*


/*
 * XmlReader class
 */
class XmlReader: AsyncTask<InputStream, Void, List<*>>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)
    // We don't use namespaces
    private val ns: String? = null


    /* Mail class variables */
    private var podcastName: String = ""
    private var episodes: TreeMap<String, MediaBrowserCompat.MediaItem> = TreeMap<String, MediaBrowserCompat.MediaItem>()
    private var currentStartTag: String = ""
    private var parsingEpisode: Boolean = false


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


    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): List<*> {
        val entries: ArrayList<Entry> = ArrayList()

        parser.require(XmlPullParser.START_TAG, ns, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.name

            // Starts by looking for the channel tag
            if (name == "channel") {
                readChannel(parser)
            } else {
                skip(parser)
            }
        }
        return entries
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readChannel(parser: XmlPullParser): List<*> {
        val entries: ArrayList<Entry> = ArrayList()

        parser.require(XmlPullParser.START_TAG, ns, "channel")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.name
            // Starts by looking for the entry tag
            if (name == "title") {
                LogHelper.e(TAG, "TITLE: ${readTitle(parser)}")
            } else if (name == "description") {
                LogHelper.e(TAG, "DESCRIPTION: ${readDescription(parser)}")
            } else if (name == "item") {
                entries.add(readEntry(parser))
            } else {
                skip(parser)
            }
        }

        LogHelper.e(TAG, "SIZE: ${entries.size}")
        for (entry in entries) {
            LogHelper.e(TAG, "${entry.title} \n ${entry.summary} \n ${entry.link} ")
        }

        return entries
    }



    // Parses the contents of an entry. If it encounters a title, summary, or link tag, hands them off
    // to their respective "read" methods for processing. Otherwise, skips the tag.
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEntry(parser: XmlPullParser): Entry {
        parser.require(XmlPullParser.START_TAG, ns, "item")
        var title: String? = null
        var summary: String? = null
        var link: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.name
            if (name == "title") {
                title = readTitle(parser)
            } else if (name == "description") {
                summary = readDescription(parser)
            } else if (name == "enclosure") {
                link = readEnclosureLink(parser)
            } else {
                skip(parser)
            }
        }
        return Entry(title, summary, link)
    }

    // Processes title tags in the feed.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTitle(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "title")
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "title")
        return title
    }

    // Processes link tags in the feed.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEnclosureLink(parser: XmlPullParser): String {
        var link = ""
        parser.require(XmlPullParser.START_TAG, ns, "enclosure")
        val tag = parser.name
        val type = parser.getAttributeValue(null, "type")
        if (tag == "enclosure") {
            if (type.contains("audio")) {
                link = parser.getAttributeValue(null, "url")
                parser.nextTag()
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "enclosure")
        return link
    }

    // Processes summary tags in the feed.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readDescription(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "description")
        val summary = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "description")
        return summary
    }

    // For the tags title and summary, extracts their text values.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }


    class Entry (val title: String?, val summary: String?, val link: String?)

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
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

}