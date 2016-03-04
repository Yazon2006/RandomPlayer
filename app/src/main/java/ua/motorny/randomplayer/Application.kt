package ua.motorny.randomplayer

import com.vk.sdk.VKSdk
import org.androidannotations.annotations.EApplication

@EApplication
open class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        VKSdk.initialize(this);
    }

}