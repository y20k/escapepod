/*
 * FileHelper.kt
 * Implements the FileHelper object
 * A FileHelper provides helper methods for reading and writing files from and to device storage
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
import android.database.Cursor
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.OpenableColumns
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.y20k.escapepods.core.Collection
import java.io.*
import java.net.URL
import java.text.NumberFormat
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine


/*
 * FileHelper object
 */
object FileHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(FileHelper::class.java)


    /* Return an InputStream for given Uri */
    fun getTextFileStream(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri)
    }


    /* Get file size for given Uri */
    fun getFileSize(context: Context, uri: Uri): Long {
        val cursor: Cursor = context.contentResolver.query(uri, null, null, null, null)
        val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()
        return cursor.getLong(sizeIndex)
    }


    /* Get file name for given Uri */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor: Cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        return cursor.getString(nameIndex)
    }


    /* Get MIME type for given file */
    fun getFileType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)
    }



    /* Checks if given feed string is XML */
    fun determineMimeType(feedUrl: String): String {
        // FIRST check if NOT an URL
        if (!feedUrl.startsWith("http", true)) return Keys.MIME_TYPE_UNSUPPORTED
        if (!isParsableAsUrl(feedUrl)) return Keys.MIME_TYPE_UNSUPPORTED
        // THEN check for type
        if (feedUrl.endsWith("xml", true)) return Keys.MIME_TYPE_XML
        if (feedUrl.endsWith("rss", true)) return Keys.MIME_TYPE_XML
        if (feedUrl.endsWith("mp3", true)) return Keys.MIME_TYPE_MP3
        if (feedUrl.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (feedUrl.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (feedUrl.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG
        // todo implement a real mime type check
        // https://developer.android.com/reference/java/net/URLConnection#guessContentTypeFromName(java.lang.String)
        return Keys.MIME_TYPE_UNSUPPORTED
    }



    /* Determines a destination folder */
    fun determineDestinationFolderPath(type: Int, podcastName: String): String {
        val folderPath: String
        val subDirectory: String = podcastName.replace("[:/]", "_")
        when (type) {
            Keys.FILE_TYPE_RSS -> folderPath = Keys.FOLDER_TEMP
            Keys.FILE_TYPE_AUDIO -> folderPath = Keys.FOLDER_AUDIO + "/" + subDirectory
            Keys.FILE_TYPE_IMAGE -> folderPath = Keys.FOLDER_IMAGES + "/" + subDirectory
            else -> folderPath = "/"
        }
        return folderPath
    }


    /* Clears given folder - keeps given number of files */
    fun clearFolder(folder: File, keep: Int) {
        if (folder.exists()) {
            val files = folder.listFiles()
            val fileCount: Int = files.size
            files.sortBy { it.lastModified() }
            for (fileNumber in files.indices) {
                if (fileNumber < fileCount - keep) {
                    files[fileNumber].delete()
                }
            }
        }
    }


    /* Suspend function: Saves podcast collection as JSON text file */
    suspend fun saveCollection(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            // set last update
            collection.lastUpdate = Calendar.getInstance().time
            PreferenceManager.getDefaultSharedPreferences(context).edit {putLong(Keys.KEY_LAST_UPDATE_COLLECTION, collection.lastUpdate.time)}
            // convert to JSON
            val gson: Gson = getCustomGson()
            val json: String = gson.toJson(collection)
            // save JSON as text file
            cont.resume(writeTextFile(context, json, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE))
        }
    }



    /* Suspend function: Reads podcast collection from storage using GSON */
    suspend fun readCollection(context: Context): Collection {
        return suspendCoroutine {cont ->
            // get JSON from text file
            val json: String = readTextFile(context, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
            if (json.isEmpty()) {
                // return an empty collection
                cont.resume(Collection())
            } else {
                // convert JSON and return as COLLECTION
                cont.resume(getCustomGson().fromJson(json, Collection::class.java))
            }
        }
    }


    /*  Creates a Gson object */
    private fun getCustomGson(): Gson {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        gsonBuilder.excludeFieldsWithoutExposeAnnotation()
        return gsonBuilder.create()
    }



    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder: File) {
        val noMediaOutStream: FileOutputStream = FileOutputStream(getNoMediaFile(folder))
        noMediaOutStream.write(0)
    }


    /* Delete nomedia file in given folder */
    fun deleteNoMediaFile(folder: File) {
        getNoMediaFile(folder).delete()
    }


    /* Converts byte value into a human readable format */
    // Source: https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    fun getReadableByteCount(bytes: Long, si: Boolean): String {

        // check if Decimal prefix symbol (SI) or Binary prefix symbol (IEC) requested
        val unit: Long = if (si) 1000L else 1024L

        // just return bytes if file size is smaller than requested unit
        if (bytes < unit) return bytes.toString() + " B"

        // calculate exp
        val exp: Int = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()

        // determine prefix symbol
        val prefix: String = ((if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i")

        // calculate result and set number format
        val result: Double = bytes / Math.pow(unit.toDouble(), exp.toDouble())
        val numberFormat = NumberFormat.getNumberInstance()
        numberFormat.maximumFractionDigits = 1

        return numberFormat.format(result) + " " + prefix + "B"
    }


    /* Reads InputStream from file uri and returns it as String */
    private fun readTextFile(context: Context, folder: String, fileName: String): String {
        // todo read https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
        // https://developer.android.com/training/secure-file-sharing/retrieve-info

        // check if file exists
        val file: File = File(context.getExternalFilesDir(folder), fileName)
        if (!file.exists()) {
            return ""
        }
        // read until last line reached
        val stream: InputStream = file.inputStream()
        val reader: BufferedReader = BufferedReader(InputStreamReader(stream))
        val builder: StringBuilder = StringBuilder()
        reader.forEachLine {
            builder.append(it)
            builder.append("\n") }
        stream.close()
        return builder.toString()
    }


    /* Writes given text to file on storage */
    private fun writeTextFile(context: Context, text: String, folder: String, fileName: String) {
        File(context.getExternalFilesDir(folder), fileName).writeText(text)
    }


    /* Returns a nomedia file object */
    private fun getNoMediaFile(folder: File): File {
        return File(folder, ".nomedia")
    }


    /* Tries to parse feed URL string as URL */
    private fun isParsableAsUrl(feedUrl: String): Boolean {
        try {
            URL(feedUrl)
        } catch (e: Exception) {
            return false
        }
        return true
    }

}