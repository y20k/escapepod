/*
 * FileHelper.kt
 * Implements the FileHelper object
 * A FileHelper provides helper methods for reading and writing files from and to device storage
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.y20k.escapepod.Keys
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.xml.OpmlHelper
import java.io.*
import java.net.URL
import java.text.NumberFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * FileHelper object
 */
object FileHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(FileHelper::class.java)


    /* Return an InputStream for given Uri */
    fun getTextFileStream(context: Context, uri: Uri): InputStream? {
        var stream : InputStream? = null
        try {
            stream = context.contentResolver.openInputStream(uri)
        } catch (e : Exception) {
            e.printStackTrace()
        }
        return stream
    }


    /* Get file size for given Uri */
    fun getFileSize(context: Context, uri: Uri): Long {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null) {
            val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size: Long = cursor.getLong(sizeIndex)
            cursor.close()
            return size
        } else {
            return 0L
        }
    }


    /* Get file name for given Uri */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null) {
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name: String = cursor.getString(nameIndex)
            cursor.close()
            return name
        } else {
            return String()
        }
    }


    /* Get content type for given file */
    fun getContentType(context: Context, uri: Uri): String {
        // get file type from content resolver
        val contentType: String = context.contentResolver.getType(uri) ?: Keys.MIME_TYPE_UNSUPPORTED
//        if (contentType != Keys.MIME_TYPE_UNSUPPORTED && contentType != Keys.MIME_TYPE_OCTET_STREAM) { // todo uncomment if octet stream should be supported
        if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
            // return the found content type
            return contentType
        } else {
            // fallback: try to determine file type based on file extension
            return getContentTypeFromExtension(getFileName(context, uri))
        }
    }


    /* Determine content type based on file extension */
    fun getContentTypeFromExtension(fileName: String): String {
        LogHelper.i(TAG, "Deducing content type from file name: $fileName")
        if (fileName.endsWith("xml", true)) return Keys.MIME_TYPE_XML
        if (fileName.endsWith("rss", true)) return Keys.MIME_TYPE_XML
        if (fileName.endsWith("mp3", true)) return Keys.MIME_TYPE_MP3
        if (fileName.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (fileName.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (fileName.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG
        // default return
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
    fun clearFolder(folder: File?, keep: Int, deleteFolder: Boolean = false) {
        if (folder != null && folder.exists()) {
            val files: Array<File>? = folder.listFiles()
            if (files != null) {
                val fileCount: Int = files.size
                files.sortBy { it.lastModified() }
                for (fileNumber in files.indices) {
                    if (fileNumber < fileCount - keep) {
                        files[fileNumber].delete()
                    }
                }
                if (deleteFolder && keep == 0) {
                    folder.delete()
                }
            }

        }
    }


    /* Creates a copy of a given uri from downloadmanager - goal is to provide stable Uris */
    fun saveCopyOfFile(context: Context, podcastName: String, tempFileUri: Uri, fileType: Int, fileName: String, async: Boolean = false): Uri {
        val targetFile: File = File(context.getExternalFilesDir(determineDestinationFolderPath(fileType, podcastName)), fileName)
        if (targetFile.exists()) targetFile.delete()
        when (async) {
            true -> {
                // copy file async (= fire & forget - no return value needed)
                GlobalScope.launch { saveCopyOfFileSuspended(context, tempFileUri, targetFile.toUri()) }
            }
            false -> {
                // copy file
                copyFile(context, tempFileUri, targetFile.toUri(), deleteOriginal = true)
            }
        }
        return targetFile.toUri()
    }


    /* Creates and save a scaled version of the podcast cover */
    fun saveCover(context: Context, podcastName: String, sourceImageUri: String, size: Int, fileName: String): Uri {
        val coverBitmap: Bitmap = ImageHelper.getScaledPodcastCover(context, sourceImageUri, size)
        val file: File = File(context.getExternalFilesDir(determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcastName)), fileName)
        writeImageFile(context, coverBitmap, file, Bitmap.CompressFormat.JPEG, quality = 75)
        return file.toUri()
    }


    /* Saves podcast collection as JSON text file */
    fun saveCollection(context: Context, collection: Collection, lastSave: Date) {
        LogHelper.v(TAG, "Saving collection - Thread: ${Thread.currentThread().name}")
        val collectionSize: Int = collection.podcasts.size
        // do not override an existing collection with an empty one - except when last podcast is deleted
        if (collectionSize > 0 || PreferencesHelper.loadCollectionSize(context) == 1) {
            // convert to JSON
            val gson: Gson = getCustomGson()
            var json: String = String()
            try {
                json = gson.toJson(collection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (json.isNotBlank()) {
                // save modification date
                PreferencesHelper.saveCollectionModificationDate(context, lastSave)
                // write text file
                writeTextFile(context, json, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
            }
        } else {
            LogHelper.w(TAG, "Not saving collection. Collection is empty.")
        }
    }

    /* Reads podcast collection from storage using GSON */
    fun readCollection(context: Context): Collection {
        LogHelper.v(TAG, "Reading collection - Thread: ${Thread.currentThread().name}")
        // get JSON from text file
        val json: String = readTextFile(context, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
        var collection: Collection = Collection()
        when (json.isNotBlank()) {
            // convert JSON and return as collection
            true -> try {
                collection = getCustomGson().fromJson(json, Collection::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return collection
    }


    /* Appends a message to an existing log - and saves it */
    fun saveLog(context: Context, logMessage: String) {
        var log: String = readTextFile(context, Keys.FOLDER_COLLECTION, Keys.DEBUG_LOG_FILE)
        log = "${log} {$logMessage}"
        writeTextFile(context, log, Keys.FOLDER_COLLECTION, Keys.DEBUG_LOG_FILE)
    }


    /* Deletes the debug log file */
    fun deleteLog(context: Context) {
        val logFile: File = File(context.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.DEBUG_LOG_FILE)
        if (logFile.exists()) {
            logFile.delete()
        }
    }


    /* Checks if enough ( = more than 512mb) free space is available */
    fun enoughFreeSpaceAvailable(context: Context): Boolean {
        val usableSpace: Long = context.getExternalFilesDir(Keys.FOLDER_COLLECTION)?.usableSpace ?: 0L
        LogHelper.e(TAG, "usableSpace: $usableSpace")
        return usableSpace > 512000000L
    }


    /* Suspend function: Wrapper for saveCollection */
    suspend fun saveCollectionSuspended(context: Context, collection: Collection, lastUpdate: Date) {
        return suspendCoroutine { cont ->
            cont.resume(saveCollection(context, collection, lastUpdate))
        }
    }


    /* Suspend function: Wrapper for readCollection */
    suspend fun readCollectionSuspended(context: Context): Collection {
        return suspendCoroutine {cont ->
            cont.resume(readCollection(context))
        }
    }


    /* Suspend function: Exports podcast collection as OPML file - local backup copy */
    suspend fun backupCollectionAsOpmlSuspended(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            LogHelper.v(TAG, "Backing up collection as OPML - Thread: ${Thread.currentThread().name}")
            // create OPML string
            val opmlString: String = OpmlHelper.createOpmlString(collection)
            // save OPML as text file
            cont.resume(writeTextFile(context, opmlString, Keys.FOLDER_COLLECTION, Keys.COLLECTION_OPML_FILE))
        }
    }


    /* Suspend function: Wrapper for copyFile */
    suspend fun saveCopyOfFileSuspended(context: Context, originalFileUri: Uri, targetFileUri: Uri): Boolean {
        return suspendCoroutine { cont ->
            cont.resume(copyFile(context, originalFileUri, targetFileUri, deleteOriginal = true))
        }
    }


    /* Copies file to specified target */
    private fun copyFile(context: Context, originalFileUri: Uri, targetFileUri: Uri, deleteOriginal: Boolean = false): Boolean {
        var success: Boolean = true
        try {
            val inputStream = context.contentResolver.openInputStream(originalFileUri)
            val outputStream = context.contentResolver.openOutputStream(targetFileUri)
            if (outputStream != null && inputStream != null) {
                inputStream.copyTo(outputStream)
            }
        } catch (exception: Exception) {
            LogHelper.e(TAG, "Unable to copy file.")
            success = false
            exception.printStackTrace()
        }
        if (deleteOriginal) {
            try {
                // use contentResolver to handle files of type content://
                context.contentResolver.delete(originalFileUri, null, null)
            } catch (e: Exception) {
                LogHelper.e(TAG, "Unable to delete the original file. Stack trace: $e")
            }
        }
        return success
    }


    /*  Creates a Gson object */
    private fun getCustomGson(): Gson {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        gsonBuilder.excludeFieldsWithoutExposeAnnotation()
        return gsonBuilder.create()
    }


    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder: File?) {
        if (folder != null && folder.exists() && folder.isDirectory) {
            val nomediaFile: File = getNoMediaFile(folder)
            if (!nomediaFile.exists()) {
                val noMediaOutStream: FileOutputStream = FileOutputStream(getNoMediaFile(folder))
                noMediaOutStream.write(0)
            } else {
                LogHelper.v(TAG, ".nomedia file exists already in given folder.")
            }
        } else  {
            LogHelper.w(TAG, "Unable to create .nomedia file. Given folder is not valid.")
        }
    }


    /* Delete nomedia file in given folder */
    fun deleteNoMediaFile(folder: File) {
        getNoMediaFile(folder).delete()
    }


    /* Converts byte value into a human readable format */
    // Source: https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    fun getReadableByteCount(bytes: Long, si: Boolean = true): String {

        // check if Decimal prefix symbol (SI) or Binary prefix symbol (IEC) requested
        val unit: Long = if (si) 1000L else 1024L

        // just return bytes if file size is smaller than requested unit
        if (bytes < unit) return "$bytes B"

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
            return String()
        }
        // readSuspended until last line reached
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


    /* Writes given text to file specified by destinationUri */
    private fun writeTextToUri(context: Context, text: String, destinationUri: Uri) {
        val resolver: ContentResolver = context.contentResolver
        val outputStream: OutputStream? = resolver.openOutputStream(destinationUri)
        outputStream?.write(text.toByteArray(Charsets.UTF_8))
    }



    /* Writes given bitmap as image file to storage */
    private fun writeImageFile(context: Context, bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 75) {
        if (file.exists()) file.delete ()
        try {
            val out = FileOutputStream(file)
            bitmap.compress(format, quality, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /* Checks if an OPML file is in the collection folder */
    fun getOpmlFile(context: Context): File {
        return File(context.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_OPML_FILE)
    }


    /* Checks the size of the collection folder */
    fun getCollectionFolderSize(context: Context): Int {
        val folder: File? = context.getExternalFilesDir(Keys.FOLDER_COLLECTION)
        val files = folder?.listFiles()
        if (folder != null && folder.exists() && folder.isDirectory) {
            return files?.size ?: -1
        } else {
            return -1
        }
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