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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.Presenter
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.View
import android.view.ViewGroup
import ua.motorny.randomplayer.AlbumArtCache
import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper

class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        LogHelper.d(TAG, "onCreateViewHolder")
        mContext = parent.context

        val cardView = ImageCardView(mContext)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(mContext!!.resources.getColor(R.color.default_background))
        return CardViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val description: MediaDescriptionCompat
        if (item is MediaBrowserCompat.MediaItem) {
            LogHelper.d(TAG, "onBindViewHolder MediaItem: ", item.toString())
            description = item.description
        } else if (item is MediaSessionCompat.QueueItem) {
            description = item.description
        } else {
            throw IllegalArgumentException("Object must be MediaItem or QueueItem, not " + item.javaClass.simpleName)
        }

        val cardViewHolder = viewHolder as CardViewHolder
        cardViewHolder.mCardView.titleText = description.title
        cardViewHolder.mCardView.contentText = description.subtitle
        cardViewHolder.mCardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        val artUri = description.iconUri
        if (artUri == null) {
            setCardImage(cardViewHolder, description.iconBitmap)
        } else {
            // IconUri potentially has a better resolution than iconBitmap.
            val artUrl = artUri.toString()
            val cache = AlbumArtCache.instance
            if (cache.getBigImage(artUrl) != null) {
                // So, we use it immediately if it's cached:
                setCardImage(cardViewHolder, cache.getBigImage(artUrl))
            } else {
                // Otherwise, we use iconBitmap if available while we wait for iconURI
                setCardImage(cardViewHolder, description.iconBitmap)
                cache.fetch(artUrl, object : AlbumArtCache.FetchListener() {
                    override fun onFetched(artUrl: String, bitmap: Bitmap, icon: Bitmap) {
                        setCardImage(cardViewHolder, bitmap)
                    }
                })
            }
        }
    }

    private fun setCardImage(cardViewHolder: CardViewHolder, art: Bitmap?) {
        var artDrawable: Drawable? = null
        if (art != null) {
            artDrawable = BitmapDrawable(mContext!!.resources, art)
        } else {
            val title = cardViewHolder.mCardView.titleText
            if (title != null && title.length > 0) {
                artDrawable = TextDrawable(title[0].toString())
            }
        }
        cardViewHolder.mCardView.mainImage = artDrawable
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        LogHelper.d(TAG, "onUnbindViewHolder")
    }

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {
        LogHelper.d(TAG, "onViewAttachedToWindow")
    }

    private class CardViewHolder(view: View) : Presenter.ViewHolder(view) {
        val mCardView: ImageCardView

        init {
            mCardView = view as ImageCardView
        }
    }

    /**
     * Simple drawable that draws a text (letter, in this case). Used with the media title when
     * the MediaDescription has no corresponding album art.
     */
    private class TextDrawable(private val text: String) : Drawable() {
        private val paint: Paint

        init {
            this.paint = Paint()
            paint.color = Color.WHITE
            paint.textSize = 280f
            paint.isAntiAlias = true
            paint.isFakeBoldText = true
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val r = bounds
            val count = canvas.save()
            canvas.translate(r.left.toFloat(), r.top.toFloat())
            val midW = (r.width() / 2).toFloat()
            val midH = r.height() / 2 - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(text, midW, midH, paint)
            canvas.restoreToCount(count)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(cf: ColorFilter?) {
            paint.colorFilter = cf
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(CardPresenter::class)
        private val CARD_WIDTH = 300
        private val CARD_HEIGHT = 250

        private var mContext: Context? = null
    }

}


