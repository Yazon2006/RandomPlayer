package ua.motorny.randomplayer

import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager
import com.vk.sdk.VKSdk
import ua.motorny.randomplayer.ui.FullScreenPlayerActivity

open class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        VKSdk.initialize(this);

        val applicationId = resources.getString(R.string.cast_application_id)
        VideoCastManager.initialize(
                applicationContext,
                CastConfiguration.Builder(applicationId).enableWifiReconnection().enableAutoReconnect().enableDebug().setTargetActivity(FullScreenPlayerActivity::class.java).build())
    }


}