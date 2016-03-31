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

package ua.motorny.randomplayer.model

import android.support.v4.media.MediaMetadataCompat
import android.util.Log

import ua.motorny.randomplayer.utils.LogHelper

import org.json.JSONException
import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.ArrayList

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
class RemoteJSONSource : MusicProviderSource {

    override fun iterator(): Iterator<MediaMetadataCompat> {
        try {
            val slashPos = CATALOG_URL.lastIndexOf('/')
            val path = CATALOG_URL.substring(0, slashPos + 1)
            val jsonObj = fetchJSONFromUrl(CATALOG_URL)
            val tracks = ArrayList<MediaMetadataCompat>()
            if (jsonObj != null) {
                val jsonTracks = jsonObj.getJSONArray(JSON_MUSIC)

                if (jsonTracks != null) {
                    for (j in 0..jsonTracks.length() - 1) {
                        tracks.add(buildFromJSON(jsonTracks.getJSONObject(j), path))
                    }
                }
            }
            return tracks.iterator()
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Could not retrieve music list")
            throw RuntimeException("Could not retrieve music list", e)
        }

    }

    @Throws(JSONException::class)
    private fun buildFromJSON(json: JSONObject, basePath: String): MediaMetadataCompat {
        val title = json.getString(JSON_TITLE)
        val album = json.getString(JSON_ALBUM)
        val artist = json.getString(JSON_ARTIST)
        val genre = json.getString(JSON_GENRE)
        var source = json.getString(JSON_SOURCE)
        var iconUrl = json.getString(JSON_IMAGE)
        val trackNumber = json.getInt(JSON_TRACK_NUMBER)
        val totalTrackCount = json.getInt(JSON_TOTAL_TRACK_COUNT)
        val duration = json.getInt(JSON_DURATION) * 1000 // ms

        LogHelper.d(TAG, "Found music track: ", json.toString())

        // Media is stored relative to JSON file
        if (!source.startsWith("http")) {
            source = basePath + source
        }
        if (!iconUrl.startsWith("http")) {
            iconUrl = basePath + iconUrl
        }
        // Since we don't have a unique ID in the server, we fake one using the hashcode of
        // the music source. In a real world app, this could come from the server.
        val id = source.hashCode().toString()

        // Adding the music source to the MediaMetadata (and consequently using it in the
        // mediaSession.setMetadata) is not a good idea for a real world music app, because
        // the session metadata can be accessed by notification listeners. This is done in this
        // sample for convenience only.
        //noinspection ResourceType
        return MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id).putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source).putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist).putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong()).putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre).putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl).putString(MediaMetadataCompat.METADATA_KEY_TITLE, title).putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong()).putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount.toLong()).build()
    }

    /**
     * Download a JSON file from a server, parse the content and return the JSON
     * object.

     * @return result JSONObject containing the parsed representation.
     */
    @Throws(JSONException::class)
    private fun fetchJSONFromUrl(urlString: String): JSONObject? {
        var reader: BufferedReader? = null
        try {
            val urlConnection = URL(urlString).openConnection()
            reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//            var strings = reader.readLines()
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            Log.d("TEST", sb.toString())
            return JSONObject(sb.toString());
        } catch (e: JSONException) {
            throw e
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to parse the json for media list", e.toString())
            return null
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    // ignore
                }

            }
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(RemoteJSONSource::class)

        protected val CATALOG_URL = "http://storage.googleapis.com/automotive-media/music.json"

        private val JSON_MUSIC = "music"
        private val JSON_TITLE = "title"
        private val JSON_ALBUM = "album"
        private val JSON_ARTIST = "artist"
        private val JSON_GENRE = "genre"
        private val JSON_SOURCE = "source"
        private val JSON_IMAGE = "image"
        private val JSON_TRACK_NUMBER = "trackNumber"
        private val JSON_TOTAL_TRACK_COUNT = "totalTrackCount"
        private val JSON_DURATION = "duration"
    }
}
