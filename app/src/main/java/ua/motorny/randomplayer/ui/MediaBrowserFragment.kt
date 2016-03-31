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
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById

import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.MediaIDHelper
import ua.motorny.randomplayer.utils.NetworkHelper

import java.util.ArrayList

@EFragment(R.layout.fragment_media_browser)
open class MediaBrowserFragment : Fragment() {

    @ViewById(R.id.playback_error)
    lateinit protected var mErrorView: View

    @ViewById(R.id.error_message)
    lateinit protected var mErrorMessage: TextView

    @ViewById(R.id.list_view)
    lateinit protected var listView: ListView

    lateinit private var mBrowserAdapter: BrowseAdapter
    private var mMediaId: String? = null
    private var mMediaFragmentListener: MediaFragmentListener? = null

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

    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        private var oldOnline = false
        override fun onReceive(context: Context, intent: Intent) {
            if (mMediaId != null) {
                val isOnline = NetworkHelper.isOnline(context)
                if (isOnline != oldOnline) {
                    oldOnline = isOnline
                    checkForUserVisibleErrors(false)
                    if (isOnline) {
                        mBrowserAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            if (metadata == null) {
                return
            }
            mBrowserAdapter.notifyDataSetChanged()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            super.onPlaybackStateChanged(state)
            checkForUserVisibleErrors(false)
            mBrowserAdapter.notifyDataSetChanged()
        }
    }

    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            try {
                checkForUserVisibleErrors(children.isEmpty())
                mBrowserAdapter.clear()
                for (item in children) {
                    mBrowserAdapter.add(item)
                }
                mBrowserAdapter.notifyDataSetChanged()
            } catch (t: Throwable) {
                //todo add error handling
            }
        }

        override fun onError(id: String) {
            Toast.makeText(activity, R.string.error_loading_media, Toast.LENGTH_LONG).show()
            checkForUserVisibleErrors(true)
        }
    }

    @AfterViews
    fun afterViewInit() {
        mBrowserAdapter = BrowseAdapter(activity)
        listView.adapter = mBrowserAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            checkForUserVisibleErrors(false)
            val item = mBrowserAdapter.getItem(position)
            mMediaFragmentListener!!.onMediaItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        mMediaFragmentListener = activity as MediaFragmentListener
        val mediaBrowser = mMediaFragmentListener?.getMediaBrowser()
        if (mediaBrowser != null && mediaBrowser.isConnected) {
            onConnected()
        }
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

    fun onConnected() {
        if (isDetached) {
            return
        }
        mMediaId = mediaId
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener!!.getMediaBrowser()!!.root
        }
        updateTitle()

        mMediaFragmentListener!!.getMediaBrowser()!!.unsubscribe(mMediaId.toString())
        mMediaFragmentListener!!.getMediaBrowser()!!.subscribe(mMediaId.toString(), mSubscriptionCallback)

        val controller = (activity as FragmentActivity).supportMediaController
        controller?.registerCallback(mMediaControllerCallback)
    }

    private fun checkForUserVisibleErrors(forceError: Boolean) {
        var showError = forceError
        if (!NetworkHelper.isOnline(activity)) {
            mErrorMessage.setText(R.string.error_no_connection)
            showError = true
        } else {
            val controller = (activity as FragmentActivity).supportMediaController
            if (controller != null
                    && controller.metadata != null
                    && controller.playbackState != null
                    && controller.playbackState.state == PlaybackStateCompat.STATE_ERROR
                    && controller.playbackState.errorMessage != null) {
                mErrorMessage.text = controller.playbackState.errorMessage
                showError = true
            } else if (forceError) {
                mErrorMessage.setText(R.string.error_loading_media)
                showError = true
            }
        }
        mErrorView.visibility = if (showError) View.VISIBLE else View.GONE
    }

    private fun updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT == mMediaId) {
            mMediaFragmentListener!!.setToolbarTitle(null)
            return
        }

        val mediaBrowser = mMediaFragmentListener!!.getMediaBrowser()
        mediaBrowser!!.getItem(mMediaId!!, object : MediaBrowserCompat.ItemCallback() {
            override fun onItemLoaded(item: MediaBrowserCompat.MediaItem?) {
                mMediaFragmentListener!!.setToolbarTitle(item!!.description.title)
            }
        })
    }

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
        private val ARG_MEDIA_ID = "media_id"
    }

}
