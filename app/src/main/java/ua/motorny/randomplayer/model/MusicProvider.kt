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

import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper


import java.util.ArrayList
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap



/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
class MusicProvider @JvmOverloads constructor(private val mSource: MusicProviderSource = RemoteJSONSource()) {

    // Categorized caches for music track data:
    private var mMusicListByGenre: ConcurrentMap<String, List<MediaMetadataCompat>>? = null
    private val mMusicListById: ConcurrentMap<String, MutableMediaMetadata>

    private val mFavoriteTracks: MutableSet<String>

    internal enum class State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    @Volatile private var mCurrentState = State.NON_INITIALIZED

    interface Callback {
        fun onMusicCatalogReady(success: Boolean)
    }

    init {
        mMusicListByGenre = ConcurrentHashMap<String, List<MediaMetadataCompat>>()
        mMusicListById = ConcurrentHashMap<String, MutableMediaMetadata>()
        mFavoriteTracks = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    }

    /**
     * Get an iterator over the list of genres

     * @return genres
     */
    val genres: Iterable<String>
        get() {
            if (mCurrentState != State.INITIALIZED) {
                return emptyList()
            }
            return mMusicListByGenre!!.keys
        }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    val shuffledMusic: Iterable<MediaMetadataCompat>
        get() {
            if (mCurrentState != State.INITIALIZED) {
                return emptyList()
            }
            val shuffled = ArrayList<MediaMetadataCompat>(mMusicListById.size)
            for (mutableMetadata in mMusicListById.values) {
                shuffled.add(mutableMetadata.metadata)
            }
            Collections.shuffle(shuffled)
            return shuffled
        }

    /**
     * Get music tracks of the given genre

     */
    fun getMusicsByGenre(genre: String): MutableList<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre!!.containsKey(genre)) {
            return mutableListOf()
        }
        return mMusicListByGenre!![genre] as MutableList<MediaMetadataCompat>
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.

     */
    fun searchMusicBySongTitle(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query)
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.

     */
    fun searchMusicByAlbum(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query)
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.

     */
    fun searchMusicByArtist(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query)
    }

    internal fun searchMusic(metadataField: String, query: String): Iterable<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED) {
            return emptyList()
        }
        val result = ArrayList<MediaMetadataCompat>()
        val tempQuery = query.toLowerCase(Locale.US)
        for (track in mMusicListById.values) {
            if (track.metadata.getString(metadataField).toLowerCase(Locale.US).contains(tempQuery)) {
                result.add(track.metadata)
            }
        }
        return result
    }


    /**
     * Return the MediaMetadataCompat for the given musicID.

     * @param musicId The unique, non-hierarchical music ID.
     */
    fun getMusic(musicId: String): MediaMetadataCompat? {
        return if (mMusicListById.containsKey(musicId)) mMusicListById[musicId]?.metadata else null
    }

    @Synchronized fun updateMusicArt(musicId: String, albumArt: Bitmap, icon: Bitmap) {
        var metadata: MediaMetadataCompat? = getMusic(musicId)
        metadata = MediaMetadataCompat.Builder(metadata)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)
                .build()

        val mutableMetadata = mMusicListById[musicId] ?: throw IllegalStateException("Unexpected error: Inconsistent data structures in " + "MusicProvider")

        mutableMetadata.metadata = metadata
    }

    fun setFavorite(musicId: String, favorite: Boolean) {
        if (favorite) {
            mFavoriteTracks.add(musicId)
        } else {
            mFavoriteTracks.remove(musicId)
        }
    }

    fun isFavorite(musicId: String): Boolean {
        return mFavoriteTracks.contains(musicId)
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    fun retrieveMediaAsync(callback: Callback?) {
        LogHelper.d(TAG, "retrieveMediaAsync called")
        if (mCurrentState == State.INITIALIZED) {
            callback?.onMusicCatalogReady(true)
            return
        }

        // Asynchronously load the music catalog in a separate thread
        object : AsyncTask<Void, Void, State>() {
            override fun doInBackground(vararg params: Void): State {
                retrieveMedia()
                return mCurrentState
            }

            override fun onPostExecute(current: State) {
                callback?.onMusicCatalogReady(current == State.INITIALIZED)
            }
        }.execute()
    }

    @Synchronized private fun buildListsByGenre() {
        val newMusicListByGenre = ConcurrentHashMap<String, List<MediaMetadataCompat>>()

        for (m in mMusicListById.values) {
            val genre = m.metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE)
            var list: MutableList<MediaMetadataCompat>? = newMusicListByGenre[genre] as MutableList<MediaMetadataCompat>?
            if (list == null) {
                list = ArrayList<MediaMetadataCompat>()
                newMusicListByGenre.put(genre, list)
            }
            list.add(m.metadata)
        }
        mMusicListByGenre = newMusicListByGenre
    }

    @Synchronized private fun retrieveMedia() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING

                val tracks = mSource.iterator()
                while (tracks.hasNext()) {
                    val item = tracks.next()
                    val musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                    mMusicListById.put(musicId, MutableMediaMetadata(musicId, item))
                }
                buildListsByGenre()
                mCurrentState = State.INITIALIZED
            }
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED
            }
        }
    }


    fun getChildren(mediaId: String, resources: Resources): List<MediaBrowserCompat.MediaItem> {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems
        }

        if (MediaIDHelper.MEDIA_ID_ROOT == mediaId) {
            mediaItems.add(createBrowsableMediaItemForRoot(resources))

        } else if (MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE == mediaId) {
            for (genre in genres) {
                mediaItems.add(createBrowsableMediaItemForGenre(genre, resources))
            }

        } else if (mediaId.startsWith(MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE)) {
            val genre = MediaIDHelper.getHierarchy(mediaId)[1]
            for (metadata in getMusicsByGenre(genre)) {
                mediaItems.add(createMediaItem(metadata))
            }

        } else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId)
        }
        return mediaItems
    }

    private fun createBrowsableMediaItemForRoot(resources: Resources): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder().setMediaId(MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE).setTitle(resources.getString(R.string.browse_genres)).setSubtitle(resources.getString(R.string.browse_genre_subtitle)).setIconUri(Uri.parse("android.resource://" + "ua.motorny.randomplayer/drawable/ic_by_genre")).build()
        return MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createBrowsableMediaItemForGenre(genre: String,
                                                 resources: Resources): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder().setMediaId(MediaIDHelper.createMediaID(null, MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE, genre)).setTitle(genre).setSubtitle(resources.getString(
                R.string.browse_musics_by_genre_subtitle, genre)).build()
        return MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createMediaItem(metadata: MediaMetadataCompat): MediaBrowserCompat.MediaItem {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        val genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE)
        val hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.description.mediaId, MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE, genre)
        val copy = MediaMetadataCompat.Builder(metadata).putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID).build()
        return MediaBrowserCompat.MediaItem(copy.description,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)

    }

    companion object {

        private val TAG = LogHelper.makeLogTag(MusicProvider::class)
    }

}
