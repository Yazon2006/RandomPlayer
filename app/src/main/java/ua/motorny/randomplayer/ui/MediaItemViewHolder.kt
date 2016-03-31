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
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.media.MediaDescriptionCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import ua.motorny.randomplayer.R

class MediaItemViewHolder {

    lateinit internal var mImageView: ImageView
    lateinit internal var mTitleView: TextView
    lateinit internal var mDescriptionView: TextView

    companion object {

        internal val STATE_INVALID = -1
        internal val STATE_NONE = 0
        internal val STATE_PLAYABLE = 1
        internal val STATE_PAUSED = 2
        internal val STATE_PLAYING = 3

        private var sColorStatePlaying: ColorStateList? = null
        private var sColorStateNotPlaying: ColorStateList? = null

        internal fun setupView(activity: Activity, convertView: View?, parent: ViewGroup,
                               description: MediaDescriptionCompat, state: Int): View {
            var convertView = convertView

            if (sColorStateNotPlaying == null || sColorStatePlaying == null) {
                initializeColorStateLists(activity)
            }

            val holder: MediaItemViewHolder

            var cachedState: Int? = STATE_INVALID

            if (convertView == null) {
                convertView = LayoutInflater.from(activity).inflate(R.layout.media_list_item, parent, false)
                holder = MediaItemViewHolder()
                holder.mImageView = convertView!!.findViewById(R.id.play_eq) as ImageView
                holder.mTitleView = convertView.findViewById(R.id.title) as TextView
                holder.mDescriptionView = convertView.findViewById(R.id.description) as TextView
                convertView.tag = holder
            } else {
                holder = convertView.tag as MediaItemViewHolder
                cachedState = convertView.getTag(R.id.tag_mediaitem_state_cache) as Int
            }

            holder.mTitleView.text = description.title
            holder.mDescriptionView.text = description.subtitle

            // If the state of convertView is different, we need to adapt the view to the
            // new state.
            if (cachedState == null || cachedState !== state) {
                when (state) {
                    STATE_PLAYABLE -> {
                        val pauseDrawable = ContextCompat.getDrawable(activity,
                                R.drawable.ic_play_arrow_black_36dp)
                        DrawableCompat.setTintList(pauseDrawable, sColorStateNotPlaying)
                        holder.mImageView.setImageDrawable(pauseDrawable)
                        holder.mImageView.visibility = View.VISIBLE
                    }
                    STATE_PLAYING -> {
                        val animation = ContextCompat.getDrawable(activity, R.drawable.ic_equalizer_white_36dp) as AnimationDrawable
                        DrawableCompat.setTintList(animation, sColorStatePlaying)
                        holder.mImageView.setImageDrawable(animation)
                        holder.mImageView.visibility = View.VISIBLE
                        animation.start()
                    }
                    STATE_PAUSED -> {
                        val playDrawable = ContextCompat.getDrawable(activity,
                                R.drawable.ic_equalizer1_white_36dp)
                        DrawableCompat.setTintList(playDrawable, sColorStatePlaying)
                        holder.mImageView.setImageDrawable(playDrawable)
                        holder.mImageView.visibility = View.VISIBLE
                    }
                    else -> holder.mImageView.visibility = View.GONE
                }
                convertView.setTag(R.id.tag_mediaitem_state_cache, state)
            }

            return convertView
        }

        private fun initializeColorStateLists(ctx: Context) {
            sColorStateNotPlaying = ColorStateList.valueOf(ctx.resources.getColor(
                    R.color.media_item_icon_not_playing))
            sColorStatePlaying = ColorStateList.valueOf(ctx.resources.getColor(
                    R.color.media_item_icon_playing))
        }
    }
}
