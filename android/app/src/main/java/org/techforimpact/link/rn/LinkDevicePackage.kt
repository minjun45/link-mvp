package org.techforimpact.link.rn
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
class LinkDevicePackage:BaseReactPackage(){
 override fun getModule(name:String,context:ReactApplicationContext):NativeModule?=if(name==LinkDeviceModule.NAME)LinkDeviceModule(context)else null
 override fun getReactModuleInfoProvider()=ReactModuleInfoProvider{mapOf(LinkDeviceModule.NAME to ReactModuleInfo(LinkDeviceModule.NAME,LinkDeviceModule.NAME,false,false,false,true))}
}
