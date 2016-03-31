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

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.PlaybackOverlayFragment
import android.support.v17.leanback.app.PlaybackOverlaySupportFragment
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter
import android.support.v17.leanback.widget.Action
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.ClassPresenterSelector
import android.support.v17.leanback.widget.ControlButtonPresenterSelector
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnActionClickedListener
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.PlaybackControlsRow
import android.support.v17.leanback.widget.PlaybackControlsRow.PlayPauseAction
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipNextAction
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipPreviousAction
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.Row
import android.support.v17.leanback.widget.RowPresenter
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils

import ua.motorny.randomplayer.AlbumArtCache
import ua.motorny.randomplayer.utils.LogHelper

/*
 * Show details of the currently playing song, along with playback controls and the playing queue.
 */
class TvPlaybackFragment : PlaybackOverlaySupportFragment() {

    private var mRowsAdapter: ArrayObjectAdapter? = null
    private var mPrimaryActionsAdapter: ArrayObjectAdapter? = null
    lateinit var mPlayPauseAction: PlayPauseAction
    private var mSkipNextAction: SkipNextAction? = null
    private var mSkipPreviousAction: SkipPreviousAction? = null
    private var mPlaybackControlsRow: PlaybackControlsRow? = null
    private var mPlaylistQueue: List<MediaSessionCompat.QueueItem>? = null
    private var mDuration: Int = 0
    private var mHandler: Handler? = null
    private var mRunnable: Runnable? = null

    private var mLastPosition: Long = 0
    private var mLastPositionUpdateTime: Long = 0

    private var mBackgroundManager: BackgroundManager? = null
    private var mListRowAdapter: ArrayObjectAdapter? = null
    private var mListRow: ListRow? = null

    private var mPresenterSelector: ClassPresenterSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.i(TAG, "onCreate")

        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager!!.attach(activity.window)
        mHandler = Handler()
        mListRowAdapter = ArrayObjectAdapter(CardPresenter())
        mPresenterSelector = ClassPresenterSelector()
        mRowsAdapter = ArrayObjectAdapter(mPresenterSelector)

        backgroundType = BACKGROUND_TYPE
        isFadingEnabled = false
    }

    private fun initializePlaybackControls(metadata: MediaMetadataCompat) {
        setupRows()
        addPlaybackControlsRow(metadata)
        adapter = mRowsAdapter
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private fun setupRows() {
        val playbackControlsRowPresenter: PlaybackControlsRowPresenter
        playbackControlsRowPresenter = PlaybackControlsRowPresenter(
                DescriptionPresenter())

        playbackControlsRowPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (activity == null) {
                return@OnActionClickedListener
            }
            val controller = activity.supportMediaController ?: return@OnActionClickedListener
            val controls = controller.transportControls
            if (action.id == mPlayPauseAction.id) {
                if (mPlayPauseAction.index == PlayPauseAction.PLAY) {
                    controls.play()
                } else {
                    controls.pause()
                }
            } else if (action.id == mSkipNextAction!!.id) {
                controls.skipToNext()
                resetPlaybackRow()
            } else if (action.id == mSkipPreviousAction!!.id) {
                controls.skipToPrevious()
                resetPlaybackRow()
            }

            if (action is PlaybackControlsRow.MultiAction) {
                action.nextIndex()
                notifyChanged(action)
            }
        }

        mPresenterSelector!!.addClassPresenter(PlaybackControlsRow::class.java,
                playbackControlsRowPresenter)
    }

    private fun addPlaybackControlsRow(metadata: MediaMetadataCompat) {

        mPlaybackControlsRow = PlaybackControlsRow(MutableMediaMetadataHolder(metadata))
        mRowsAdapter!!.add(mPlaybackControlsRow)

        resetPlaybackRow()

        val presenterSelector = ControlButtonPresenterSelector()
        mPrimaryActionsAdapter = ArrayObjectAdapter(presenterSelector)
        mPlaybackControlsRow!!.primaryActionsAdapter = mPrimaryActionsAdapter

        mPlayPauseAction = PlayPauseAction(activity)
        mSkipNextAction = PlaybackControlsRow.SkipNextAction(activity)
        mSkipPreviousAction = PlaybackControlsRow.SkipPreviousAction(activity)

        mPrimaryActionsAdapter!!.add(mSkipPreviousAction)
        mPrimaryActionsAdapter!!.add(mPlayPauseAction)
        mPrimaryActionsAdapter!!.add(mSkipNextAction)
    }

    private fun equalsQueue(list1: List<MediaSessionCompat.QueueItem>?,
                            list2: List<MediaSessionCompat.QueueItem>?): Boolean {
        if (list1 === list2) {
            return true
        }
        if (list1 == null || list2 == null) {
            return false
        }
        if (list1.size != list2.size) {
            return false
        }
        for (i in list1.indices) {
            if (list1[i].queueId != list2[i].queueId) {
                return false
            }
            if (!TextUtils.equals(list1[i].description.mediaId,
                    list2[i].description.mediaId)) {
                return false
            }
        }
        return true
    }

    protected fun updatePlayListRow(playlistQueue: List<MediaSessionCompat.QueueItem>?) {
        if (equalsQueue(mPlaylistQueue, playlistQueue)) {
            // if the playlist queue hasn't changed, we don't need to update it
            return
        }
        LogHelper.d(TAG, "Updating playlist queue ('now playing')")
        mPlaylistQueue = playlistQueue
        if (playlistQueue == null || playlistQueue.isEmpty()) {
            // Remove the playlist row if no items are in the playlist
            mRowsAdapter!!.remove(mListRow)
            mListRow = null
            return
        }
        mListRowAdapter!!.clear()
        for (i in playlistQueue.indices) {
            val item = playlistQueue[i]
            mListRowAdapter!!.add(item)
        }

        if (mListRow == null) {
            var queueSize = 0
            val controller = activity.supportMediaController
            if (controller != null && controller.queue != null) {
                queueSize = controller.queue.size
            }
            val header = HeaderItem(0, queueSize.toString() + " song(s) in this playlist")

            mPresenterSelector!!.addClassPresenter(ListRow::class.java, ListRowPresenter())

            mListRow = ListRow(header, mListRowAdapter)
            mRowsAdapter!!.add(mListRow)
        } else {
            mRowsAdapter!!.notifyArrayItemRangeChanged(mRowsAdapter!!.indexOf(mListRow), 1)
        }
    }

    private fun notifyChanged(action: Action) {
        val adapter = mPrimaryActionsAdapter
        if (adapter!!.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1)
        }
    }

    private fun resetPlaybackRow() {
        mDuration = 0
        mPlaybackControlsRow!!.totalTime = 0
        mPlaybackControlsRow!!.currentTime = 0
        mRowsAdapter!!.notifyArrayItemRangeChanged(
                mRowsAdapter!!.indexOf(mPlaybackControlsRow), 1)
    }

    private val updatePeriod: Int
        get() {
            if (view == null || mPlaybackControlsRow!!.totalTime <= 0) {
                return DEFAULT_UPDATE_PERIOD
            }
            return Math.max(UPDATE_PERIOD, mPlaybackControlsRow!!.totalTime / view!!.width)
        }

    protected fun startProgressAutomation() {
        if (mHandler != null && mRunnable != null) {
            mHandler!!.removeCallbacks(mRunnable)
        }
        mRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = SystemClock.elapsedRealtime() - mLastPositionUpdateTime
                val currentPosition = Math.min(mDuration, (mLastPosition + elapsedTime).toInt())
                mPlaybackControlsRow!!.currentTime = currentPosition
                mHandler!!.postDelayed(this, updatePeriod.toLong())
            }
        }
        mHandler!!.postDelayed(mRunnable, updatePeriod.toLong())
        isFadingEnabled = true
    }

    protected fun stopProgressAutomation() {
        if (mHandler != null && mRunnable != null) {
            mHandler!!.removeCallbacks(mRunnable)
            isFadingEnabled = false
        }
    }

    private fun updateAlbumArt(artUri: Uri) {
        AlbumArtCache.instance.fetch(artUri.toString(), object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                val artDrawable = BitmapDrawable(
                        this@TvPlaybackFragment.resources, iconImage)
                val bgDrawable = BitmapDrawable(
                        this@TvPlaybackFragment.resources, iconImage)
                mPlaybackControlsRow!!.imageDrawable = artDrawable
                mBackgroundManager!!.drawable = bgDrawable
                mRowsAdapter!!.notifyArrayItemRangeChanged(
                        mRowsAdapter!!.indexOf(mPlaybackControlsRow), 1)
            }
        })
    }

    fun updateMetadata(metadata: MediaMetadataCompat) {
        if (mPlaybackControlsRow == null) {
            initializePlaybackControls(metadata)
        }
        mDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        mPlaybackControlsRow!!.totalTime = mDuration
        (mPlaybackControlsRow!!.item as MutableMediaMetadataHolder).metadata = metadata
        mRowsAdapter!!.notifyArrayItemRangeChanged(
                mRowsAdapter!!.indexOf(mPlaybackControlsRow), 1)
        updateAlbumArt(metadata.description.iconUri!!)
    }

    fun updatePlaybackState(state: PlaybackStateCompat) {
        if (mPlaybackControlsRow == null) {
            // We only update playback state after we get a valid metadata.
            return
        }
        mLastPosition = state.position
        mLastPositionUpdateTime = state.lastPositionUpdateTime
        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startProgressAutomation()
                mPlayPauseAction.index = PlayPauseAction.PAUSE
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                stopProgressAutomation()
                mPlayPauseAction.index = PlayPauseAction.PLAY
            }
        }

        val controller = activity.supportMediaController
        updatePlayListRow(controller.queue)
        mRowsAdapter!!.notifyArrayItemRangeChanged(
                mRowsAdapter!!.indexOf(mPlaybackControlsRow), 1)
    }

    private class DescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(viewHolder: AbstractDetailsDescriptionPresenter.ViewHolder, item: Any) {
            val data = item as MutableMediaMetadataHolder
            viewHolder.title.text = data.metadata.description.title
            viewHolder.subtitle.text = data.metadata.description.subtitle
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {

            if (item is MediaSessionCompat.QueueItem) {
                LogHelper.d(TAG, "item: ", item.toString())
                val controller = activity.supportMediaController
                controller.transportControls.skipToQueueItem(
                        item.queueId)
            }
        }
    }

    private class MutableMediaMetadataHolder(internal var metadata: MediaMetadataCompat)

    companion object {
        private val TAG = LogHelper.makeLogTag(TvPlaybackFragment::class)

        private val BACKGROUND_TYPE = PlaybackOverlayFragment.BG_DARK
        private val DEFAULT_UPDATE_PERIOD = 1000
        private val UPDATE_PERIOD = 16
    }
}
