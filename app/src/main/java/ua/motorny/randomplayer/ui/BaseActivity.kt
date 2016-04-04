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

import android.app.ActivityManager
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

import ua.motorny.randomplayer.MusicService
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.NetworkHelper
import ua.motorny.randomplayer.utils.ResourceHelper

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
abstract class BaseActivity : ActionBarCastActivity(), MediaBrowserProvider {

    lateinit private var mMediaBrowser: MediaBrowserCompat
    private var mControlsFragment: PlaybackControlsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 21) {
            // Since our app icon has the same color as colorPrimary, our entry in the Recent Apps
            // list gets weird. We need to change either the icon or the color
            // of the TaskDescription.
            val taskDesc = ActivityManager.TaskDescription(
                    title.toString(),
                    BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_white),
                    ResourceHelper.getThemeColor(this, R.attr.colorPrimary,
                            android.R.color.darker_gray))
            setTaskDescription(taskDesc)
        }

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mMediaBrowser = MediaBrowserCompat(this, ComponentName(this, MusicService::class.java), mConnectionCallback, null)
    }

    override fun onStart() {
        super.onStart()

        mControlsFragment = fragmentManager.findFragmentById(R.id.fragment_playback_controls) as PlaybackControlsFragment
        if (mControlsFragment == null) {
            throw IllegalStateException("Mising fragment with id 'controls'. Cannot continue.")
        }

        hidePlaybackControls()

        mMediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")
        if (supportMediaController != null) {
            supportMediaController.unregisterCallback(mMediaControllerCallback)
        }
        mMediaBrowser.disconnect()
    }

    override fun getMediaBrowser(): MediaBrowserCompat {
        return mMediaBrowser
    }

    protected open fun onMediaControllerConnected() {
        // empty implementation, can be overridden by clients.
    }

    protected fun showPlaybackControls() {
        LogHelper.d(TAG, "showPlaybackControls")
        if (NetworkHelper.isOnline(this)) {
            fragmentManager.beginTransaction().setCustomAnimations(
                    R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom,
                    R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom).show(mControlsFragment).commit()
        }
    }

    protected fun hidePlaybackControls() {
        LogHelper.d(TAG, "hidePlaybackControls")
        fragmentManager.beginTransaction().hide(mControlsFragment).commit()
    }

    /**
     * Check if the MediaSession is active and in a "playback-able" state
     * (not NONE and not STOPPED).

     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    protected fun shouldShowControls(): Boolean {
        val mediaController = supportMediaController
        if (mediaController == null || mediaController.metadata == null || mediaController.playbackState == null) {
            return false
        }
        when (mediaController.playbackState.state) {
            PlaybackStateCompat.STATE_ERROR, PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> return false
            else -> return true
        }
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(this, token)
        supportMediaController = mediaController
        mediaController.registerCallback(mMediaControllerCallback)

        if (shouldShowControls()) {
            showPlaybackControls()
        } else {
            LogHelper.d(TAG, "connectionCallback.onConnected: " + "hiding controls because metadata is null")
            hidePlaybackControls()
        }

        if (mControlsFragment != null) {
            mControlsFragment!!.onConnected()
        }

        onMediaControllerConnected()
    }

    // Callback that ensures that we are showing the controls
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " + "hiding controls because state is ", state.state.toString())
                hidePlaybackControls()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " + "hiding controls because metadata is null")
                hidePlaybackControls()
            }
        }
    }

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            try {
                connectToSession(mMediaBrowser!!.sessionToken)
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
                hidePlaybackControls()
            }

        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(BaseActivity::class)
    }

}
