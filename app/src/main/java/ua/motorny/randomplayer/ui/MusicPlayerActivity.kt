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

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat

import android.text.TextUtils

import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
class MusicPlayerActivity : BaseActivity(), MediaBrowserFragment.MediaFragmentListener {

    private var mVoiceSearchParams: Bundle? = null

    val mediaId: String?
        get() {
            val fragment = browseFragment ?: return null
            return fragment.mediaId
        }

    private val browseFragment: MediaBrowserFragment_?
        get() {
            val fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)
            if (fragment != null) return fragment as MediaBrowserFragment_?
            else return null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        setContentView(R.layout.activity_player)

        initializeToolbar()
        initializeFromParams(savedInstanceState, intent)

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val mediaId = mediaId
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.mediaId)
        if (item.isPlayable) {
            supportMediaController.transportControls.playFromMediaId(item.mediaId, null)
        } else if (item.isBrowsable) {
            navigateToBrowser(item.mediaId)
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.mediaId)
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        var title = title
        LogHelper.d(TAG, "Setting toolbar title to ", title.toString())
        if (title == null) {
            title = getString(R.string.app_name)
        }
        setTitle(title.toString())
    }

    override fun onNewIntent(intent: Intent) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent)
        initializeFromParams(null, intent)
        startFullScreenActivityIfNeeded(intent)
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            val fullScreenIntent = Intent(this, FullScreenPlayerActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
                    intent.getParcelableExtra<Parcelable>(EXTRA_CURRENT_MEDIA_DESCRIPTION))
            startActivity(fullScreenIntent)
        }
    }

    protected fun initializeFromParams(savedInstanceState: Bundle?, intent: Intent) {
        var mediaId: String? = null
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.action != null && intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            mVoiceSearchParams = intent.extras
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams!!.getString(SearchManager.QUERY))
        } else {
            if (savedInstanceState != null) {
                // If there is a saved media ID, use it
                mediaId = savedInstanceState.getString(SAVED_MEDIA_ID)
            }
        }
        navigateToBrowser(mediaId)
    }

    private fun navigateToBrowser(mediaId: String?) {
//        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId?.toString())
        var fragment = browseFragment

        if (fragment == null || !TextUtils.equals(fragment.mediaId, mediaId)) {
            fragment = MediaBrowserFragment_()
            fragment.mediaId = mediaId
            val transaction = fragmentManager.beginTransaction()
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right)
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG)
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null)
            }
            transaction.commit()
        }
    }

    override fun onMediaControllerConnected() {
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            val query = mVoiceSearchParams!!.getString(SearchManager.QUERY)
            supportMediaController.transportControls.playFromSearch(query, mVoiceSearchParams)
            mVoiceSearchParams = null
        }
        browseFragment!!.onConnected()
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(MusicPlayerActivity::class)
        private val SAVED_MEDIA_ID = "ua.motorny.randomplayer.MEDIA_ID"
        private val FRAGMENT_TAG = "uamp_list_container"

        val EXTRA_START_FULLSCREEN = "ua.motorny.randomplayer.EXTRA_START_FULLSCREEN"

        /**
         * Optionally used with [.EXTRA_START_FULLSCREEN] to carry a MediaDescription to
         * the [FullScreenPlayerActivity], speeding up the screen rendering
         * while the [android.support.v4.media.session.MediaControllerCompat] is connecting.
         */
        val EXTRA_CURRENT_MEDIA_DESCRIPTION = "ua.motorny.randomplayer.CURRENT_MEDIA_DESCRIPTION"
    }
}


