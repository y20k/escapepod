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
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.LogHelper
import java.io.InputStream


/*
 * XmlReader class
 */
class XmlReader : AsyncTask<InputStream, Void, Podcast>() {

    /* Define log tag */
    private val TAG : String = LogHelper.makeLogTag(XmlReader::class.java.name)

    /* Implements doInBackground */
    override fun doInBackground(vararg params: InputStream): Podcast {
        val xmlStream : InputStream = params[0]
        val parser : XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(xmlStream, null)

        // todo implment

        return Podcast()
    }


    /* Implements onPostExecute */
    override fun onPostExecute(result: Podcast) {
        super.onPostExecute(result)
        // todo implement a callback to calling class
    }

}