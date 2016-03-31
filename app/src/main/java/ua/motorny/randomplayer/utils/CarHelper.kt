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
package ua.motorny.randomplayer.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle

import ua.motorny.randomplayer.MusicService

object CarHelper {
    private val TAG = LogHelper.makeLogTag(CarHelper::class)

    private val AUTO_APP_PACKAGE_NAME = "com.google.android.projection.gearhead"

    // Use these extras to reserve space for the corresponding actions, even when they are disabled
    // in the playbackstate, so the custom actions don't reflow.
    private val SLOT_RESERVATION_SKIP_TO_NEXT = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"
    private val SLOT_RESERVATION_SKIP_TO_PREV = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
    private val SLOT_RESERVATION_QUEUE = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_QUEUE"

    /**
     * Action for an intent broadcast by Android Auto when a media app is connected or
     * disconnected. A "connected" media app is the one currently attached to the "media" facet
     * on Android Auto. So, this intent is sent by AA on:

     * - connection: when the phone is projecting and at the moment the app is selected from the
     * list of media apps
     * - disconnection: when another media app is selected from the list of media apps or when
     * the phone stops projecting (when the user unplugs it, for example)

     * The actual event (connected or disconnected) will come as an Intent extra,
     * with the key MEDIA_CONNECTION_STATUS (see below).
     */
    val ACTION_MEDIA_STATUS = "com.google.android.gms.car.media.STATUS"

    /**
     * Key in Intent extras that contains the media connection event type (connected or disconnected)
     */
    val MEDIA_CONNECTION_STATUS = "media_connection_status"

    /**
     * Value of the key MEDIA_CONNECTION_STATUS in Intent extras used when the current media app
     * is connected.
     */
    val MEDIA_CONNECTED = "media_connected"

    /**
     * Value of the key MEDIA_CONNECTION_STATUS in Intent extras used when the current media app
     * is disconnected.
     */
    val MEDIA_DISCONNECTED = "media_disconnected"


    fun isValidCarPackage(packageName: String): Boolean {
        return AUTO_APP_PACKAGE_NAME == packageName
    }

    fun setSlotReservationFlags(extras: Bundle, reservePlayingQueueSlot: Boolean,
                                reserveSkipToNextSlot: Boolean, reserveSkipToPrevSlot: Boolean) {
        if (reservePlayingQueueSlot) {
            extras.putBoolean(SLOT_RESERVATION_QUEUE, true)
        } else {
            extras.remove(SLOT_RESERVATION_QUEUE)
        }
        if (reserveSkipToPrevSlot) {
            extras.putBoolean(SLOT_RESERVATION_SKIP_TO_PREV, true)
        } else {
            extras.remove(SLOT_RESERVATION_SKIP_TO_PREV)
        }
        if (reserveSkipToNextSlot) {
            extras.putBoolean(SLOT_RESERVATION_SKIP_TO_NEXT, true)
        } else {
            extras.remove(SLOT_RESERVATION_SKIP_TO_NEXT)
        }
    }

    /**
     * Returns true when running Android Auto or a car dock.

     * A preferable way of detecting if your app is running in the context of an Android Auto
     * compatible car is by registering a BroadcastReceiver for the action
     * [CarHelper.ACTION_MEDIA_STATUS]. See a sample implementation in
     * [MusicService.onCreate].

     * @param c Context to detect UI Mode.
     * *
     * @return true when device is running in car mode, false otherwise.
     */
    fun isCarUiMode(c: Context): Boolean {
        val uiModeManager = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            LogHelper.d(TAG, "Running in Car mode")
            return true
        } else {
            LogHelper.d(TAG, "Running on a non-Car mode")
            return false
        }
    }
}
