/*
 * DateHelper.kt
 * Implements the DateHelper object
 * A DateHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import java.text.SimpleDateFormat
import java.util.*


/*
 * DateHelper object
 */
object DateHelper {


    /* Main class variables */
    private val pattern: String = "EEE, dd MMM yyyy HH:mm:ss Z"
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertFromRfc2822(dateString: String): Date {
        if (dateString.isEmpty()) {
            // return Calendar.getInstance().time
            return Date()
        } else {
            return dateFormat.parse((dateString)) ?: Calendar.getInstance().time
        }
    }


    /* Converts a DATE to its RFC 2822 string representation */
    fun convertToRfc2822(date: Date): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return dateFormat.format(date)
    }

}