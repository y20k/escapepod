/*
 * LogHelper.kt
 * Implements the LogHelper object
 * A LogHelper wraps the logging calls to be able to strip them out of release versions
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.y20k.escapepod.BuildConfig
import org.y20k.escapepod.R
import java.util.*


/*
 * LogHelper object
 */
object LogHelper {

    private const val TESTING: Boolean = false // set to "false"
    private const val LOG_PREFIX: String = "escapepod_"
    private const val MAX_LOG_TAG_LENGTH: Int = 64
    private const val LOG_PREFIX_LENGTH: Int = LOG_PREFIX.length

    fun makeLogTag(str: String): String {
        return if (str.length > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1)
        } else LOG_PREFIX + str
    }

    fun makeLogTag(cls: Class<*>): String {
        // don't use this when obfuscating class names
        return makeLogTag(cls.simpleName)
    }

    fun v(tag: String, vararg messages: Any) {
        // Only log VERBOSE if build type is DEBUG or if TESTING is true
        if (BuildConfig.DEBUG || TESTING) {
            log(tag, Log.VERBOSE, null, *messages)
        }
    }

    fun d(tag: String, vararg messages: Any) {
        // Only log DEBUG if build type is DEBUG or if TESTING is true
        if (BuildConfig.DEBUG || TESTING) {
            log(tag, Log.DEBUG, null, *messages)
        }
    }

    fun i(tag: String, vararg messages: Any) {
        log(tag, Log.INFO, null, *messages)
    }

    fun w(tag: String, vararg messages: Any) {
        log(tag, Log.WARN, null, *messages)
    }

    fun w(tag: String, t: Throwable, vararg messages: Any) {
        log(tag, Log.WARN, t, *messages)
    }

    fun e(tag: String, vararg messages: Any) {
        log(tag, Log.ERROR, null, *messages)
    }

    fun e(tag: String, t: Throwable, vararg messages: Any) {
        log(tag, Log.ERROR, t, *messages)
    }

    fun save(context: Context, tag: String, vararg messages: Any) {
        save(context, tag, null, *messages)
    }

    fun save(context: Context, tag: String, t: Throwable?, vararg messages: Any) {
        if (PreferencesHelper.loadKeepDebugLog(context)) {
            val sb = StringBuilder()
            sb.append(DateTimeHelper.convertToRfc2822(Calendar.getInstance().time))
            sb.append(" | ")
            sb.append(tag)
            sb.append(" | ")
            for (m in messages) {
                sb.append(m)
            }
            if (t != null) {
                sb.append("\n")
                sb.append(Log.getStackTraceString(t))
            }
            sb.append("\n")
            val message = sb.toString()
            FileHelper.saveLog(context, message)
        }
    }

    fun toggleDebugLogFileCreation(context: Context) {
        when (PreferencesHelper.loadKeepDebugLog(context)) {
            true -> {
                // turn off log
                PreferencesHelper.saveKeepDebugLog(context, false)
                FileHelper.deleteLog(context)
                Toast.makeText(context, R.string.toast_message_debug_logging_to_file_stopped, Toast.LENGTH_LONG).show()
            }
            false -> {
                // turn on log
                PreferencesHelper.saveKeepDebugLog(context, true)
                Toast.makeText(context, R.string.toast_message_debug_logging_to_file_started, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun log(tag: String, level: Int, t: Throwable?, vararg messages: Any) {
        val message: String
        if (t == null && messages.size == 1) {
            // handle this common case without the extra cost of creating a stringbuffer:
            message = messages[0].toString()
        } else {
            val sb = StringBuilder()
            for (m in messages) {
                sb.append(m)
            }
            if (t != null) {
                sb.append("\n").append(Log.getStackTraceString(t))
            }
            message = sb.toString()
        }
        Log.println(level, tag, message)

//        if (Log.isLoggable(tag, level)) {
//            val message: String
//            if (t == null && messages != null && messages.size == 1) {
//                // handle this common case without the extra cost of creating a stringbuffer:
//                message = messages[0].toString()
//            } else {
//                val sb = StringBuilder()
//                if (messages != null)
//                    for (m in messages) {
//                        sb.append(m)
//                    }
//                if (t != null) {
//                    sb.append("\n").append(Log.getStackTraceString(t))
//                }
//                message = sb.toString()
//            }
//            Log.println(level, tag, message)
//        }
    }
}
