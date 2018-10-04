/*
 * Collection.kt
 * Implements the Collection class
 * A Collection object holds a list of Podcasts
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.core

import com.google.gson.annotations.Expose
import org.y20k.escapepods.helpers.Keys
import java.util.*


/*
 * Collection class
 */
class Collection(@Expose var podcasts: MutableList<Podcast> = mutableListOf<Podcast>(),
                 @Expose val version: Int = Keys.CURRENT_COLLECTION_CLASS_VERSION_NUMBER,
                 @Expose var lastUpdate: Date = Date(0)) {

    /* overrides toString method */
    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder()
        stringBuilder.append("Format version: $version\n")
        stringBuilder.append("Last update of collection: $lastUpdate\n")
        stringBuilder.append("Number of podcasts in collection: ${podcasts.size}\n\n")
        podcasts.forEach {
            stringBuilder.append("${it.toString()}\n")
        }
        return stringBuilder.toString()
    }



}


