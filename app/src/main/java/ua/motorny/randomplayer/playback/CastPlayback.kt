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

import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.common.images.WebImage
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException

import org.json.JSONException
import org.json.JSONObject

import android.support.v4.media.session.MediaSessionCompat.QueueItem
import ua.motorny.randomplayer.model.MusicProvider
import ua.motorny.randomplayer.model.MusicProviderSource
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper

/**
 * An implementation of Playback that talks to Cast.
 */
class CastPlayback(private val mMusicProvider: MusicProvider) : Playback {
    private val mCastConsumer = object : VideoCastConsumerImpl() {

        override fun onRemoteMediaPlayerMetadataUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerMetadataUpdated")
            setMetadataFromRemote()
        }

        override fun onRemoteMediaPlayerStatusUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated")
            updatePlaybackState()
        }
    }

    /** The current PlaybackState */
    override var state: Int = 0
    /** Callback for making completion/error calls on  */
    private var mCallback: Playback.Callback? = null
    @Volatile override var currentStreamPosition: Int? = 0
        get() {
            if (!VideoCastManager.getInstance().isConnected) {
                return currentStreamPosition
            }
            try {
                return VideoCastManager.getInstance().currentMediaPosition.toInt()
            } catch (e: TransientNetworkDisconnectionException) {
                LogHelper.e(TAG, e, "Exception getting media position")
            } catch (e: NoConnectionException) {
                LogHelper.e(TAG, e, "Exception getting media position")
            }

            return -1
        }
    @Volatile override var currentMediaId: String? = null;

    override fun start() {
        VideoCastManager.getInstance().addVideoCastConsumer(mCastConsumer)
    }

    override fun stop(notifyListeners: Boolean) {
        VideoCastManager.getInstance().removeVideoCastConsumer(mCastConsumer)
        state = PlaybackStateCompat.STATE_STOPPED
        if (notifyListeners && mCallback != null) {
            mCallback!!.onPlaybackStatusChanged(state)
        }
    }

    override fun updateLastKnownStreamPosition() {
        currentStreamPosition = currentStreamPosition
    }

    override fun play(item: QueueItem) {
        try {
            if (item.description.mediaId != null) {
                loadMedia(item.description.mediaId!!, true)
            }
            state = PlaybackStateCompat.STATE_BUFFERING
            if (mCallback != null) {
                mCallback!!.onPlaybackStatusChanged(state)
            }
        } catch (e: TransientNetworkDisconnectionException) {
            LogHelper.e(TAG, "Exception loading media ", e.toString())
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: NoConnectionException) {
            LogHelper.e(TAG, "Exception loading media ", e.toString())
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, "Exception loading media ", e.toString())
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: IllegalArgumentException) {
            LogHelper.e(TAG, "Exception loading media ", e.toString())
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        }

    }

    override fun pause() {
        try {
            val manager = VideoCastManager.getInstance()
            if (manager.isRemoteMediaLoaded) {
                manager.pause()
                currentStreamPosition = manager.currentMediaPosition.toInt()
            } else {
                loadMedia(currentMediaId!!, false)
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: CastException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: TransientNetworkDisconnectionException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: NoConnectionException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: IllegalArgumentException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        }

    }

    override fun seekTo(position: Int) {
        if (mCallback != null) {
            mCallback!!.onError("seekTo cannot be calling in the absence of mediaId.")
        }
        try {
            if (VideoCastManager.getInstance().isRemoteMediaLoaded) {
                VideoCastManager.getInstance().seek(position)
                currentStreamPosition = position
            } else {
                currentStreamPosition = position
                loadMedia(currentMediaId!!, false)
            }
        } catch (e: TransientNetworkDisconnectionException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: NoConnectionException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        } catch (e: IllegalArgumentException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            if (mCallback != null) {
                mCallback!!.onError(e.message.toString())
            }
        }

    }

    override fun setCallback(callback: Playback.Callback) {
        this.mCallback = callback
    }

    override val isConnected: Boolean
        get() = VideoCastManager.getInstance().isConnected

    override val isPlaying: Boolean
        get() {
            try {
                return VideoCastManager.getInstance().isConnected && VideoCastManager.getInstance().isRemoteMediaPlaying
            } catch (e: TransientNetworkDisconnectionException) {
                LogHelper.e(TAG, e, "Exception calling isRemoteMoviePlaying")
            } catch (e: NoConnectionException) {
                LogHelper.e(TAG, e, "Exception calling isRemoteMoviePlaying")
            }

            return false
        }

    @Throws(TransientNetworkDisconnectionException::class, NoConnectionException::class, JSONException::class)
    private fun loadMedia(mediaId: String, autoPlay: Boolean) {
        val musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId)
        if (musicId != null) {
            val track = mMusicProvider.getMusic(musicId) ?: throw IllegalArgumentException("Invalid mediaId " + mediaId)
            if (!TextUtils.equals(mediaId, currentMediaId)) {
                currentMediaId = mediaId
                currentStreamPosition = 0
            }
            val customData = JSONObject()
            customData.put(ITEM_ID, mediaId)
            val media = toCastMediaMetadata(track, customData)
            VideoCastManager.getInstance().loadMedia(media, autoPlay, currentStreamPosition!!, customData)
        }
    }

    private fun setMetadataFromRemote() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            val mediaInfo = VideoCastManager.getInstance().remoteMediaInformation ?: return
            val customData = mediaInfo.customData

            if (customData != null && customData.has(ITEM_ID)) {
                val remoteMediaId = customData.getString(ITEM_ID)
                if (!TextUtils.equals(currentMediaId, remoteMediaId)) {
                    currentMediaId = remoteMediaId
                    if (mCallback != null) {
                        mCallback!!.setCurrentMediaId(remoteMediaId)
                    }
                    updateLastKnownStreamPosition()
                }
            }
        } catch (e: TransientNetworkDisconnectionException) {
            LogHelper.e(TAG, e, "Exception processing update metadata")
        } catch (e: NoConnectionException) {
            LogHelper.e(TAG, e, "Exception processing update metadata")
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception processing update metadata")
        }

    }

    private fun updatePlaybackState() {
        val status = VideoCastManager.getInstance().playbackStatus
        val idleReason = VideoCastManager.getInstance().idleReason

        LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated ", status.toString())

        // Convert the remote playback states to media playback states.
        when (status) {
            MediaStatus.PLAYER_STATE_IDLE -> if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                if (mCallback != null) {
                    mCallback!!.onCompletion()
                }
            }
            MediaStatus.PLAYER_STATE_BUFFERING -> {
                state = PlaybackStateCompat.STATE_BUFFERING
                if (mCallback != null) {
                    mCallback!!.onPlaybackStatusChanged(state)
                }
            }
            MediaStatus.PLAYER_STATE_PLAYING -> {
                state = PlaybackStateCompat.STATE_PLAYING
                setMetadataFromRemote()
                if (mCallback != null) {
                    mCallback!!.onPlaybackStatusChanged(state)
                }
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                state = PlaybackStateCompat.STATE_PAUSED
                setMetadataFromRemote()
                if (mCallback != null) {
                    mCallback!!.onPlaybackStatusChanged(state)
                }
            }
            else // case unknown
            -> LogHelper.d(TAG, "State default : ", status.toString())
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(CastPlayback::class)

        private val MIME_TYPE_AUDIO_MPEG = "audio/mpeg"
        private val ITEM_ID = "itemId"

        /**
         * Helper method to convert a [android.media.MediaMetadata] to a
         * [com.google.android.gms.cast.MediaInfo] used for sending media to the receiver app.

         * @param track [com.google.android.gms.cast.MediaMetadata]
         * *
         * @param customData custom data specifies the local mediaId used by the player.
         * *
         * @return mediaInfo [com.google.android.gms.cast.MediaInfo]
         */
        private fun toCastMediaMetadata(track: MediaMetadataCompat,
                                        customData: JSONObject): MediaInfo {
            val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
            mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                    if (track.description.title == null)
                        ""
                    else
                        track.description.title!!.toString())
            mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                    if (track.description.subtitle == null)
                        ""
                    else
                        track.description.subtitle!!.toString())
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                    track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                    track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
            val image = WebImage(
                    Uri.Builder().encodedPath(
                            track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)).build())
            // First image is used by the receiver for showing the audio album art.
            mediaMetadata.addImage(image)
            // Second image is used by Cast Companion Library on the full screen activity that is shown
            // when the cast dialog is clicked.
            mediaMetadata.addImage(image)

            //noinspection ResourceType
            return MediaInfo.Builder(track.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE)).setContentType(MIME_TYPE_AUDIO_MPEG).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(mediaMetadata).setCustomData(customData).build()
        }
    }
}
