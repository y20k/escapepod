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
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.XmlHelper
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList


/*
 * XmlReader class
 */
class XmlReader: AsyncTask<InputStream, Void, Podcast>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(XmlReader::class.java.name)


    /* Mail class variables */
    private val nameSpace: String? = null


    /* Implements doInBackground */
    override fun doInBackground(vararg params: InputStream): Podcast {
        val xmlStream: InputStream = params[0]
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(xmlStream, null)

        return readFeed(parser)
    }


    /* Implements onPostExecute */
    override fun onPostExecute(result: Podcast) {
        super.onPostExecute(result)
        // todo implement a callback to calling class
    }



    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): Podcast {

        // test
        var podcastName: String? = null;
        var episodeNames: ArrayList<String> = ArrayList<String>()


        var eventType = parser.eventType
        LogHelper.e("TAG", "The event type is: $eventType")

        while (eventType != XmlPullParser.START_DOCUMENT) {
            eventType = parser.next()
            LogHelper.e("TAG", "The event type is: $eventType")
        }



        parser.require(XmlPullParser.START_TAG, nameSpace, "rss");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.name
            // Starts by looking for the entry tag
            if (name == "channel") {
                podcastName = XmlHelper.readPodcastTitle(parser, nameSpace)
            } else if (name == "entry") {
                episodeNames.add(XmlHelper.readEpisodeTitle(parser, nameSpace))
            } else {
                skip(parser)
            }
        }

        return Podcast("Title", Uri.parse("http://www.y20k.org"), TreeMap())
    }


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