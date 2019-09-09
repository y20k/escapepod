/*
 * DateTimeHelper.kt
 * Implements the DateTimeHelper object
 * A DateTimeHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import org.y20k.escapepod.Keys
import java.text.SimpleDateFormat
import java.util.*


/*
 * DateTimeHelper object
 */
object DateTimeHelper {


    /* Main class variables */
    private val pattern: String = "EEE, dd MMM yyyy HH:mm:ss Z"
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertFromRfc2822(dateString: String): Date {
        if (dateString.isEmpty()) {
            return Keys.DEFAULT_DATE
        } else {
            return dateFormat.parse((dateString)) ?: Keys.DEFAULT_DATE
        }
    }


    /* Converts a DATE to its RFC 2822 string representation */
    fun convertToRfc2822(date: Date): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return dateFormat.format(date)
    }


    /* Converts a milliseconds in to a readable format */
    fun convertToMinutesAndSeconds(milliseconds: Long): String {
        val minutes: Long = milliseconds / 1000 / 60
        val seconds: Long = milliseconds / 1000 % 60
        return ("${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}")
    }

}