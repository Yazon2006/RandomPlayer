package ua.motorny.randomplayer.ui.activities

import android.support.v7.widget.Toolbar
import android.widget.TextView
import com.vk.sdk.VKSdk
import org.androidannotations.annotations.AfterViews

import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.ViewById
import ua.motorny.randomplayer.R

@EActivity(R.layout.activity_navigation)
open class NavigationActivity : BaseActivity () {

    @ViewById
    lateinit var toolbar : Toolbar;

    @ViewById
    lateinit var hello_textView : TextView;

    @AfterViews
    protected fun afterView() {
        setSupportActionBar(toolbar);
        VKSdk.login(this, null);
    }

}
