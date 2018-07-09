/*
 * DateHelper.kt
 * Implements the DateHelper class
 * A DateHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import java.text.SimpleDateFormat
import java.util.*


/*
 * DateHelper class
 */
class DateHelper {


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertRFC2822(dateString: String): Date {
        val pattern = "EEE, dd MMM yyyy HH:mm:ss Z"
        val format = SimpleDateFormat(pattern, Locale.ENGLISH)
        var date = format.parse((dateString))
        if (date == null) {
            date = Calendar.getInstance().time
        }
        return date
    }
}