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

import android.content.ComponentName
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat

import ua.motorny.randomplayer.MusicService
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

/**
 * Activity used to display details of the currently playing song, along with playback controls
 * and the playing queue.
 */
class TvPlaybackActivity : FragmentActivity() {

    var mMediaBrowser: MediaBrowserCompat? = null
    var mPlaybackFragment: TvPlaybackFragment? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        mMediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java),
                mConnectionCallback, null)

        setContentView(R.layout.tv_playback_controls)

        mPlaybackFragment = supportFragmentManager.findFragmentById(R.id.playback_controls_fragment) as TvPlaybackFragment
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart")
        mMediaBrowser!!.connect()
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")
        if (supportMediaController != null) {
            supportMediaController.unregisterCallback(mMediaControllerCallback)
        }
        mMediaBrowser!!.disconnect()

    }

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            try {
                val mediaController = MediaControllerCompat(
                        this@TvPlaybackActivity, mMediaBrowser!!.sessionToken)
                supportMediaController = mediaController
                mediaController.registerCallback(mMediaControllerCallback)

                val metadata = mediaController.metadata
                if (metadata != null) {
                    mPlaybackFragment!!.updateMetadata(metadata)
                    mPlaybackFragment!!.updatePlaybackState(mediaController.playbackState)
                }
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
            }

        }

        override fun onConnectionFailed() {
            LogHelper.d(TAG, "onConnectionFailed")
        }

        override fun onConnectionSuspended() {
            LogHelper.d(TAG, "onConnectionSuspended")
            supportMediaController.unregisterCallback(mMediaControllerCallback)
            supportMediaController = null
        }
    }

    /**
     * Receive callbacks from the MediaController. Here we update our state such as which queue
     * is being shown, the current title and description and the PlaybackState.
     */
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "onPlaybackStateChanged, state=", state.toString())
            if (mPlaybackFragment == null || state.state == PlaybackStateCompat.STATE_BUFFERING) {
                return
            }
            mPlaybackFragment!!.updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.d(TAG, "onMetadataChanged, title=", metadata!!.description.title.toString())
            if (mPlaybackFragment == null) {
                return
            }
            mPlaybackFragment!!.updateMetadata(metadata)
        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(TvPlaybackActivity::class)
    }
}
