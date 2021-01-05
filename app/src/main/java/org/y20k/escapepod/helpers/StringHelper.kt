/*
 * StringHelper.kt
 * Implements the StringHelper object
 * A StringHelper provides helper methods for working with strings
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.util.Base64
import java.security.MessageDigest
import java.util.*


/*
 * StringHelper object
 */
object StringHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(StringHelper::class.java)


    /* Creates SHA-1 hash for given text */
    fun createSha1(text: String): String {
        return try {
            val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-1")
            messageDigest.update(stringToByteArray(text))
            byteArrayToString(messageDigest.digest())
        } catch (ignored: Exception) {
            ignored.printStackTrace()
            String()
        }
    }

    /* Encrypts a text to BASE64 */
    fun encrypt(text: String): String {
        val data: ByteArray = stringToByteArray(text)
        return Base64.encodeToString(data, Base64.DEFAULT)
    }


    /* Decrypts a text from BASE64 */
    fun decrypt(encryptedText: String): String {
        val data: ByteArray = Base64.decode(encryptedText, Base64.DEFAULT)
        return String(data, charset("UTF-8"))
    }


    /* Converts byte array to text string */
    private fun byteArrayToString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(java.lang.String.format(Locale.getDefault(), "%02x", b))
        }
        return sb.toString()
    }


    /* Converts text string to byte array */
    private fun stringToByteArray(string: String): ByteArray {
        return string.toByteArray(charset("UTF-8"))
    }

}