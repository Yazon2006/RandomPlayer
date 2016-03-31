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
package ua.motorny.randomplayer.ui.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat

import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

import java.util.HashSet

/**
 * Browse media categories and current playing queue.
 *
 *
 * WARNING: This sample's UI is implemented for a specific MediaBrowser tree structure.
 * It expects a tree that is three levels deep under root:
 * - level 0: root
 * - level 1: categories of categories (like "by genre", "by artist", "playlists")
 * - level 2: song categories (like "by genre -> Rock", "by  artist -> artistname" or
 * "playlists -> my favorite music")
 * - level 3: the actual music
 *
 *
 * If you are reusing this TV code, make sure you adapt it to your MediaBrowser structure, in case
 * it is not the same.
 *
 *
 *
 *
 * It uses a [android.support.v4.media.MediaBrowserCompat] to connect to the [ua.motorny.randomplayer.MusicService].
 * Once connected, the fragment subscribes to get the children of level 1 and then, for each
 * children, it adds a ListRow and subscribes for its children, which, when received, are
 * added to the ListRow. These items (like "Rock"), when clicked, will open a
 * TvVerticalGridActivity that lists all songs of the specified category on a grid-like UI.
 *
 *
 * This fragment also shows the MediaSession queue ("now playing" list), in case there is
 * something playing.
 */
class TvBrowseFragment : BrowseSupportFragment() {

    private var mRowsAdapter: ArrayObjectAdapter? = null
    private var mListRowAdapter: ArrayObjectAdapter? = null
    private var mMediaFragmentListener: MediaFragmentListener? = null

    private var mMediaBrowser: MediaBrowserCompat? = null
    private var mSubscribedMediaIds: HashSet<String>? = null

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata != null) {
                val mediaController = activity.supportMediaController
                val activeQueueId: Long
                if (mediaController.playbackState == null) {
                    activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                } else {
                    activeQueueId = mediaController.playbackState.activeQueueItemId
                }
                updateNowPlayingList(mediaController.queue, activeQueueId)
                mRowsAdapter!!.notifyArrayItemRangeChanged(0, mRowsAdapter!!.size())
            }
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            // queue has changed somehow
            val mediaController = activity.supportMediaController

            val activeQueueId: Long
            if (mediaController.playbackState == null) {
                activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            } else {
                activeQueueId = mediaController.playbackState.activeQueueItemId
            }
            updateNowPlayingList(queue!!, activeQueueId)
            mRowsAdapter!!.notifyArrayItemRangeChanged(0, mRowsAdapter!!.size())
        }
    }

    private fun updateNowPlayingList(queue: MutableList<MediaSessionCompat.QueueItem>, activeQueueId: Long) {
        mListRowAdapter!!.clear()
        if (activeQueueId != MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val queueItem = iterator.next()
                if (activeQueueId != queueItem.queueId) {
                    iterator.remove()
                } else {
                    break
                }
            }
        }
        mListRowAdapter!!.addAll(0, queue)
    }

    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {

        override fun onChildrenLoaded(parentId: String,
                                      children: List<MediaBrowserCompat.MediaItem>) {

            mRowsAdapter!!.clear()
            val cardPresenter = CardPresenter()

            for (i in children.indices) {
                val item = children[i]
                val title = item.description.title as String?
                val header = HeaderItem(i.toLong(), title)
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                mRowsAdapter!!.add(ListRow(header, listRowAdapter))

                if (item.isPlayable) {
                    listRowAdapter.add(item)
                } else if (item.isBrowsable) {
                    subscribeToMediaId(item.mediaId,
                            RowSubscriptionCallback(listRowAdapter))
                } else {
                    LogHelper.e(TAG, "Item should be playable or browsable.")
                }
            }

            val mediaController = activity.supportMediaController

            if (mediaController.queue != null && !mediaController.queue.isEmpty()) {
                // add Now Playing queue to Browse Home
                val header = HeaderItem(
                        children.size.toLong(), getString(R.string.now_playing))
                mListRowAdapter = ArrayObjectAdapter(cardPresenter)
                mRowsAdapter!!.add(ListRow(header, mListRowAdapter))
                val activeQueueId: Long
                if (mediaController.playbackState == null) {
                    activeQueueId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                } else {
                    activeQueueId = mediaController.playbackState.activeQueueItemId
                }
                updateNowPlayingList(mediaController.queue, activeQueueId)
            }

            mRowsAdapter!!.notifyArrayItemRangeChanged(0, children.size)
        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "SubscriptionCallback subscription onError, id=" + id)
        }
    }

    /**
     * This callback fills content for a single Row in the BrowseFragment.
     */
    private inner class RowSubscriptionCallback(private val mListRowAdapter: ArrayObjectAdapter) : MediaBrowserCompat.SubscriptionCallback() {

        override fun onChildrenLoaded(parentId: String,
                                      children: List<MediaBrowserCompat.MediaItem>) {
            mListRowAdapter.clear()
            for (item in children) {
                mListRowAdapter.add(item)
            }
            mListRowAdapter.notifyArrayItemRangeChanged(0, children.size)
        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "RowSubscriptionCallback subscription onError, id=", id)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        LogHelper.d(TAG, "onActivityCreated")

        mSubscribedMediaIds = HashSet<String>()

        // set search icon color
        searchAffordanceColor = resources.getColor(R.color.tv_search_button)

        loadRows()
        setupEventListeners()
    }

    private fun loadRows() {
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = mRowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { viewHolder, o, viewHolder2, row ->
            if (o is MediaBrowserCompat.MediaItem) {
                if (o.isPlayable) {
                    LogHelper.w(TAG, "Ignoring click on PLAYABLE MediaItem in" + "TvBrowseFragment. mediaId=", o.mediaId)
                    return@OnItemViewClickedListener
                }
                val intent = Intent(activity, TvVerticalGridActivity::class.java)
                intent.putExtra(TvBrowseActivity.SAVED_MEDIA_ID, o.mediaId)
                intent.putExtra(TvBrowseActivity.BROWSE_TITLE,
                        o.description.title)
                startActivity(intent)

            } else if (o is MediaSessionCompat.QueueItem) {
                val mediaController = activity.supportMediaController
                mediaController.transportControls.skipToQueueItem(o.queueId)
                val intent = Intent(activity, TvPlaybackActivity::class.java)
                startActivity(intent)
            }
        }

        setOnSearchClickedListener {
            LogHelper.d(TAG, "In-app search")
            // TODO: implement in-app search
            val intent = Intent(activity, TvBrowseActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        try {
            mMediaFragmentListener = activity as MediaFragmentListener?
        } catch (ex: ClassCastException) {
            LogHelper.e(TAG, "TVBrowseFragment can only be attached to an activity that " + "implements MediaFragmentListener", ex.toString())
        }

    }

    override fun onStop() {
        super.onStop()
        if (mMediaBrowser != null && mMediaBrowser!!.isConnected) {
            for (mediaId in mSubscribedMediaIds!!) {
                mMediaBrowser!!.unsubscribe(mediaId)
            }
            mSubscribedMediaIds!!.clear()
        }
        val mediaController = activity.supportMediaController
        mediaController?.unregisterCallback(mMediaControllerCallback)
    }

    override fun onDetach() {
        super.onDetach()
        mMediaFragmentListener = null
    }

    fun initializeWithMediaId(mediaId: String?) {
        var mediaId = mediaId
        LogHelper.d(TAG, "subscribeToData")
        // fetch browsing information to fill the listview:
        mMediaBrowser = mMediaFragmentListener!!.mediaBrowser

        if (mediaId == null) {
            mediaId = mMediaBrowser!!.root
        }

        subscribeToMediaId(mediaId, mSubscriptionCallback)

        // Add MediaController callback so we can redraw the list when metadata changes:
        val mediaController = activity.supportMediaController
        mediaController?.registerCallback(mMediaControllerCallback)
    }

    private fun subscribeToMediaId(mediaId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        if (mSubscribedMediaIds!!.contains(mediaId)) {
            mMediaBrowser!!.unsubscribe(mediaId)
        } else {
            mSubscribedMediaIds!!.add(mediaId)
        }
        mMediaBrowser!!.subscribe(mediaId, callback)
    }

    interface MediaFragmentListener {
        var mediaBrowser: MediaBrowserCompat?
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(TvBrowseFragment::class)
    }

}

