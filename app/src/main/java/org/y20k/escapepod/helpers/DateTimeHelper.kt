/*
 * DateTimeHelper.kt
 * Implements the DateTimeHelper object
 * A DateTimeHelper provides helper methods for converting Date and Time objects
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.os.Build
import androidx.annotation.RequiresApi
import org.y20k.escapepod.Keys
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/*
 * DateTimeHelper object
 */
object DateTimeHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DateTimeHelper::class.java)

    /* Main class variables */
    private const val rfc2822Pattern: String = "EEE, d MMM yyyy HH:mm:ss zzz"
    private const val rfc2822patternWithoutTimezone: String = "EEE, d MMM yyyy HH:mm:ss"
    private const val rfc2822patternWithoutSeconds: String = "EEE, d MMM yyyy HH:mm"


    /* Creates a readable date string */
    fun getDateString(date: Date, dateStyle: Int = DateFormat.MEDIUM): String {
        return DateFormat.getDateInstance(dateStyle, Locale.getDefault()).format(date)
    }


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertFromRfc2822(dateString: String): Date {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return convertToDateFromRfc2822(dateString)
        } else {
            return convertToDateFromRfc2822Legacy(dateString)
        }
    }


    /* Converts a DATE to its RFC 2822 string representation */
    fun convertToRfc2822(date: Date): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat(rfc2822Pattern, Locale.ENGLISH)
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


    /* Determines if a date is too far in the future. Used to determine, if date is unrealistic */
    fun isSignificantlyInTheFuture(date: Date, hoursInTheFuture: Int = 48): Boolean {
        val now = GregorianCalendar.getInstance().timeInMillis
        val significantlyInTheFuture = now + (hoursInTheFuture * 3600 * 1000) // hours to milliseconds
        return date.time > significantlyInTheFuture
    }


    /* Converts RFC 2822 string representation of a date to DATE - version using DateTimeFormatter */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun convertToDateFromRfc2822(dateString: String) : Date {
        var date: Date
        try {
            // 1st try: parse using standard formatter (RFC_1123_DATE_TIME)
            val formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
            val zonedDateTime: ZonedDateTime = ZonedDateTime.from(formatter.parse(dateString))
            date =  Date.from(zonedDateTime.toInstant()) ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            LogHelper.w(TAG, "Unable to parse. Trying an alternative Date formatter. $e")
            try {
                // 2nd try: parse using standard pattern
                val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(rfc2822Pattern, Locale.ENGLISH)
                val zonedDateTime: ZonedDateTime = ZonedDateTime.from(formatter.parse(dateString))
                date =  Date.from(zonedDateTime.toInstant()) ?: Keys.DEFAULT_DATE
            } catch (e: Exception) {
                LogHelper.w(TAG, "Unable to parse. Trying an alternative Date formatter. $e")
                try {
                    // 3nd try: parse without time zone
                    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(rfc2822patternWithoutTimezone, Locale.ENGLISH).withZone(ZoneId.systemDefault())
                    val zonedDateTime: ZonedDateTime = ZonedDateTime.from(formatter.parse(dateString))
                    date =  Date.from(zonedDateTime.toInstant()) ?: Keys.DEFAULT_DATE
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Unable to parse. Trying an alternative Date formatter. $e")
                    try {
                        // 4rd try: parse without seconds
                        val dateStringTrimmed: String = dateString.substring(0, (dateString.lastIndexOf(":")+3)) // remove anything after HH:mm
                        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(rfc2822patternWithoutSeconds, Locale.ENGLISH).withZone(ZoneId.systemDefault())
                        val zonedDateTime: ZonedDateTime = ZonedDateTime.from(formatter.parse(dateStringTrimmed))
                        date =  Date.from(zonedDateTime.toInstant()) ?: Keys.DEFAULT_DATE
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Unable to parse. Retry using legacy method. $e")
                        date = convertToDateFromRfc2822Legacy(dateString)
                    }
                }
            }
        }
        return date
    }


    /* Converts RFC 2822 string representation of a date to DATE - version using SimpleDateFormat */
    private fun convertToDateFromRfc2822Legacy(dateString: String) : Date {
        var date: Date
        try {
            // 1st try: parse using standard pattern
            val dateFormat: SimpleDateFormat = SimpleDateFormat(rfc2822Pattern, Locale.ENGLISH)
            date = dateFormat.parse(dateString) ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            LogHelper.w(TAG, "Unable to parse. Trying an alternative Date format. $e")
            try {
                // 2nd try: parse without time zone
                val dateFormat: SimpleDateFormat = SimpleDateFormat(rfc2822patternWithoutTimezone, Locale.ENGLISH)
                date = dateFormat.parse(dateString) ?: Keys.DEFAULT_DATE
            } catch (e: Exception) {
                LogHelper.w(TAG, "Unable to parse. Trying an alternative Date format. $e")
                try {
                    // 3rd try: parse without seconds
                    val dateFormat: SimpleDateFormat = SimpleDateFormat(rfc2822patternWithoutSeconds, Locale.ENGLISH)
                    date = dateFormat.parse(dateString) ?: Keys.DEFAULT_DATE
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Unable to parse. Returning a default date. $e")
                    date = Keys.DEFAULT_DATE
                }
            }
        }
        return date
    }


}