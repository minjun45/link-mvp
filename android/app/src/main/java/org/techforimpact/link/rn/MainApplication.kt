package org.techforimpact.link.rn

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(LinkDevicePackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    ReactFontManager.getInstance().addCustomFont(this, "LinkNoto", R.font.link_noto)
    loadReactNative(this)
  }
}
