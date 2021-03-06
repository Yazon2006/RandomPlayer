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
package ua.motorny.randomplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils

import java.io.IOException

import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnErrorListener
import android.media.MediaPlayer.OnPreparedListener
import android.media.MediaPlayer.OnSeekCompleteListener
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import ua.motorny.randomplayer.MusicService
import ua.motorny.randomplayer.model.MusicProvider
import ua.motorny.randomplayer.model.MusicProviderSource
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper

/**
 * A class that implements local media playback using [android.media.MediaPlayer]
 */
class LocalPlayback(private val mContext: Context, private val mMusicProvider: MusicProvider) : Playback, AudioManager.OnAudioFocusChangeListener, OnCompletionListener, OnErrorListener, OnPreparedListener, OnSeekCompleteListener {
    private val mWifiLock: WifiManager.WifiLock
    override var state: Int = 0
    private var mPlayOnFocusGain: Boolean = false
    private var mCallback: Playback.Callback? = null
    @Volatile private var mAudioNoisyReceiverRegistered: Boolean = false
    @Volatile override var currentStreamPosition: Int? = 0
    @Volatile override var currentMediaId: String? = null

    // Type of audio focus we have:
    private var mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
    private val mAudioManager: AudioManager
    private var mMediaPlayer: MediaPlayer? = null

    private val mAudioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private val mAudioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                LogHelper.d(TAG, "Headphones disconnected.")
                if (isPlaying) {
                    val i = Intent(context, MusicService::class.java)
                    i.action = MusicService.ACTION_CMD
                    i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE)
                    mContext.startService(i)
                }
            }
        }
    }

    init {
        this.mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Create the Wifi lock (this does not acquire the lock, this just creates it)
        this.mWifiLock = (mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, "uAmp_lock")
        this.state = PlaybackStateCompat.STATE_NONE
    }

    override fun start() {
    }

    override fun stop(notifyListeners: Boolean) {
        state = PlaybackStateCompat.STATE_STOPPED
        if (notifyListeners && mCallback != null) {
            mCallback!!.onPlaybackStatusChanged(state)
        }
        currentStreamPosition = currentStreamPosition
        // Give up Audio focus
        giveUpAudioFocus()
        unregisterAudioNoisyReceiver()
        // Relax all resources
        relaxResources(true)
    }

    override val isConnected: Boolean
        get() = true

    override val isPlaying: Boolean
        get() = mPlayOnFocusGain || mMediaPlayer != null && mMediaPlayer!!.isPlaying

    override fun updateLastKnownStreamPosition() {
        if (mMediaPlayer != null) {
            currentStreamPosition = mMediaPlayer!!.currentPosition
        }
    }

    override fun play(item: QueueItem) {
        mPlayOnFocusGain = true
        tryToGetAudioFocus()
        registerAudioNoisyReceiver()
        val mediaId = item.description.mediaId
        val mediaHasChanged = !TextUtils.equals(mediaId, currentMediaId)
        if (mediaHasChanged) {
            currentStreamPosition = 0
            currentMediaId = mediaId.toString()
        }

        if (state == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mMediaPlayer != null) {
            configMediaPlayerState()
        } else {
            state = PlaybackStateCompat.STATE_STOPPED
            relaxResources(false) // release everything except MediaPlayer
            val track = mMusicProvider.getMusic(
                    MediaIDHelper.extractMusicIDFromMediaID(item.description.mediaId.toString()).toString())

            //noinspection ResourceType
            val source = track?.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE)

            try {
                createMediaPlayerIfNeeded()

                state = PlaybackStateCompat.STATE_BUFFERING

                mMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                mMediaPlayer!!.setDataSource(source)

                // Starts preparing the media player in the background. When
                // it's done, it will call our OnPreparedListener (that is,
                // the onPrepared() method on this class, since we set the
                // listener to 'this'). Until the media player is prepared,
                // we *cannot* call start() on it!
                mMediaPlayer!!.prepareAsync()

                // If we are streaming from the internet, we want to hold a
                // Wifi lock, which prevents the Wifi radio from going to
                // sleep while the song is playing.
                mWifiLock.acquire()

                if (mCallback != null) {
                    mCallback!!.onPlaybackStatusChanged(state)
                }

            } catch (ex: IOException) {
                LogHelper.e(TAG, ex, "Exception playing song")
                if (mCallback != null) {
                    mCallback!!.onError(ex.message.toString())
                }
            }

        }
    }

    override fun pause() {
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' state.
            if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
                mMediaPlayer!!.pause()
                currentStreamPosition = mMediaPlayer!!.currentPosition
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources(false)
            giveUpAudioFocus()
        }
        state = PlaybackStateCompat.STATE_PAUSED
        if (mCallback != null) {
            mCallback!!.onPlaybackStatusChanged(state)
        }
        unregisterAudioNoisyReceiver()
    }

    override fun seekTo(position: Int) {
        LogHelper.d(TAG, "seekTo called with ", position.toString())

        if (mMediaPlayer == null) {
            // If we do not have a current media player, simply update the current position
            currentStreamPosition = position
        } else {
            if (mMediaPlayer!!.isPlaying) {
                state = PlaybackStateCompat.STATE_BUFFERING
            }
            mMediaPlayer!!.seekTo(position)
            if (mCallback != null) {
                mCallback!!.onPlaybackStatusChanged(state)
            }
        }
    }

    override fun setCallback(callback: Playback.Callback) {
        this.mCallback = callback
    }

    /**
     * Try to get the system audio focus.
     */
    private fun tryToGetAudioFocus() {
        LogHelper.d(TAG, "tryToGetAudioFocus")
        if (mAudioFocus != AUDIO_FOCUSED) {
            val result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_FOCUSED
            }
        }
    }

    /**
     * Give up the audio focus.
     */
    private fun giveUpAudioFocus() {
        LogHelper.d(TAG, "giveUpAudioFocus")
        if (mAudioFocus == AUDIO_FOCUSED) {
            if (mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
            }
        }
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus state. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private fun configMediaPlayerState() {
        LogHelper.d(TAG, "configMediaPlayerState. mAudioFocus=", mAudioFocus.toString())
        if (mAudioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                pause()
            }
        } else {
            // we have audio focus:
            if (mAudioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mMediaPlayer!!.setVolume(VOLUME_DUCK, VOLUME_DUCK) // we'll be relatively quiet
            } else {
                if (mMediaPlayer != null) {
                    mMediaPlayer!!.setVolume(VOLUME_NORMAL, VOLUME_NORMAL) // we can be loud again
                } // else do something for remote client.
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                if (mMediaPlayer != null && !mMediaPlayer!!.isPlaying) {
                    LogHelper.d(TAG, "configMediaPlayerState startMediaPlayer. seeking to ", currentStreamPosition.toString())
                    if (currentStreamPosition == mMediaPlayer!!.currentPosition) {
                        mMediaPlayer!!.start()
                        state = PlaybackStateCompat.STATE_PLAYING
                    } else {
                        mMediaPlayer!!.seekTo(currentStreamPosition!!)
                        state = PlaybackStateCompat.STATE_BUFFERING
                    }
                }
                mPlayOnFocusGain = false
            }
        }
        if (mCallback != null) {
            mCallback!!.onPlaybackStatusChanged(state)
        }
    }

    /**
     * Called by AudioManager on audio focus changes.
     * Implementation of [android.media.AudioManager.OnAudioFocusChangeListener]
     */
    override fun onAudioFocusChange(focusChange: Int) {
        LogHelper.d(TAG, "onAudioFocusChange. focusChange=", focusChange.toString())
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // We have gained focus:
            mAudioFocus = AUDIO_FOCUSED

        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // We have lost focus. If we can duck (low playback volume), we can keep playing.
            // Otherwise, we need to pause the playback.
            val canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            mAudioFocus = if (canDuck) AUDIO_NO_FOCUS_CAN_DUCK else AUDIO_NO_FOCUS_NO_DUCK

            // If we are playing, we need to reset media player by calling configMediaPlayerState
            // with mAudioFocus properly set.
            if (state == PlaybackStateCompat.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
                mPlayOnFocusGain = true
            }
        } else {
            LogHelper.e(TAG, "onAudioFocusChange: Ignoring unsupported focusChange: ", focusChange.toString())
        }
        configMediaPlayerState()
    }

    /**
     * Called when MediaPlayer has completed a seek

     * @see OnSeekCompleteListener
     */
    override fun onSeekComplete(mp: MediaPlayer) {
        LogHelper.d(TAG, "onSeekComplete from MediaPlayer:", mp.currentPosition.toString())
        currentStreamPosition = mp.currentPosition
        if (state == PlaybackStateCompat.STATE_BUFFERING) {
            mMediaPlayer!!.start()
            state = PlaybackStateCompat.STATE_PLAYING
        }
        if (mCallback != null) {
            mCallback!!.onPlaybackStatusChanged(state)
        }
    }

    /**
     * Called when media player is done playing current song.

     * @see OnCompletionListener
     */
    override fun onCompletion(player: MediaPlayer) {
        LogHelper.d(TAG, "onCompletion from MediaPlayer")
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mCallback != null) {
            mCallback!!.onCompletion()
        }
    }

    /**
     * Called when media player is done preparing.

     * @see OnPreparedListener
     */
    override fun onPrepared(player: MediaPlayer) {
        LogHelper.d(TAG, "onPrepared from MediaPlayer")
        // The media player is done preparing. That means we can start playing if we
        // have audio focus.
        configMediaPlayerState()
    }

    /**
     * Called when there's an error playing media. When this happens, the media
     * player goes to the Error state. We warn the user about the error and
     * reset the media player.

     * @see OnErrorListener
     */
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        LogHelper.e(TAG, "Media player error: what=$what, extra=$extra")
        if (mCallback != null) {
            mCallback!!.onError("MediaPlayer error $what ($extra)")
        }
        return true // true indicates we handled the error
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private fun createMediaPlayerIfNeeded() {
        LogHelper.d(TAG, "createMediaPlayerIfNeeded. needed? ", mMediaPlayer.toString())
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer()

            // Make sure the media player will acquire a wake-lock while
            // playing. If we don't do that, the CPU might go to sleep while the
            // song is playing, causing playback to stop.
            mMediaPlayer!!.setWakeMode(mContext.applicationContext,
                    PowerManager.PARTIAL_WAKE_LOCK)

            // we want the media player to notify us when it's ready preparing,
            // and when it's done playing:
            mMediaPlayer!!.setOnPreparedListener(this)
            mMediaPlayer!!.setOnCompletionListener(this)
            mMediaPlayer!!.setOnErrorListener(this)
            mMediaPlayer!!.setOnSeekCompleteListener(this)
        } else {
            mMediaPlayer!!.reset()
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.

     * @param releaseMediaPlayer Indicates whether the Media Player should also
     * *            be released or not
     */
    private fun relaxResources(releaseMediaPlayer: Boolean) {
        LogHelper.d(TAG, "relaxResources. releaseMediaPlayer=", releaseMediaPlayer.toString())

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mMediaPlayer != null) {
            mMediaPlayer!!.reset()
            mMediaPlayer!!.release()
            mMediaPlayer = null
        }

        // we can also release the Wifi lock, if we're holding it
        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter)
            mAudioNoisyReceiverRegistered = true
        }
    }

    private fun unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver)
            mAudioNoisyReceiverRegistered = false
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(LocalPlayback::class)

        // The volume we set the media player to when we lose audio focus, but are
        // allowed to reduce the volume instead of stopping playback.
        val VOLUME_DUCK = 0.2f
        // The volume we set the media player when we have audio focus.
        val VOLUME_NORMAL = 1.0f

        // we don't have audio focus, and can't duck (play at a low volume)
        private val AUDIO_NO_FOCUS_NO_DUCK = 0
        // we don't have focus, but can duck (play at a low volume)
        private val AUDIO_NO_FOCUS_CAN_DUCK = 1
        // we have full audio focus
        private val AUDIO_FOCUSED = 2
    }
}
