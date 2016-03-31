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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView

import ua.motorny.randomplayer.AlbumArtCache
import ua.motorny.randomplayer.MusicService
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import android.view.View.INVISIBLE
import android.view.View.VISIBLE

/**
 * A full screen player that shows the current playing music with a background image
 * depicting the album art. The activity also has controls to seek/pause/play the audio.
 */
class FullScreenPlayerActivity : ActionBarCastActivity() {

    private var mSkipPrev: ImageView? = null
    private var mSkipNext: ImageView? = null
    private var mPlayPause: ImageView? = null
    private var mStart: TextView? = null
    private var mEnd: TextView? = null
    private var mSeekbar: SeekBar? = null
    private var mLine1: TextView? = null
    private var mLine2: TextView? = null
    private var mLine3: TextView? = null
    private var mLoading: ProgressBar? = null
    private var mControllers: View? = null
    private var mPauseDrawable: Drawable? = null
    private var mPlayDrawable: Drawable? = null
    private var mBackgroundImage: ImageView? = null

    private var mCurrentArtUrl: String? = null
    private val mHandler = Handler()
    private var mMediaBrowser: MediaBrowserCompat? = null

    private val mUpdateProgressTask = Runnable { updateProgress() }

    private val mExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var mScheduleFuture: ScheduledFuture<*>? = null
    private var mLastPlaybackState: PlaybackStateCompat? = null

    private val mCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "onPlaybackstate changed", state.toString())
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata != null) {
                updateMediaDescription(metadata.description)
                updateDuration(metadata)
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
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_player)
        initializeToolbar()
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = ""
        }

        mBackgroundImage = findViewById(R.id.background_image) as ImageView
        mPauseDrawable = ContextCompat.getDrawable(this, R.drawable.uamp_ic_pause_white_48dp)
        mPlayDrawable = ContextCompat.getDrawable(this, R.drawable.uamp_ic_play_arrow_white_48dp)
        mPlayPause = findViewById(R.id.play_pause) as ImageView
        mSkipNext = findViewById(R.id.next) as ImageView
        mSkipPrev = findViewById(R.id.prev) as ImageView
        mStart = findViewById(R.id.startText) as TextView
        mEnd = findViewById(R.id.endText) as TextView
        mSeekbar = findViewById(R.id.seekBar1) as SeekBar
        mLine1 = findViewById(R.id.line1) as TextView
        mLine2 = findViewById(R.id.line2) as TextView
        mLine3 = findViewById(R.id.line3) as TextView
        mLoading = findViewById(R.id.progressBar1) as ProgressBar
        mControllers = findViewById(R.id.controllers)

        mSkipNext!!.setOnClickListener {
            val controls = supportMediaController.transportControls
            controls.skipToNext()
        }

        mSkipPrev!!.setOnClickListener {
            val controls = supportMediaController.transportControls
            controls.skipToPrevious()
        }

        mPlayPause!!.setOnClickListener {
            val state = supportMediaController.playbackState
            if (state != null) {
                val controls = supportMediaController.transportControls
                when (state.state) {
                    PlaybackStateCompat.STATE_PLAYING // fall through
                        , PlaybackStateCompat.STATE_BUFFERING -> {
                        controls.pause()
                        stopSeekbarUpdate()
                    }
                    PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> {
                        controls.play()
                        scheduleSeekbarUpdate()
                    }
                    else -> LogHelper.d(TAG, "onClick with state ", state.state.toString())
                }
            }
        }

        mSeekbar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mStart!!.text = DateUtils.formatElapsedTime((progress / 1000).toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopSeekbarUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                supportMediaController.transportControls.seekTo(seekBar.progress.toLong())
                scheduleSeekbarUpdate()
            }
        })

        // Only update from the intent if we are not recreating from a config change:
        if (savedInstanceState == null) {
            updateFromParams(intent)
        }

        mMediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java), mConnectionCallback, null)
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(
                this@FullScreenPlayerActivity, token)
        if (mediaController.metadata == null) {
            finish()
            return
        }
        supportMediaController = mediaController
        mediaController.registerCallback(mCallback)
        val state = mediaController.playbackState
        updatePlaybackState(state)
        val metadata = mediaController.metadata
        if (metadata != null) {
            updateMediaDescription(metadata.description)
            updateDuration(metadata)
        }
        updateProgress()
        if (state != null && (state.state == PlaybackStateCompat.STATE_PLAYING || state.state == PlaybackStateCompat.STATE_BUFFERING)) {
            scheduleSeekbarUpdate()
        }
    }

    private fun updateFromParams(intent: Intent?) {
        if (intent != null) {
            val description = intent.getParcelableExtra<MediaDescriptionCompat>(
                    MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION)
            if (description != null) {
                updateMediaDescription(description)
            }
        }
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    { mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture!!.cancel(false)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (mMediaBrowser != null) {
            mMediaBrowser!!.connect()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (mMediaBrowser != null) {
            mMediaBrowser!!.disconnect()
        }
        if (supportMediaController != null) {
            supportMediaController.unregisterCallback(mCallback)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        stopSeekbarUpdate()
        mExecutorService.shutdown()
    }

    private fun fetchImageAsync(description: MediaDescriptionCompat) {
        if (description.iconUri == null) {
            return
        }
        val artUrl = description.iconUri!!.toString()
        mCurrentArtUrl = artUrl
        val cache = AlbumArtCache.instance
        var art = cache.getBigImage(artUrl)
        if (art == null) {
            art = description.iconBitmap
        }
        if (art != null) {
            // if we have the art cached or from the MediaDescription, use it:
            mBackgroundImage!!.setImageBitmap(art)
        } else {
            // otherwise, fetch a high res version and update:
            cache.fetch(artUrl, object : AlbumArtCache.FetchListener() {
                override fun onFetched(artUrl: String, bitmap: Bitmap, icon: Bitmap) {
                    // sanity check, in case a new fetch request has been done while
                    // the previous hasn't yet returned:
                    if (artUrl == mCurrentArtUrl) {
                        mBackgroundImage!!.setImageBitmap(bitmap)
                    }
                }
            })
        }
    }

    private fun updateMediaDescription(description: MediaDescriptionCompat?) {
        if (description == null) {
            return
        }
        LogHelper.d(TAG, "updateMediaDescription called ")
        mLine1!!.text = description.title
        mLine2!!.text = description.subtitle
        fetchImageAsync(description)
    }

    private fun updateDuration(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            return
        }
        LogHelper.d(TAG, "updateDuration called ")
        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        mSeekbar!!.max = duration
        mEnd!!.text = DateUtils.formatElapsedTime((duration / 1000).toLong())
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            return
        }
        mLastPlaybackState = state
        if (supportMediaController != null && supportMediaController.extras != null) {
            val castName = supportMediaController.extras.getString(MusicService.EXTRA_CONNECTED_CAST)
            val line3Text = if (castName == null)
                ""
            else
                resources.getString(R.string.casting_to_device, castName)
            mLine3!!.text = line3Text
        }

        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                mLoading!!.visibility = INVISIBLE
                mPlayPause!!.visibility = VISIBLE
                mPlayPause!!.setImageDrawable(mPauseDrawable)
                mControllers!!.visibility = VISIBLE
                scheduleSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                mControllers!!.visibility = VISIBLE
                mLoading!!.visibility = INVISIBLE
                mPlayPause!!.visibility = VISIBLE
                mPlayPause!!.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {
                mLoading!!.visibility = INVISIBLE
                mPlayPause!!.visibility = VISIBLE
                mPlayPause!!.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_BUFFERING -> {
                mPlayPause!!.visibility = INVISIBLE
                mLoading!!.visibility = VISIBLE
                mLine3!!.setText(R.string.loading)
                stopSeekbarUpdate()
            }
            else -> LogHelper.d(TAG, "Unhandled state ", state.state.toString())
        }

        mSkipNext!!.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT == 0L)
            INVISIBLE
        else
            VISIBLE
        mSkipPrev!!.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS == 0L)
            INVISIBLE
        else
            VISIBLE
    }

    private fun updateProgress() {
        if (mLastPlaybackState == null) {
            return
        }
        var currentPosition = mLastPlaybackState!!.position
        if (mLastPlaybackState!!.state != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            val timeDelta = SystemClock.elapsedRealtime() - mLastPlaybackState!!.lastPositionUpdateTime
            currentPosition += (timeDelta.toInt() * mLastPlaybackState!!.playbackSpeed).toLong()
        }
        mSeekbar!!.progress = currentPosition.toInt()
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(FullScreenPlayerActivity::class)
        private val PROGRESS_UPDATE_INTERNAL: Long = 1000
        private val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    }
}
