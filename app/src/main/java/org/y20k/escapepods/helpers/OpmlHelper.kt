/*
 * OpmlHelper.kt
 * Implements the OpmlHelper object
 * An OpmlHelper provides helper methods for exporting and importing OPML podcast lists
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import org.y20k.escapepods.core.Collection


/*
 * OpmlHelper object
 */
object OpmlHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(OpmlHelper::class.java)


    /* Create OPML string from podcast collection */
    fun createOpmlString(collection: Collection): String {
        val opmlString = StringBuilder()
        // add opening tag
        opmlString.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        opmlString.append("<opml version=\"1.0\">\n")

        // add <head> section
        opmlString.append("\t<head>\n")
        opmlString.append("\t\t<title>Escapepods</title>\n")
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