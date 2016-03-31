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
package ua.motorny.randomplayer.ui

import android.app.Activity
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper
import ua.motorny.randomplayer.utils.NetworkHelper

import java.util.ArrayList

/**
 * A Fragment that lists all the various browsable queues available
 * from a [android.service.media.MediaBrowserService].
 *
 *
 * It uses a [MediaBrowserCompat] to connect to the [ua.motorny.randomplayer.MusicService].
 * Once connected, the fragment subscribes to get all the children.
 * All [MediaBrowserCompat.MediaItem]'s that can be browsed are shown in a ListView.
 */
class MediaBrowserFragment : Fragment() {

    private var mBrowserAdapter: BrowseAdapter? = null
    private var mMediaId: String? = null
    private var mMediaFragmentListener : MediaFragmentListener? = null
    private var mErrorView: View? = null
    private var mErrorMessage: TextView? = null
    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        private var oldOnline = false
        override fun onReceive(context: Context, intent: Intent) {
            // We don't care about network changes while this fragment is not associated
            // with a media ID (for example, while it is being initialized)
            if (mMediaId != null) {
                val isOnline = NetworkHelper.isOnline(context)
                if (isOnline != oldOnline) {
                    oldOnline = isOnline
                    checkForUserVisibleErrors(false)
                    if (isOnline) {
                        mBrowserAdapter!!.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            if (metadata == null) {
                return
            }
            LogHelper.d(TAG, "Received metadata change to media ",
                    metadata.description.mediaId.toString())
            mBrowserAdapter!!.notifyDataSetChanged()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            super.onPlaybackStateChanged(state)
            LogHelper.d(TAG, "Received state change: ", state.toString())
            checkForUserVisibleErrors(false)
            mBrowserAdapter!!.notifyDataSetChanged()
        }
    }

    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String,
                                      children: List<MediaBrowserCompat.MediaItem>) {
            try {
                LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                        "  count=" + children.size)
                checkForUserVisibleErrors(children.isEmpty())
                mBrowserAdapter!!.clear()
                for (item in children) {
                    mBrowserAdapter!!.add(item)
                }
                mBrowserAdapter!!.notifyDataSetChanged()
            } catch (t: Throwable) {
                LogHelper.e(TAG, "Error on childrenloaded", t.toString())
            }
        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "browse fragment subscription onError, id=" + id)
            Toast.makeText(activity, R.string.error_loading_media, Toast.LENGTH_LONG).show()
            checkForUserVisibleErrors(true)
        }
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = activity as MediaFragmentListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        LogHelper.d(TAG, "fragment.onCreateView")
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)

        mErrorView = rootView.findViewById(R.id.playback_error)
        mErrorMessage = mErrorView!!.findViewById(R.id.error_message) as TextView

        mBrowserAdapter = BrowseAdapter(activity)

        val listView = rootView.findViewById(R.id.list_view) as ListView
        listView.adapter = mBrowserAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            checkForUserVisibleErrors(false)
            val item = mBrowserAdapter!!.getItem(position)
            mMediaFragmentListener!!.onMediaItemSelected(item)
        }

        return rootView
    }

    override fun onStart() {
        super.onStart()

        // fetch browsing information to fill the listview:
        val mediaBrowser = mMediaFragmentListener?.getMediaBrowser()

//        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId?.toString()!!, " onConnected=" + mediaBrowser?.isConnected.toString())

        if (mediaBrowser != null && mediaBrowser.isConnected) {
            onConnected()
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.activity.registerReceiver(mConnectivityChangeReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onStop() {
        super.onStop()
        val mediaBrowser = mMediaFragmentListener!!.getMediaBrowser()
        if (mediaBrowser != null && mediaBrowser.isConnected && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId.toString())
        }
        val controller = (activity as FragmentActivity).supportMediaController
        controller?.unregisterCallback(mMediaControllerCallback)
        this.activity.unregisterReceiver(mConnectivityChangeReceiver)
    }

    override fun onDetach() {
        super.onDetach()
        mMediaFragmentListener = null
    }

    var mediaId: String?
        get() {
            val args = arguments
            if (args != null) {
                return args.getString(ARG_MEDIA_ID)
            }
            return null
        }
        set(mediaId) {
            val args = Bundle(1)
            args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId)
            arguments = args
        }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    fun onConnected() {
        if (isDetached) {
            return
        }
        mMediaId = mediaId
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener!!.getMediaBrowser()!!.root
        }
        updateTitle()

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener!!.getMediaBrowser()!!.unsubscribe(mMediaId.toString())

        mMediaFragmentListener!!.getMediaBrowser()!!.subscribe(mMediaId.toString(), mSubscriptionCallback)

        // Add MediaController callback so we can redraw the list when metadata changes:
        val controller = (activity as FragmentActivity).supportMediaController
        controller?.registerCallback(mMediaControllerCallback)
    }

    private fun checkForUserVisibleErrors(forceError: Boolean) {
        var showError = forceError
        // If offline, message is about the lack of connectivity:
        if (!NetworkHelper.isOnline(activity)) {
            mErrorMessage!!.setText(R.string.error_no_connection)
            showError = true
        } else {
            // otherwise, if state is ERROR and metadata!=null, use playback state error message:
            val controller = (activity as FragmentActivity).supportMediaController
            if (controller != null
                    && controller.metadata != null
                    && controller.playbackState != null
                    && controller.playbackState.state == PlaybackStateCompat.STATE_ERROR
                    && controller.playbackState.errorMessage != null) {
                mErrorMessage!!.text = controller.playbackState.errorMessage
                showError = true
            } else if (forceError) {
                // Finally, if the caller requested to show error, show a generic message:
                mErrorMessage!!.setText(R.string.error_loading_media)
                showError = true
            }
        }
        mErrorView!!.visibility = if (showError) View.VISIBLE else View.GONE
        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError.toString(),
                " showError=", showError.toString(),
                " isOnline=", NetworkHelper.isOnline(activity).toString())
    }

    private fun updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT == mMediaId) {
            mMediaFragmentListener!!.setToolbarTitle(null)
            return
        }

        val mediaBrowser = mMediaFragmentListener!!.getMediaBrowser()
        mediaBrowser!!.getItem(mMediaId!!, object : MediaBrowserCompat.ItemCallback() {
            override fun onItemLoaded(item: MediaBrowserCompat.MediaItem?) {
                mMediaFragmentListener!!.setToolbarTitle(
                        item!!.description.title)
            }
        })
    }

    // An adapter for showing the list of browsed MediaItem's
    private class BrowseAdapter(context: Activity) : ArrayAdapter<MediaBrowserCompat.MediaItem>(context, R.layout.media_list_item, ArrayList<MediaBrowserCompat.MediaItem>()) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)
            var itemState = MediaItemViewHolder.STATE_NONE
            if (item.isPlayable) {
                itemState = MediaItemViewHolder.STATE_PLAYABLE
                val controller = (context as FragmentActivity).supportMediaController
                if (controller != null && controller.metadata != null) {
                    val currentPlaying = controller.metadata.description.mediaId
                    val musicId = MediaIDHelper.extractMusicIDFromMediaID(
                            item.description.mediaId!!)
                    if (currentPlaying != null && currentPlaying == musicId) {
                        val pbState = controller.playbackState
                        if (pbState == null || pbState.state == PlaybackStateCompat.STATE_ERROR) {
                            itemState = MediaItemViewHolder.STATE_NONE
                        } else if (pbState.state == PlaybackStateCompat.STATE_PLAYING) {
                            itemState = MediaItemViewHolder.STATE_PLAYING
                        } else {
                            itemState = MediaItemViewHolder.STATE_PAUSED
                        }
                    }
                }
            }
            return MediaItemViewHolder.setupView(context as Activity, convertView, parent,
                    item.description, itemState)
        }
    }

    interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem)
        fun setToolbarTitle(title: CharSequence?)
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MediaBrowserFragment::class)
        private val ARG_MEDIA_ID = "media_id"
    }

}
