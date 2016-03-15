package ua.motorny.randomplayer.ui.activities

import android.support.v7.widget.Toolbar
import android.widget.TextView
import android.widget.Toast
import com.vk.sdk.VKAccessToken
import com.vk.sdk.VKAccessTokenTracker
import com.vk.sdk.VKSdk
import com.vk.sdk.api.VKApi
import com.vk.sdk.api.VKError
import com.vk.sdk.api.VKRequest
import com.vk.sdk.api.VKResponse
import com.vk.sdk.api.model.VkAudioArray
import org.androidannotations.annotations.AfterViews

import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.ViewById
import ua.motorny.randomplayer.R

@EActivity(R.layout.activity_navigation)
open class NavigationActivity : BaseActivity () {

    var vkAccessTokenTracker : VKAccessTokenTracker = object : VKAccessTokenTracker() {
        override fun onVKAccessTokenChanged(oldToken : VKAccessToken?, newToken : VKAccessToken? ) {
            if (newToken == null) {
                VKSdk.login(this@NavigationActivity, "scope=offline,audio");
            } else {
                getMusic();
            }
        }
    };

    @ViewById
    lateinit var toolbar : Toolbar;

    @ViewById
    lateinit var hello_textView : TextView;

    @AfterViews
    protected fun afterView() {
        setSupportActionBar(toolbar);

        vkAccessTokenTracker.startTracking();
        if (!VKSdk.isLoggedIn()) {
//            var fingerprints : String = VKUtil.getCertificateFingerprint(this@NavigationActivity, this.packageName)[0];
            VKSdk.login(this@NavigationActivity, "scope=offline,audio");
        } else {
            getMusic();
        }
    }

    private fun getMusic() {
        var vkRequest : VKRequest = VKApi.audio().popular;
        vkRequest.methodParameters.put("only_eng", "1");
        vkRequest.methodParameters.put("count", "11");

        vkRequest.executeWithListener(object : VKRequest.VKRequestListener() {
            override fun onComplete(response: VKResponse) {
                var vkAudioArray : VkAudioArray = response.parsedModel as VkAudioArray;
                for (audio in vkAudioArray) {
                    hello_textView.append(audio.title + "\n");
                }
                Toast.makeText(this@NavigationActivity, "Ok", Toast.LENGTH_SHORT).show();
                //Do complete stuff
            }

            override fun onError(error: VKError) {
                Toast.makeText(this@NavigationActivity, error.errorMessage, Toast.LENGTH_SHORT).show();
                //Do error stuff
            }

            override fun onProgress(progressType: VKRequest.VKProgressType, bytesLoaded: Long, bytesTotal: Long) {
                //I don't really believe in progress
            }

            override fun attemptFailed(request: VKRequest, attemptNumber: Int, totalAttempts: Int) {
                //More luck next time
            }
        })
    }

}
