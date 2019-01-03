/*
 * ImageHelper.kt
 * Implements the ImageHelper object
 * An ImageHelper provides helper methods for image related operations
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import org.y20k.escapepods.R
import java.io.IOException


/*
 * ImageHelper class
 */
object ImageHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(ImageHelper::class.java)

    /* Get scaling factor from display density */
    fun getDensityScalingFactor(context: Context): Float {
        return context.getResources().getDisplayMetrics().density
    }


    /* Get a scaled version of the podcast cover */
    fun getPodcastCover(context: Context, imageUri: Uri, coverSize: Int): Bitmap {
        val size: Int = (coverSize * getDensityScalingFactor(context)).toInt()
        return decodeSampledBitmapFromUri(context, imageUri, size, size)
    }


    /* Extracts color from an image */
    fun getMainColor(context: Context, imageUri: Uri): Int {

        // extract color palette from station image
        val palette: Palette = Palette.from(decodeSampledBitmapFromUri(context, imageUri, 72, 72)).generate()
        // get muted and vibrant swatches
        val vibrantSwatch = palette.getVibrantSwatch()
        val mutedSwatch = palette.getMutedSwatch()

        if (vibrantSwatch != null) {
            // return vibrant color
            val rgb = vibrantSwatch.getRgb()
            return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        } else if (mutedSwatch != null) {
            // return muted color
            val rgb = mutedSwatch.getRgb()
            return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        } else {
            // default return
            return context.resources.getColor(R.color.escapepods_grey_light, null)
        }
    }


    /* Return sampled down image for given Uri */
    private fun decodeSampledBitmapFromUri(context: Context, imageUri: Uri, reqWidth: Int, reqHeight: Int): Bitmap {

        var bitmap: Bitmap? = null
        try {
            // first decode with inJustDecodeBounds=true to check dimensions
            var stream = context.getContentResolver().openInputStream(imageUri)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(stream, null, options)
            stream!!.close()

            // calculate inSampleSize
            options.inSampleSize = calculateSampleParameter(options, reqWidth, reqHeight)

            // decode bitmap with inSampleSize set
            stream = context.getContentResolver().openInputStream(imageUri)
            options.inJustDecodeBounds = false
            bitmap = BitmapFactory.decodeStream(stream, null, options)
            stream!!.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }

        // get default image
        if (bitmap == null) {
            bitmap = ContextCompat.getDrawable(context, R.drawable.ic_default_cover_rss_icon_24dp)!!.toBitmap()
        }

        return bitmap
    }


    /* Calculates parameter needed to scale image down */
    private fun calculateSampleParameter(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // get size of original image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight = height / 2
            val halfWidth = width / 2

            // calculates the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width
            while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

}