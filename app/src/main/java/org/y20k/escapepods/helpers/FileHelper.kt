/*
 * FileHelper.kt
 * Implements the FileHelper class
 * A FileHelper is provides helper methods for reading and writing files from and to  device storage
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.net.Uri
import java.io.*


/**
 * FileHelper class
 */
class FileHelper {

    /* Reads InputStream from file uri and returns it as String*/
    fun readTextFile(context : Context, uri: Uri) : String {
        // todo read https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
        val stream : InputStream = context.getContentResolver().openInputStream(uri)
        val reader : BufferedReader = BufferedReader(InputStreamReader(stream))
        val builder : StringBuilder = StringBuilder()

        // read until last line reached
        reader.forEachLine {
            builder.append(it)
            builder.append("\n") }
        stream.close()

        return builder.toString()
    }


    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder : File) {
        val noMediaOutStream : FileOutputStream = FileOutputStream(getNoMediaFile(folder))
        noMediaOutStream.write(0)
    }


    /* Delete nomedia file in given folder */
    fun deleteNoMediaFile(folder: File) {
        getNoMediaFile(folder).delete()
    }


    /* Returns a nomedia file object */
    private fun getNoMediaFile(folder : File) : File {
        return File(folder, ".nomedia")
    }

}