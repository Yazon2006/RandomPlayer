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
import android.support.v17.leanback.app.VerticalGridSupportFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.Presenter
import android.support.v17.leanback.widget.Row
import android.support.v17.leanback.widget.RowPresenter
import android.support.v17.leanback.widget.VerticalGridPresenter
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.text.TextUtils

import ua.motorny.randomplayer.utils.LogHelper

/*
 * VerticalGridFragment shows a grid of music songs
 */
class TvVerticalGridFragment : VerticalGridSupportFragment() {

    private var mAdapter: ArrayObjectAdapter? = null
    private var mMediaId: String? = null
    private var mMediaFragmentListener: MediaFragmentListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "onCreate")

        setupFragment()
    }

    private fun setupFragment() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)

        mAdapter = ArrayObjectAdapter(CardPresenter())
        adapter = mAdapter
        onItemViewClickedListener = ItemViewClickedListener()
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        mMediaFragmentListener = activity as MediaFragmentListener?
    }

    fun setMediaId(mediaId: String?) {
        var mediaId = mediaId
        LogHelper.d(TAG, "setMediaId: ", mediaId.toString())
        if (TextUtils.equals(mMediaId, mediaId)) {
            return
        }
        val mediaBrowser = mMediaFragmentListener!!.mediaBrowser

        // First, unsubscribe from old mediaId:
        if (mMediaId != null) {
            mediaBrowser!!.unsubscribe(mMediaId!!)
        }
        if (mediaId == null) {
            mediaId = mediaBrowser!!.root
        }
        mMediaId = mediaId
        mediaBrowser!!.subscribe(mMediaId!!, mSubscriptionCallback)
    }

    override fun onStop() {
        super.onStop()
        val mediaBrowser = mMediaFragmentListener!!.mediaBrowser
        if (mediaBrowser != null && mediaBrowser.isConnected && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId!!)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mMediaFragmentListener = null
    }

    interface MediaFragmentListener {
        val mediaBrowser: MediaBrowserCompat?
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {

            val controller = activity.supportMediaController ?: return
            val controls = controller.transportControls
            controls.playFromMediaId((item as MediaBrowserCompat.MediaItem).mediaId, null)

            val intent = Intent(activity, TvPlaybackActivity::class.java)
            val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity,
                    (itemViewHolder.view as ImageCardView).mainImageView,
                    TvVerticalGridActivity.SHARED_ELEMENT_NAME).toBundle()

            activity.startActivity(intent, bundle)
        }
    }

    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            mAdapter!!.clear()
            for (i in children.indices) {
                val item = children[i]
                if (!item.isPlayable) {
                    LogHelper.e(TAG, "Cannot show non-playable items. Ignoring ", item.mediaId)
                } else {
                    mAdapter!!.add(item)
                }
            }
            mAdapter!!.notifyArrayItemRangeChanged(0, children.size)
        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "browse fragment subscription onError, id=", id)
        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(TvVerticalGridFragment::class)

        private val NUM_COLUMNS = 5
    }
}
