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

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat

import ua.motorny.randomplayer.VoiceSearchParams
import ua.motorny.randomplayer.model.MusicProvider

import java.util.ArrayList

import ua.motorny.randomplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE
import ua.motorny.randomplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH

/**
 * Utility class to help on queue related tasks.
 */
object QueueHelper {

    private val TAG = LogHelper.makeLogTag(QueueHelper::class)

    private val RANDOM_QUEUE_SIZE = 10

    fun getPlayingQueue(mediaId: String, musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {

        // extract the browsing hierarchy from the media ID:
        val hierarchy = MediaIDHelper.getHierarchy(mediaId)

        if (hierarchy.size != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId)
            return object : ArrayList<MediaSessionCompat.QueueItem>() {

            }
        }

        val categoryType = hierarchy[0]
        val categoryValue = hierarchy[1]
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue)

        var tracks: Iterable<MediaMetadataCompat>? = null
        // This sample only supports genre and by_search category types.
        if (categoryType == MEDIA_ID_MUSICS_BY_GENRE) {
            tracks = musicProvider.getMusicsByGenre(categoryValue)
        } else if (categoryType == MEDIA_ID_MUSICS_BY_SEARCH) {
            tracks = musicProvider.searchMusicBySongTitle(categoryValue)
        }

        if (tracks == null) {
            LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId)
            return object : ArrayList<MediaSessionCompat.QueueItem>() {

            }
        }

        return convertToQueue(tracks, hierarchy[0], hierarchy[1])
    }

    fun getPlayingQueueFromSearch(query: String,
                                  queryParams: Bundle, musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
                " params=", queryParams.toString())

        val params = VoiceSearchParams(query, queryParams)

        LogHelper.d(TAG, "VoiceSearchParams: ", params.toString())

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider)
        }

        var result: Iterable<MediaMetadataCompat>? = null
        if (params.isAlbumFocus) {
            result = musicProvider.searchMusicByAlbum(params.album)
        } else if (params.isGenreFocus) {
            result = musicProvider.getMusicsByGenre(params.genre)
        } else if (params.isArtistFocus) {
            result = musicProvider.searchMusicByArtist(params.artist)
        } else if (params.isSongFocus) {
            result = musicProvider.searchMusicBySongTitle(params.song)
        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured || result == null || !result.iterator().hasNext()) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title only. A real world application could search on other fields as well.
            result = musicProvider.searchMusicBySongTitle(query)
        }

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, query)
    }


    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             mediaId: String): Int {
        var index = 0
        for (item in queue) {
            if (mediaId == item.description.mediaId) {
                return index
            }
            index++
        }
        return -1
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             queueId: Long): Int {
        var index = 0
        for (item in queue) {
            if (queueId == item.queueId) {
                return index
            }
            index++
        }
        return -1
    }

    private fun convertToQueue(
            tracks: Iterable<MediaMetadataCompat>, vararg categories: String): List<MediaSessionCompat.QueueItem> {
        val queue = ArrayList<MediaSessionCompat.QueueItem>()
        var count = 0
        for (track in tracks) {

            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            val hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.description.mediaId, *categories)

            val trackCopy = MediaMetadataCompat.Builder(track).putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID).build()

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            val item = MediaSessionCompat.QueueItem(
                    trackCopy.description, count++.toLong())
            queue.add(item)
        }
        return queue

    }

    /**
     * Create a random queue with at most [.RANDOM_QUEUE_SIZE] elements.

     * @param musicProvider the provider used for fetching music.
     * *
     * @return list containing [MediaSessionCompat.QueueItem]'s
     */
    fun getRandomQueue(musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        val result = ArrayList<MediaMetadataCompat>(RANDOM_QUEUE_SIZE)
        val shuffled = musicProvider.shuffledMusic
        for (metadata in shuffled) {
            if (result.size == RANDOM_QUEUE_SIZE) {
                break
            }
            result.add(metadata)
        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size.toString())

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, "random")
    }

    fun isIndexPlayable(index: Int, queue: List<MediaSessionCompat.QueueItem>?): Boolean {
        return queue != null && index >= 0 && index < queue.size
    }
}
