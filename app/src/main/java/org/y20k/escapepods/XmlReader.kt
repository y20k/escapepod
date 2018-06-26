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

import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaBrowserCompat
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.LogHelper
import java.io.InputStream
import java.util.*


/*
 * XmlReader class
 */
class XmlReader: AsyncTask<InputStream, Void, Podcast>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)


    /* Mail class variables */
    private var podcastName: String = ""
    private var episodes: TreeMap<String, MediaBrowserCompat.MediaItem> = TreeMap<String, MediaBrowserCompat.MediaItem>()
    private var currentStartTag: String = ""
    private var parsingEpisode: Boolean = false


    /* Implements doInBackground */
    override fun doInBackground(vararg params: InputStream): Podcast {
        val xmlStream: InputStream = params[0]
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(xmlStream, null)
            return readFeed(parser)
        } finally {
            xmlStream.close()
        }
    }


    /* Implements onPostExecute */
    override fun onPostExecute(result: Podcast) {
        super.onPostExecute(result)
        // todo implement a callback to calling class
    }


    private fun readFeed(parser: XmlPullParser) : Podcast {

        // test
        var podcastName: String = "";
        var episode: MediaBrowserCompat.MediaItem
        var episodes: TreeMap<String, MediaBrowserCompat.MediaItem>

        var eventType: Int = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> processStartDocument(parser)
                XmlPullParser.START_TAG -> processStartTag(parser)
                XmlPullParser.END_TAG -> processEndTag(parser)
                XmlPullParser.TEXT -> processText(parser)
            }
            eventType = parser.next();
        }
        LogHelper.e(TAG, "End of document") // todo remove

        return Podcast("Title", Uri.parse("http://www.y20k.org"), TreeMap())
    }


    private fun processStartDocument(parser: XmlPullParser) {
        LogHelper.e(TAG, "Start of document") // todo remove
    }


    private fun processStartTag(parser: XmlPullParser) {
        currentStartTag = parser.name
        if (parser.name == "item") {
            parsingEpisode
        }
    }


    private fun processEndTag(parser: XmlPullParser) {
//        if (parser.name == "item") {
//            !parsingEpisode
//        }

        if (parser.name == currentStartTag) {
            currentStartTag = ""
        }
    }


    fun processText(parser: XmlPullParser) {
//        LogHelper.e(TAG, "Text: ${parser.text}") // todo remove
        when (currentStartTag) {
            "title" -> {
                if (!parsingEpisode) {
                    if (podcastName.length == 0) {
                        podcastName = parser.text
                        LogHelper.e(TAG, "Podcast Name: ${parser.text}") // todo remove
                    }
                } else {
                    LogHelper.e(TAG, "Episode Title: ${parser.text}") // todo remove
                }
            }
        }

    }


//    private fun readFeed2(parser: XmlPullParser) {
//        var eventType = parser.next()
//        while (eventType != XmlPullParser.END_DOCUMENT) {
//            if (eventType == XmlPullParser.START_TAG && 0 == XML_ELEMENT_TAG.compareTo(parser.name)) {
//                loadElement(parser)
//            }
//
//            eventType = parser.next()
//        }
//    }
//
//
//    private fun loadElement(xpp: XmlPullParser) {
//
//        var eventType = xpp.eventType
//        if (eventType == XmlPullParser.START_TAG && 0 == XML_ELEMENT_TAG.compareTo(xpp.name)) {
//            eventType = xpp.next()
//            while (eventType != XmlPullParser.END_TAG || 0 != XML_ELEMENT_TAG.compareTo(xpp.name)) {
//                if (eventType == XmlPullParser.START_TAG && 0 == XML_ITEM_TAG.compareTo(xpp.name)) {
//                    loadItem(xpp)
//                }
//
//                eventType = xpp.next()
//            }
//        }
//    }
//
//    private fun loadItem(xpp: XmlPullParser) {
//
//        var eventType = xpp.eventType
//        if (eventType == XmlPullParser.START_TAG && 0 == XML_ITEM_TAG.compareTo(xpp.name)) {
//
//            eventType = xpp.next()
//            while (eventType != XmlPullParser.END_TAG || 0 != XML_ITEM_TAG.compareTo(xpp.name)) {
//
//                // Get attributes.
//                val attr = xpp.getAttributeValue(null, XML_MY_ATTR)
//                var text: String? = null
//
//                // Get item text if present.
//                eventType = xpp.next()
//                while (eventType != XmlPullParser.END_TAG || 0 != XML_ITEM_TAG.compareTo(xpp.name)) {
//                    if (eventType == XmlPullParser.TEXT) {
//                        text = xpp.text
//                    }
//
//                    eventType = xpp.next()
//                }
//
//                eventType = xpp.next()
//            }
//        }
//    }

}