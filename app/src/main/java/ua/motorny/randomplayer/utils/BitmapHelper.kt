/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ua.motorny.randomplayer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object BitmapHelper {
    private val TAG = LogHelper.makeLogTag(BitmapHelper::class)

    // Max read limit that we allow our input stream to mark/reset.
    private val MAX_READ_LIMIT_PER_IMG = 1024 * 1024

    fun scaleBitmap(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scaleFactor = Math.min(
                maxWidth.toDouble() / src.width, maxHeight.toDouble() / src.height)
        return Bitmap.createScaledBitmap(src,
                (src.width * scaleFactor).toInt(), (src.height * scaleFactor).toInt(), false)
    }

    fun scaleBitmap(scaleFactor: Int, `is`: InputStream): Bitmap {
        // Get the dimensions of the bitmap
        val bmOptions = BitmapFactory.Options()

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor

        return BitmapFactory.decodeStream(`is`, null, bmOptions)
    }

    fun findScaleFactor(targetW: Int, targetH: Int, `is`: InputStream): Int {
        // Get the dimensions of the bitmap
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeStream(`is`, null, bmOptions)
        val actualW = bmOptions.outWidth
        val actualH = bmOptions.outHeight

        // Determine how much to scale down the image
        return Math.min(actualW / targetW, actualH / targetH)
    }

    @SuppressWarnings("SameParameterValue")
    @Throws(IOException::class)
    fun fetchAndRescaleBitmap(uri: String, width: Int, height: Int): Bitmap {
        val url = URL(uri)
        var `is`: BufferedInputStream? = null
        try {
            val urlConnection = url.openConnection() as HttpURLConnection
            `is` = BufferedInputStream(urlConnection.inputStream)
            `is`.mark(MAX_READ_LIMIT_PER_IMG)
            val scaleFactor = findScaleFactor(width, height, `is`)
            LogHelper.d(TAG, "Scaling bitmap ", uri, " by factor ", scaleFactor.toString(), " to support ",
                    width.toString(), "x", height.toString(), "requested dimension")
            `is`.reset()
            return scaleBitmap(scaleFactor, `is`)
        } finally {
            if (`is` != null) {
                `is`.close()
            }
        }
    }
}
