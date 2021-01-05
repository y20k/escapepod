/*
 * XmlHelper.kt
 * Implements the XmlHelper object
 * A XmlHelper provides helper methods for reading xml feeds
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.xml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepod.helpers.LogHelper
import java.io.IOException


/*
 * XmlHelper object
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


    /* Reads text */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

}