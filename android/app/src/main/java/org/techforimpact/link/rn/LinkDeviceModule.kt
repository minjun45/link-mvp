package org.techforimpact.link.rn

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Android capabilities only. Screen flow, cart and payment state live in TypeScript. */
class LinkDeviceModule(private val context:ReactApplicationContext):NativeLinkDeviceSpec(context) {
 companion object { const val NAME="LinkDevice";const val PERMISSION=710;const val BLUETOOTH=711;const val VOICE=712;const val PAYMENT=713 }
 private val handler=Handler(Looper.getMainLooper())
 private var permissionPromise:Promise?=null
 private var bluetoothPromise:Promise?=null
 private var voicePromise:Promise?=null
 private var paymentPromise:Promise?=null
 private var scanPromise:Promise?=null
 private var callback:ScanCallback?=null
 private var scanner:android.bluetooth.le.BluetoothLeScanner?=null
 private var finishScan:Runnable?=null
 private var tts:TextToSpeech?=null
 private var ttsReady=false
 private var waitingSpeech:String?=null
 private var tone:ToneGenerator?=null
 private val listener=object:BaseActivityEventListener(){
  override fun onActivityResult(activity:Activity,requestCode:Int,resultCode:Int,data:Intent?){when(requestCode){
   BLUETOOTH->{bluetoothPromise?.resolve(resultCode==Activity.RESULT_OK);bluetoothPromise=null}
   VOICE->{val p=voicePromise;voicePromise=null;if(resultCode==Activity.RESULT_OK)p?.resolve(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()) else p?.reject("VOICE_CANCELLED","음성 검색을 취소했어요.")}
   PAYMENT->{paymentPromise?.resolve("returned");paymentPromise=null}
  }}
 }
 init {context.addActivityEventListener(listener)}
 override fun getName()=NAME
 private fun permissions()=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
 private fun permitted()=permissions().all{ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}
 @SuppressLint("MissingPermission")
 private fun ready():String {
  if(!permitted())return "permission"
  val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter?:return "unsupported"
  if(!adapter.isEnabled)return "bluetooth"
  if(Build.VERSION.SDK_INT<=30){val lm=context.getSystemService(LocationManager::class.java);if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))return "location"}
  return "ready"
 }
 override fun readiness(promise:Promise){promise.resolve(ready())}
 override fun requestPermissions(promise:Promise){handler.post{
  val activity=context.currentActivity as? PermissionAwareActivity
  if(activity==null){promise.reject("NO_ACTIVITY","앱을 다시 열어 주세요.");return@post}
  if(permitted()){promise.resolve(true);return@post}
  if(permissionPromise!=null){promise.reject("BUSY","권한 창에서 선택해 주세요.");return@post}
  permissionPromise=promise
  activity.requestPermissions(permissions(),PERMISSION,PermissionListener{code,_,_->if(code==PERMISSION){permissionPromise?.resolve(permitted());permissionPromise=null;true}else false})
 }}
 @SuppressLint("MissingPermission")
 override fun requestBluetooth(promise:Promise){handler.post{
  val activity=context.currentActivity
  if(activity==null||!permitted()){promise.reject("PERMISSION","주변 기기 권한을 먼저 허용해 주세요.");return@post}
  if(bluetoothPromise!=null){promise.reject("BUSY","블루투스 창에서 선택해 주세요.");return@post}
  try{bluetoothPromise=promise;activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),BLUETOOTH)}catch(e:Exception){bluetoothPromise=null;promise.reject("BLUETOOTH",e)}
 }}
 @SuppressLint("MissingPermission")
 override fun scan(namesJson:String,promise:Promise){handler.post{
  stopScan(true)
  if(ready()!="ready"){promise.reject("NOT_READY","블루투스와 권한을 확인해 주세요.");return@post}
  try{
   val names=JSONArray(namesJson).let{a->(0 until a.length()).map{a.getString(it)}.toSet()}
   if(names.isEmpty()){promise.resolve("[]");return@post}
   val found=mutableMapOf<String,Int>();scanPromise=promise
   scanner=context.getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner
   callback=object:ScanCallback(){
    override fun onScanResult(callbackType:Int,result:ScanResult){val name=result.scanRecord?.deviceName?:return;if(name in names)found[name]=result.rssi}
    override fun onBatchScanResults(results:MutableList<ScanResult>){results.forEach{onScanResult(0,it)}}
    override fun onScanFailed(errorCode:Int){val p=scanPromise;scanPromise=null;stopScan(false);p?.reject("SCAN_FAILED","매장 탐색을 시작하지 못했어요. ($errorCode)")}
   }
   finishScan=Runnable{val p=scanPromise;scanPromise=null;stopScan(false);val result=JSONArray();found.entries.sortedByDescending{it.value}.forEach{result.put(JSONObject().put("name",it.key).put("rssi",it.value))};p?.resolve(result.toString())}
   scanner?.startScan(names.map{ScanFilter.Builder().setDeviceName(it).build()},ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
   if(scanner==null)throw IllegalStateException("블루투스를 사용할 수 없어요.")
   handler.postDelayed(finishScan!!,8000)
  }catch(e:Exception){scanPromise=null;stopScan(false);promise.reject("SCAN_FAILED",e)}
 }}
 @SuppressLint("MissingPermission")
 private fun stopScan(reject:Boolean){finishScan?.let{handler.removeCallbacks(it)};callback?.let{runCatching{scanner?.stopScan(it)}};callback=null;scanner=null;finishScan=null;if(reject){scanPromise?.reject("SCAN_CANCELLED","매장 탐색을 중지했어요.");scanPromise=null}}
 override fun cancelScan(){handler.post{stopScan(true)}}
 override fun openSettings(kind:String){handler.post{val intent=when(kind){"location"->Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);"bluetooth"->Intent(Settings.ACTION_BLUETOOTH_SETTINGS);else->Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))};runCatching{context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}}
 override fun signal(kind:String){handler.post{
  val pattern=when(kind){"found"->longArrayOf(0,90,90,90);"selected"->longArrayOf(0,60);"success"->longArrayOf(0,100,70,100,70,250);else->longArrayOf(0,350,120,350)}
  val vibrator=context.getSystemService(Vibrator::class.java)
  if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(pattern,-1))else @Suppress("DEPRECATION") vibrator.vibrate(pattern,-1)
  runCatching{if(tone==null)tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,50);tone?.startTone(when(kind){"error"->ToneGenerator.TONE_PROP_NACK;"success"->ToneGenerator.TONE_PROP_ACK;else->ToneGenerator.TONE_PROP_BEEP},120)}
 }}
 override fun speak(text:String){handler.post{
  if(tts==null){waitingSpeech=text;tts=TextToSpeech(context){status->handler.post{ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){tts?.language=Locale.KOREAN;waitingSpeech?.let{tts?.speak(it,TextToSpeech.QUEUE_FLUSH,null,"link")};waitingSpeech=null}}}}
  else if(ttsReady)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"link")else waitingSpeech=text
 }}
 override fun stopSpeaking(){handler.post{waitingSpeech=null;tts?.stop()}}
 override fun recognize(promise:Promise){handler.post{
  if(voicePromise!=null){promise.reject("BUSY","음성 검색을 진행하고 있어요.");return@post}
  try{val activity=context.currentActivity?:throw IllegalStateException("앱을 다시 열어 주세요.");voicePromise=promise;activity.startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR").putExtra(RecognizerIntent.EXTRA_PROMPT,"찾을 이름을 말씀해 주세요"),VOICE)}catch(e:Exception){voicePromise=null;promise.reject("VOICE_UNAVAILABLE","음성 검색을 사용할 수 없어요. 글자로 검색해 주세요.",e)}
 }}
 override fun openPayment(url:String,base:String,promise:Promise){handler.post{
  if(paymentPromise!=null){promise.reject("BUSY","열려 있는 결제 창을 확인해 주세요.");return@post}
  try{val activity=context.currentActivity?:throw IllegalStateException("앱을 다시 열어 주세요.");paymentPromise=promise;activity.startActivityForResult(Intent(activity,PaymentActivity::class.java).putExtra("url",url).putExtra("base",base).putExtra("cookie",CookieManager.getInstance().getCookie(base).orEmpty()),PAYMENT)}catch(e:Exception){paymentPromise=null;promise.reject("PAYMENT_UNAVAILABLE",e)}
 }}
 override fun invalidate(){handler.post{stopScan(true);tts?.stop();tts?.shutdown();tone?.release();context.removeActivityEventListener(listener);permissionPromise?.reject("CLOSED","앱이 닫혔어요.");bluetoothPromise?.reject("CLOSED","앱이 닫혔어요.");voicePromise?.reject("CLOSED","앱이 닫혔어요.");paymentPromise?.reject("CLOSED","앱이 닫혔어요.")};super.invalidate()}
}
