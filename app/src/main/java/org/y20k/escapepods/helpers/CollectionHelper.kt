/*
 * CollectionHelper.kt
 * Implements the CollectionHelper class
 * A CollectionHelper provides helper methods for the podcast collection
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
import java.util.*


/*
 * CollectionHelper class
 */
class CollectionHelper {

    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(collection: Collection): Boolean {
        val currentDate: Date = Calendar.getInstance().time
        return true // todo remove
//        return currentDate.time - collection.lastUpdate.time  > FIVE_MINUTES_IN_MILLISECONDS
    }

}