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
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat

import ua.motorny.randomplayer.MusicService
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

/**
 * Main activity for the Android TV user interface.
 */
class TvBrowseActivity : FragmentActivity(), TvBrowseFragment.MediaFragmentListener {

    override var mediaBrowser: MediaBrowserCompat? = null

    private var mMediaId: String? = null
    private var mBrowseTitle: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        setContentView(R.layout.tv_activity_player)

        mediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java),
                mConnectionCallback, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mMediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mMediaId)
            outState.putString(BROWSE_TITLE, mBrowseTitle)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart")
        mediaBrowser!!.connect()
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")
        if (mediaBrowser != null) {
            mediaBrowser!!.disconnect()
        }
    }

    override fun onSearchRequested(): Boolean {
        startActivity(Intent(this, TvBrowseActivity::class.java))
        return true
    }

    protected fun navigateToBrowser(mediaId: String?) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId!!)
        val fragment = supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as TvBrowseFragment
        fragment.initializeWithMediaId(mediaId)
        mMediaId = mediaId
        fragment.title = mBrowseTitle
    }

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected: session token ",
                    mediaBrowser!!.sessionToken.toString())
            try {
                val mediaController = MediaControllerCompat(
                        this@TvBrowseActivity, mediaBrowser!!.sessionToken)
                supportMediaController = mediaController
                navigateToBrowser(mMediaId)
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
            }

        }

        override fun onConnectionFailed() {
            LogHelper.d(TAG, "onConnectionFailed")
        }

        override fun onConnectionSuspended() {
            LogHelper.d(TAG, "onConnectionSuspended")
            supportMediaController = null
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(TvBrowseActivity::class)
        val SAVED_MEDIA_ID = "ua.motorny.randomplayer.MEDIA_ID"
        val BROWSE_TITLE = "ua.motorny.randomplayer.BROWSE_TITLE"
    }
}
