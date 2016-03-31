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

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle

import ua.motorny.randomplayer.ui.tv.TvPlaybackActivity
import ua.motorny.randomplayer.utils.LogHelper

/**
 * The activity for the Now Playing Card PendingIntent.
 * https://developer.android.com/training/tv/playback/now-playing.html

 * This activity determines which activity to launch based on the current UI mode.
 */
class NowPlayingActivity : Activity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "onCreate")
        val newIntent: Intent
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            LogHelper.d(TAG, "Running on a TV Device")
            newIntent = Intent(this, TvPlaybackActivity::class.java)
        } else {
            LogHelper.d(TAG, "Running on a non-TV Device")
            newIntent = Intent(this, MusicPlayerActivity::class.java)
        }
        startActivity(newIntent)
        finish()
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(NowPlayingActivity::class)
    }
}
