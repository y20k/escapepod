/*
 * PodcastCollectionHelper.kt
 * Implements the PodcastCollectionHelper class
 * A PodcastCollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import org.y20k.escapepods.core.PodcastCollection
import java.util.*


/*
 * PodcastCollectionHelper class
 */
class PodcastCollectionHelper {

    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(podcastCollection: PodcastCollection): Boolean {
        val currentDate: Date = Calendar.getInstance().time
        return true // todo remove
//        return currentDate.time - podcastCollection.lastUpdate.time  > FIVE_MINUTES_IN_MILLISECONDS
    }

}