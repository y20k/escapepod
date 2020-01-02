/*
 * DateTimeHelper.kt
 * Implements the DateTimeHelper object
 * A DateTimeHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
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

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DateTimeHelper::class.java)

    /* Main class variables */
    private const val pattern: String = "EEE, dd MMM yyyy HH:mm:ss Z"
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertFromRfc2822(dateString: String): Date {
        var date: Date = Keys.DEFAULT_DATE
        try {
            // parse date string using standard pattern
            date = dateFormat.parse((dateString)) ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            try {
                // try to parse without seconds - if first attempt failed
                date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm Z", Locale.ENGLISH).parse((dateString)) ?: Keys.DEFAULT_DATE
                LogHelper.w(TAG, "Unable to parse. Trying an alternative Date format. $e")
            } catch (e: Exception) {
                LogHelper.e(TAG, "Unable to parse. Returning a default date. $e")
            }
        }
        return date
    }


    /* Converts a DATE to its RFC 2822 string representation */
    fun convertToRfc2822(date: Date): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return dateFormat.format(date)
    }


    /* Converts a milliseconds in to a readable format */
    fun convertToMinutesAndSeconds(milliseconds: Long, negativeValue: Boolean = false): String {
        // convert milliseconds to minutes and seconds
        val minutes: Long = milliseconds / 1000 / 60
        val seconds: Long = milliseconds / 1000 % 60
        var timeString: String = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        if (negativeValue) {
            // add a minus sign if a negative values was requested
            timeString = "-$timeString"
        }
        return timeString
    }

}