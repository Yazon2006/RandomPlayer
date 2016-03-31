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

import android.content.res.Resources
import android.os.AsyncTask
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.model.MusicProvider
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper
import ua.motorny.randomplayer.utils.WearHelper

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
class PlaybackManager(private val mServiceCallback: PlaybackManager.PlaybackServiceCallback, private val mResources: Resources,
                      private val mMusicProvider: MusicProvider, private val mQueueManager: QueueManager,
                      playback: Playback) : Playback.Callback {
    var playback: Playback? = null
        private set
    private val mMediaSessionCallback: MediaSessionCallback

    init {
        mMediaSessionCallback = MediaSessionCallback()
        this.playback = playback
        this.playback!!.setCallback(this)
    }

    val mediaSessionCallback: MediaSessionCompat.Callback
        get() = mMediaSessionCallback

    /**
     * Handle a request to play music
     */
    fun handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + playback!!.state)
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart()
            playback!!.play(currentMusic)
        }
    }

    /**
     * Handle a request to pause music
     */
    fun handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + playback!!.state)
        if (playback!!.isPlaying) {
            playback!!.pause()
            mServiceCallback.onPlaybackStop()
        }
    }

    /**
     * Handle a request to stop music

     * @param withError Error message in case the stop has an unexpected cause. The error
     * *                  message will be set in the PlaybackState and will be visible to
     * *                  MediaController clients.
     */
    fun handleStopRequest(withError: String?) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + playback!!.state + " error=", withError.toString())
        playback!!.stop(true)
        mServiceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }


    /**
     * Update the current media player state, optionally showing an error message.

     * @param error if not null, error message to present to the user.
     */
    fun updatePlaybackState(error: String?) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + playback!!.state)
        var position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        if (playback != null && playback!!.isConnected) {
            position = playback!!.currentStreamPosition!!.toLong()
        }

        //noinspection ResourceType
        val stateBuilder = PlaybackStateCompat.Builder().setActions(availableActions)

        setCustomAction(stateBuilder)
        var state = playback!!.state

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error)
            state = PlaybackStateCompat.STATE_ERROR
        }
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

        // Set the activeQueueItemId if the current index is valid.
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.queueId)
        }

        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build())

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            mServiceCallback.onNotificationRequired()
        }
    }

    private fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {
        val currentMusic = mQueueManager.currentMusic ?: return
        // Set appropriate "Favorite" icon on Custom action:
        val mediaId = currentMusic.description.mediaId ?: return
        val musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId)
        val favoriteIcon = if (mMusicProvider.isFavorite(musicId.toString()))
            R.drawable.ic_star_on
        else
            R.drawable.ic_star_off
        LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                musicId.toString(), " current favorite=", mMusicProvider.isFavorite(musicId.toString()).toString())
        val customActionExtras = Bundle()
        WearHelper.setShowCustomActionOnWear(customActionExtras, true)
        stateBuilder.addCustomAction(PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_THUMBS_UP, mResources.getString(R.string.favorite), favoriteIcon).setExtras(customActionExtras).build())
    }

    private val availableActions: Long
        get() {
            var actions = PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            if (playback!!.isPlaying) {
                actions = actions or PlaybackStateCompat.ACTION_PAUSE
            }
            return actions
        }

    /**
     * Implementation of the Playback.Callback interface
     */
    override fun onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mQueueManager.skipQueuePosition(1)) {
            handlePlayRequest()
            mQueueManager.updateMetadata()
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null)
        }
    }

    override fun onPlaybackStatusChanged(state: Int) {
        updatePlaybackState(null)
    }

    override fun onError(error: String) {
        updatePlaybackState(error)
    }

    override fun setCurrentMediaId(mediaId: String) {
        LogHelper.d(TAG, "setCurrentMediaId", mediaId)
        mQueueManager.setQueueFromMusic(mediaId)
    }


    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.

     * @param playback switch to this playback
     */
    fun switchToPlayback(playback: Playback?, resumePlaying: Boolean) {
        if (playback == null) {
            throw IllegalArgumentException("Playback cannot be null")
        }
        // suspend the current one.
        val oldState = this.playback!!.state
        val pos = this.playback!!.currentStreamPosition
        val currentMediaId = this.playback!!.currentMediaId
        this.playback!!.stop(false)
        playback.setCallback(this)
        playback.currentStreamPosition = if (pos!! < 0) 0 else pos
        playback.currentMediaId = currentMediaId
        playback.start()
        // finally swap the instance
        this.playback = playback
        when (oldState) {
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_PAUSED -> this.playback!!.pause()
            PlaybackStateCompat.STATE_PLAYING -> {
                val currentMusic = mQueueManager.currentMusic
                if (resumePlaying && currentMusic != null) {
                    this.playback!!.play(currentMusic)
                } else if (!resumePlaying) {
                    this.playback!!.pause()
                } else {
                    this.playback!!.stop(true)
                }
            }
            PlaybackStateCompat.STATE_NONE -> {
            }
            else -> LogHelper.d(TAG, "Default called. Old state is ", oldState.toString())
        }
    }


    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            LogHelper.d(TAG, "play")
            if (mQueueManager.currentMusic == null) {
                mQueueManager.setRandomQueue()
            }
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId)
            mQueueManager.setCurrentQueueItem(queueId)
            handlePlayRequest()
            mQueueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            LogHelper.d(TAG, "onSeekTo:", position.toString())
            playback!!.seekTo(position.toInt())
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId.toString(), "  extras=", extras.toString())
            mQueueManager.setQueueFromMusic(mediaId.toString())
            handlePlayRequest()
        }

        override fun onPause() {
            LogHelper.d(TAG, "pause. current state=" + playback!!.state)
            handlePauseRequest()
        }

        override fun onStop() {
            LogHelper.d(TAG, "stop. current state=" + playback!!.state)
            handleStopRequest(null)
        }

        override fun onSkipToNext() {
            LogHelper.d(TAG, "skipToNext")
            if (mQueueManager.skipQueuePosition(1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mQueueManager.updateMetadata()
        }

        override fun onSkipToPrevious() {
            if (mQueueManager.skipQueuePosition(-1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mQueueManager.updateMetadata()
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (CUSTOM_ACTION_THUMBS_UP == action) {
                LogHelper.i(TAG, "onCustomAction: favorite for current track")
                val currentMusic = mQueueManager.currentMusic
                if (currentMusic != null) {
                    val mediaId = currentMusic.description.mediaId
                    if (mediaId != null) {
                        val musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId)
                        mMusicProvider.setFavorite(musicId.toString(), !mMusicProvider.isFavorite(musicId.toString()))
                    }
                }
                // playback state needs to be updated because the "Favorite" icon on the
                // custom action will change to reflect the new favorite state.
                updatePlaybackState(null)
            } else {
                LogHelper.e(TAG, "Unsupported action: ", action)
            }
        }

        /**
         * Handle free and contextual searches.
         *
         *
         * All voice searches on Android Auto are sent to this method through a connected
         * [android.support.v4.media.session.MediaControllerCompat].
         *
         *
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         *
         *
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an [AsyncTask] as we do here).
         */
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            LogHelper.d(TAG, "playFromSearch  query=", query.toString(), " extras=", extras.toString())

            playback!!.state = PlaybackStateCompat.STATE_CONNECTING
            if (query != null && extras != null) {
                mQueueManager.setQueueFromSearch(query, extras)
            }
            handlePlayRequest()
            mQueueManager.updateMetadata()
        }
    }


    interface PlaybackServiceCallback {
        fun onPlaybackStart()

        fun onNotificationRequired()

        fun onPlaybackStop()

        fun onPlaybackStateUpdated(newState: PlaybackStateCompat)
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(PlaybackManager::class)
        // Action to thumbs up a media item
        private val CUSTOM_ACTION_THUMBS_UP = "ua.motorny.randomplayer.THUMBS_UP"
    }
}
