package ua.motorny.randomplayer.ui.activities

import android.os.Bundle
import android.support.v7.widget.Toolbar

import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.ViewById
import ua.motorny.randomplayer.R

@EActivity(R.layout.activity_navigation)
open class NavigationActivity : BaseActivity () {

    @ViewById
    lateinit var toolbar : Toolbar;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
    }

}
