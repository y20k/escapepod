/*
 * OpmlHelper.kt
 * Implements the OpmlHelper class
 * An OpmlHelper provides helper methods for exporting and importing OPML podcast lists
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
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.helpers.LogHelper
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * OpmlHelper helper
 */
class OpmlHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(OpmlHelper::class.java)


    /* Main class variables */
    private var feedUrlList: ArrayList<String> = arrayListOf()


    /* Suspend function: Read OPML feed from given input stream - async using coroutine */
    suspend fun readSuspended(context: Context, localFileUri: Uri): Array<String> {
        return suspendCoroutine {cont ->
           LogHelper.v(TAG, "Reading OPML feed - Thread: ${Thread.currentThread().name}")
            // try parsing
            val stream: InputStream? = FileHelper.getTextFileStream(context, localFileUri)
            try {
                // create XmlPullParser for InputStream
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                parser.nextTag()
                // start reading opml feed
                feedUrlList = parseFeed(parser)
            } catch (exception : Exception) {
                exception.printStackTrace()
            } finally {
                stream?.close()
            }

            // return parsing result
            cont.resume(feedUrlList.toTypedArray())
        }
    }


    /* Parses whole OPML feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseFeed(parser: XmlPullParser): ArrayList<String> {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.OPML_OPML)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found a podcast
                Keys.OPML_BODY -> readBody(parser)
                // skip to next tag
                else -> XmlHelper.skip(parser)
            }
        }
        return feedUrlList
    }


    /* Reads body element - within feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readBody(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.OPML_BODY)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found an outline
                Keys.OPML_OUTLINE -> {
                    LogHelper.e(TAG, "found an outline") // todo remove
                    val feedUrl: String = readOutline(parser, Keys.XML_NAME_SPACE)
                    if (feedUrl.isNotEmpty()) {
                        feedUrlList.add(feedUrl)
                    } else {
                        XmlHelper.skip(parser)
                    }
                }
                // skip to next tag
                else -> {
                    LogHelper.e(TAG, "skip to next tag") // todo remove
                    XmlHelper.skip(parser)
                }
            }
        }

    }


    /* Reads (parent) outline tag - within body element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readParentOutline(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.OPML_OUTLINE)
        while (parser.next() != XmlPullParser.END_TAG) {
            // abort loop early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // readSuspended only relevant tags
            when (parser.name) {
                // found child outline element - usually containing a podcast url
                Keys.OPML_OUTLINE -> {
                    if (outlineTagIsParent(parser, Keys.XML_NAME_SPACE)) {
                        // todo
                        //  skip to next
                        //  read child outline
                    } else {
                        // todo
                        //  just read child outline
                        val feedUrl: String = readOutline(parser, Keys.XML_NAME_SPACE)
                        if (feedUrl.isNotEmpty()) {
                            feedUrlList.add(feedUrl)
                        }
                    }
                }
                // skip to next tag
                else -> { XmlHelper.skip(parser)
                }
            }
        }
    }


    /* Reads podcast URL from child outline element */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readOutline(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.OPML_OUTLINE)
        val tag = parser.name
        if (tag == Keys.OPML_OUTLINE) {
            val attributeCount: Int = parser.attributeCount
            for (i in 0 until attributeCount) {
                when (parser.getAttributeName(i)) {
                    Keys.OPML_OUTLINE_TYPE -> {
                        val type = parser.getAttributeValue(null, Keys.OPML_OUTLINE_TYPE)
                        when (type) {
                            Keys.OPML_OUTLINE_TYPE_RSS -> {
                                link = parser.getAttributeValue(null, Keys.OPML_OUTLINE_XML_URL)
                                parser.nextTag()
                            }
                            else -> {
                                parser.nextTag()
                            }
                        }
                    }
                    Keys.OPML_OUTLINE_TEXT -> {
                        val text = parser.getAttributeValue(null, Keys.OPML_OUTLINE_TEXT)
                        if (text == Keys.OPML_OUTLINE_TEXT_FEEDS) {
                            LogHelper.d(TAG, "feeds!") // todo remove
                        }
                    }
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.OPML_OUTLINE)
        return link
    }


    @Throws(IOException::class, XmlPullParserException::class)
    private fun outlineTagIsParent(parser: XmlPullParser, nameSpace: String?): Boolean {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.OPML_OUTLINE)
        val attributeCount: Int = parser.attributeCount
        for (i in 0 until attributeCount) {
            if (parser.getAttributeName(i) == Keys.OPML_OUTLINE_TEXT) {
                val text = parser.getAttributeValue(null, Keys.OPML_OUTLINE_TEXT)
                if (text == Keys.OPML_OUTLINE_TEXT_FEEDS) {
                    return true
                }
            }
        }
        return false
    }


    /* Create OPML string from podcast collection */
    fun createOpmlString(collection: Collection): String {
        val opmlString = StringBuilder()
        // add opening tag
        opmlString.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        opmlString.append("<opml version=\"1.0\">\n")

        // add <head> section
        opmlString.append("\t<head>\n")
        opmlString.append("\t\t<title>Escapepod</title>\n")
        opmlString.append("\t</head>\n")

        // add opening <body> and <outline> tags
        opmlString.append("\t<body>\n")
        opmlString.append("\t\t<outline text=\"feeds\">\n")

        // add <outline> tags containing name and URL for each podcast
        collection.podcasts.forEach { podcast ->
            opmlString.append("\t\t\t<outline type=\"rss\" text=\"")
            opmlString.append(podcast.name)
            opmlString.append("\" xmlUrl=\"")
            opmlString.append(podcast.remotePodcastFeedLocation)
            opmlString.append("\" />\n")
        }

        // add <outline> and <body> closing tags
        opmlString.append("\t\t</outline>\n")
        opmlString.append("\t</body>\n")

        // add <opml> closing tag
        opmlString.append("</opml>\n")

        return opmlString.toString()
    }

}