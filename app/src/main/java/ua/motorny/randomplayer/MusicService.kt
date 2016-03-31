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

package ua.motorny.randomplayer

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.media.MediaRouter

import com.google.android.gms.cast.ApplicationMetadata
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl
import ua.motorny.randomplayer.model.MusicProvider
import ua.motorny.randomplayer.playback.CastPlayback
import ua.motorny.randomplayer.playback.LocalPlayback
import ua.motorny.randomplayer.playback.PlaybackManager
import ua.motorny.randomplayer.playback.QueueManager
import ua.motorny.randomplayer.ui.NowPlayingActivity
import ua.motorny.randomplayer.utils.CarHelper
import ua.motorny.randomplayer.utils.LogHelper
import ua.motorny.randomplayer.utils.MediaIDHelper
import ua.motorny.randomplayer.utils.WearHelper

import java.lang.ref.WeakReference


/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.

 * To implement a MediaBrowserService, you need to:

 *

 *  *  Extend [android.service.media.MediaBrowserService], implementing the media browsing
 * related methods [android.service.media.MediaBrowserService.onGetRoot] and
 * [android.service.media.MediaBrowserService.onLoadChildren];
 *  *  In onCreate, start a new [android.media.session.MediaSession] and notify its parent
 * with the session's token [android.service.media.MediaBrowserService.setSessionToken];

 *  *  Set a callback on the
 * [android.media.session.MediaSession.setCallback].
 * The callback will receive all the user's actions, like play, pause, etc;

 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])

 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [android.media.session.MediaSession.setPlaybackState]
 * [android.media.session.MediaSession.setMetadata] and
 * [android.media.session.MediaSession.setQueue])

 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService

 *

 * To make your app compatible with Android Auto, you also need to:

 *

 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;

 *

 * @see [README.md](README.md) for more details.
 */
class MusicService : MediaBrowserServiceCompat(), PlaybackManager.PlaybackServiceCallback {

    lateinit private var mMusicProvider: MusicProvider
    private var mPlaybackManager: PlaybackManager? = null

    private var mSession: MediaSessionCompat? = null
    private var mMediaNotificationManager: MediaNotificationManager? = null
    lateinit private var mSessionExtras: Bundle
    private val mDelayedStopHandler = DelayedStopHandler(this)
    private var mMediaRouter: MediaRouter? = null
    private var mPackageValidator: PackageValidator? = null

    private var mIsConnectedToCar: Boolean = false
    private var mCarConnectionReceiver: BroadcastReceiver? = null

    /**
     * Consumer responsible for switching the Playback instances depending on whether
     * it is connected to a remote player.
     */
    private val mCastConsumer = object : VideoCastConsumerImpl() {

        override fun onApplicationConnected(appMetadata: ApplicationMetadata?, sessionId: String?,
                                            wasLaunched: Boolean) {
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                    VideoCastManager.getInstance().deviceName)
            mSession!!.setExtras(mSessionExtras)
            // Now we can switch to CastPlayback
            val playback = CastPlayback(mMusicProvider)
            mMediaRouter?.setMediaSessionCompat(mSession)
            mPlaybackManager!!.switchToPlayback(playback, true)
        }

        override fun onDisconnectionReason(reason: Int) {
            LogHelper.d(TAG, "onDisconnectionReason")
            // This is our final chance to update the underlying stream position
            // In onDisconnected(), the underlying CastPlayback#mVideoCastConsumer
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            mPlaybackManager?.playback?.updateLastKnownStreamPosition()
        }

        override fun onDisconnected() {
            LogHelper.d(TAG, "onDisconnected")
            mSessionExtras.remove(EXTRA_CONNECTED_CAST)
            mSession?.setExtras(mSessionExtras)
            val playback = LocalPlayback(this@MusicService, mMusicProvider)
            mMediaRouter?.setMediaSessionCompat(null)
            mPlaybackManager?.switchToPlayback(playback, false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogHelper.d(TAG, "onCreate")

        mMusicProvider = MusicProvider()

        // To make the app more responsive, fetch and cache catalog information now.
        // This can help improve the response time in the method
        // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
        mMusicProvider.retrieveMediaAsync(null /* Callback */)

        mPackageValidator = PackageValidator(this)

        val queueManager = QueueManager(mMusicProvider, resources,
                object : QueueManager.MetadataUpdateListener {
                    override fun onMetadataChanged(metadata: MediaMetadataCompat) {
                        mSession!!.setMetadata(metadata)
                    }

                    override fun onMetadataRetrieveError() {
                        mPlaybackManager!!.updatePlaybackState(
                                getString(R.string.error_no_metadata))
                    }

                    override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
                        mPlaybackManager!!.handlePlayRequest()
                    }

                    override fun onQueueUpdated(title: String,
                                                newQueue: List<MediaSessionCompat.QueueItem>) {
                        mSession!!.setQueue(newQueue)
                        mSession!!.setQueueTitle(title)
                    }
                })

        val playback = LocalPlayback(this, mMusicProvider)
        mPlaybackManager = PlaybackManager(this, resources, mMusicProvider, queueManager,
                playback)

        // Start a new MediaSession
        mSession = MediaSessionCompat(this, "MusicService")
        setSessionToken(mSession!!.sessionToken)
        mSession!!.setCallback(mPlaybackManager!!.mediaSessionCallback)
        mSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        val context = applicationContext
        val intent = Intent(context, NowPlayingActivity::class.java)
        val pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT)
        mSession!!.setSessionActivity(pi)

        mSessionExtras = Bundle()
        CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true)
        WearHelper.setSlotReservationFlags(mSessionExtras, true, true)
        WearHelper.setUseBackgroundFromTheme(mSessionExtras, true)
        mSession?.setExtras(mSessionExtras)

        mPlaybackManager?.updatePlaybackState(null)

        try {
            mMediaNotificationManager = MediaNotificationManager(this)
        } catch (e: RemoteException) {
            throw IllegalStateException("Could not create a MediaNotificationManager", e)
        }

        VideoCastManager.getInstance().addVideoCastConsumer(mCastConsumer)
        mMediaRouter = MediaRouter.getInstance(applicationContext)

        registerCarConnectionReceiver()
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service.onStartCommand
     */
    override fun onStartCommand(startIntent: Intent?, flags: Int, startId: Int): Int {
        if (startIntent != null) {
            val action = startIntent.action
            val command = startIntent.getStringExtra(CMD_NAME)
            if (ACTION_CMD == action) {
                if (CMD_PAUSE == command) {
                    mPlaybackManager!!.handlePauseRequest()
                } else if (CMD_STOP_CASTING == command) {
                    VideoCastManager.getInstance().disconnect()
                }
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent)
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        return Service.START_STICKY
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service.onDestroy
     */
    override fun onDestroy() {
        LogHelper.d(TAG, "onDestroy")
        unregisterCarConnectionReceiver()
        // Service is being killed, so make sure we release our resources
        mPlaybackManager!!.handleStopRequest(null)
        mMediaNotificationManager!!.stopNotification()
        VideoCastManager.getInstance().removeVideoCastConsumer(mCastConsumer)
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mSession!!.release()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int,
                           rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                "; clientUid=$clientUid ; rootHints=", rootHints.toString())
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator!!.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return null. No further calls will
            // be made to other media browsing methods.
            LogHelper.w(TAG, "OnGetRoot: IGNORING request from untrusted package " + clientPackageName)
            return null
        }
        //noinspection StatementWithEmptyBody
        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library to show a different subset
            // when connected to the car, this is where you should handle it.
            // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
            // that should be different on cars, you should instead use the boolean flag
            // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
        }
        //noinspection StatementWithEmptyBody
        if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library for when browsing from a
            // Wear device, you should return a different MEDIA ROOT here, and then,
            // on onLoadChildren, handle it accordingly.
        }

        return MediaBrowserServiceCompat.BrowserRoot(MediaIDHelper.MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaItem>>) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId)
        result.sendResult(mMusicProvider.getChildren(parentMediaId, resources))
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    override fun onPlaybackStart() {
        if (!mSession!!.isActive) {
            mSession!!.isActive = true
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null)

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        startService(Intent(applicationContext, MusicService::class.java))
    }


    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    override fun onPlaybackStop() {
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        stopForeground(true)
    }

    override fun onNotificationRequired() {
        mMediaNotificationManager!!.startNotification()
    }

    override fun onPlaybackStateUpdated(newState: PlaybackStateCompat) {
        mSession!!.setPlaybackState(newState)
    }

    private fun registerCarConnectionReceiver() {
        val filter = IntentFilter(CarHelper.ACTION_MEDIA_STATUS)
        mCarConnectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS)
                mIsConnectedToCar = CarHelper.MEDIA_CONNECTED == connectionEvent
                LogHelper.i(TAG, "Connection event to Android Auto: ", connectionEvent,
                        " isConnectedToCar=", mIsConnectedToCar.toString())
            }
        }
        registerReceiver(mCarConnectionReceiver, filter)
    }

    private fun unregisterCarConnectionReceiver() {
        unregisterReceiver(mCarConnectionReceiver)
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private class DelayedStopHandler constructor(service: MusicService) : Handler() {
        private val mWeakReference: WeakReference<MusicService>

        init {
            mWeakReference = WeakReference(service)
        }

        override fun handleMessage(msg: Message) {
            val service = mWeakReference.get()
            if (service != null && service.mPlaybackManager!!.playback != null) {
                var isPlaying : Boolean? = service.mPlaybackManager?.playback?.isPlaying;
                if (isPlaying != null && isPlaying) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.")
                    return
                }
                LogHelper.d(TAG, "Stopping service with delay handler.")
                service.stopSelf()
            }
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(MusicService::class)

        // Extra on MediaSession that contains the Cast device name currently connected to
        val EXTRA_CONNECTED_CAST = "ua.motorny.randomplayer.CAST_NAME"
        // The action of the incoming Intent indicating that it contains a command
        // to be executed (see {@link #onStartCommand})
        val ACTION_CMD = "ua.motorny.randomplayer.ACTION_CMD"
        // The key in the extras of the incoming Intent indicating the command that
        // should be executed (see {@link #onStartCommand})
        val CMD_NAME = "CMD_NAME"
        // A value of a CMD_NAME key in the extras of the incoming Intent that
        // indicates that the music playback should be paused (see {@link #onStartCommand})
        val CMD_PAUSE = "CMD_PAUSE"
        // A value of a CMD_NAME key that indicates that the music playback should switch
        // to local playback from cast playback.
        val CMD_STOP_CASTING = "CMD_STOP_CASTING"
        // Delay stopSelf by using a handler.
        private val STOP_DELAY = 30000
    }
}
