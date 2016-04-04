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

import android.app.ActivityOptions
import android.app.FragmentManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.MediaRouteButton
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View

import ua.motorny.randomplayer.R
import ua.motorny.randomplayer.utils.LogHelper
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl
import com.google.android.libraries.cast.companionlibrary.widgets.IntroductoryOverlay
import kotlin.reflect.KClass

/**
 * Abstract activity with toolbar, navigation drawer and cast support. Needs to be extended by
 * any activity that wants to be shown as a top level activity.

 * The requirements for a subclass is to call [.initializeToolbar] on onCreate, after
 * setContentView() is called and have three mandatory layout elements:
 * a [android.support.v7.widget.Toolbar] with id 'toolbar',
 * a [android.support.v4.widget.DrawerLayout] with id 'drawerLayout' and
 * a [android.widget.ListView] with id 'drawerList'.
 */
abstract class ActionBarCastActivity : AppCompatActivity() {

    lateinit private var mCastManager: VideoCastManager
    lateinit private var mMediaRouteMenuItem: MenuItem
    lateinit private var mToolbar: Toolbar
    lateinit private var mDrawerToggle: ActionBarDrawerToggle
    lateinit private var mDrawerLayout: DrawerLayout

    private var mToolbarInitialized: Boolean = false

    private var mItemToOpenWhenDrawerCloses = -1

    private val mCastConsumer = object : VideoCastConsumerImpl() {
        override fun onFailed(resourceId: Int, statusCode: Int) {
            LogHelper.d(TAG, "onFailed ", resourceId.toString(), " status ", statusCode.toString())
        }

        override fun onConnectionSuspended(cause: Int) {
            LogHelper.d(TAG, "onConnectionSuspended() was called with cause: ", cause.toString())
        }

        override fun onConnectivityRecovered() {
        }

        override fun onCastAvailabilityChanged(castPresent: Boolean) {
            if (castPresent) {
                Handler().postDelayed({
                    if (mMediaRouteMenuItem.isVisible) {
                        LogHelper.d(TAG, "Cast Icon is visible")
                        showFtu()
                    }
                }, DELAY_MILLIS.toLong())
            }
        }
    }

    private val mDrawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerClosed(drawerView: View) {
            mDrawerToggle.onDrawerClosed(drawerView)
            if (mItemToOpenWhenDrawerCloses >= 0) {
                val extras = ActivityOptions.makeCustomAnimation(
                        this@ActionBarCastActivity, R.anim.fade_in, R.anim.fade_out).toBundle()

                var activityClass: KClass<out Any>? = null
                when (mItemToOpenWhenDrawerCloses) {
                    R.id.navigation_allmusic -> activityClass = MusicPlayerActivity::class
                    R.id.navigation_playlists -> activityClass = PlaceholderActivity::class
                }
                if (activityClass != null) {
                    startActivity(Intent(this@ActionBarCastActivity, activityClass.java), extras)
                    finish()
                }
            }
        }

        override fun onDrawerStateChanged(newState: Int) {
            mDrawerToggle.onDrawerStateChanged(newState)
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            mDrawerToggle.onDrawerSlide(drawerView, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            mDrawerToggle.onDrawerOpened(drawerView)
            var actionBar = supportActionBar
            if (actionBar != null)
                actionBar.setTitle(R.string.app_name)
        }
    }

    private val mBackStackChangedListener = FragmentManager.OnBackStackChangedListener { updateDrawerToggle() }


    /* lifecycle */

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VideoCastManager.checkGooglePlayServices(this)

        mCastManager = VideoCastManager.getInstance()
        mCastManager.reconnectSessionIfPossible()
    }

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("You must run super.initializeToolbar at " + "the end of your onCreate method")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
            mDrawerToggle.syncState()

    }

    public override fun onResume() {
        super.onResume()
        mCastManager.addVideoCastConsumer(mCastConsumer)
        mCastManager.incrementUiCounter()

        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        fragmentManager.addOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
            mDrawerToggle.onConfigurationChanged(newConfig)
    }

    public override fun onPause() {
        super.onPause()
        mCastManager.removeVideoCastConsumer(mCastConsumer)
        mCastManager.decrementUiCounter()
        fragmentManager.removeOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        mMediaRouteMenuItem = mCastManager.addMediaRouterButton(menu, R.id.media_route_menu_item)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item != null && item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers()
            return
        }
        // Otherwise, it may return to the previous fragment stack
        val fragmentManager = fragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            // Lastly, it will rely on the system behavior for back
            super.onBackPressed()
        }
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        mToolbar.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        mToolbar.setTitle(titleId)
    }

    protected fun initializeToolbar() {
        mToolbar = findViewById(R.id.toolbar) as Toolbar
        mToolbar.inflateMenu(R.menu.main)
        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        val navigationView = findViewById(R.id.nav_view) as NavigationView

        // Create an ActionBarDrawerToggle that will handle opening/closing of the drawer:
        mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.open_content_drawer, R.string.close_content_drawer)
        mDrawerLayout.addDrawerListener(mDrawerListener)
        populateDrawerItems(navigationView)
        setSupportActionBar(mToolbar)
        updateDrawerToggle()

        mToolbarInitialized = true
    }

    private fun populateDrawerItems(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isChecked = true
            mItemToOpenWhenDrawerCloses = menuItem.itemId
            mDrawerLayout.closeDrawers()
            true
        }
        if (MusicPlayerActivity::class.java.isAssignableFrom(javaClass)) {
            navigationView.setCheckedItem(R.id.navigation_allmusic)
        } else if (PlaceholderActivity::class.java.isAssignableFrom(javaClass)) {
            navigationView.setCheckedItem(R.id.navigation_playlists)
        }
    }

    protected fun updateDrawerToggle() {
        val isRoot = fragmentManager.backStackEntryCount == 0
        mDrawerToggle.isDrawerIndicatorEnabled = isRoot
        var actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(!isRoot)
            actionBar.setDisplayHomeAsUpEnabled(!isRoot)
            actionBar.setHomeButtonEnabled(!isRoot)
        }
        if (isRoot) {
            mDrawerToggle.syncState()
        }
    }

    /**
     * Shows the Cast First Time User experience to the user (an overlay that explains what is
     * the Cast icon)
     */
    private fun showFtu() {
        val menu = mToolbar.menu
        val view = menu.findItem(R.id.media_route_menu_item).actionView
        if (view != null && view is MediaRouteButton) {
            val overlay = IntroductoryOverlay.Builder(this).setMenuItem(mMediaRouteMenuItem).setTitleText(R.string.touch_to_cast).setSingleTime().build()
            overlay.show()
        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(ActionBarCastActivity::class)
        private val DELAY_MILLIS = 1000
    }
}
