package org.techforimpact.link.rn

import android.app.Activity
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.net.URI

/** 결제 전용 WebView. 주문 UI는 React Native이며 JavaScript native bridge는 없다. */
class PaymentActivity:Activity() {
    private lateinit var web:WebView
    private lateinit var base:URI
    @SuppressLint("SetJavaScriptEnabled") // 공급자 결제 페이지에 필요하며 native bridge는 제공하지 않는다.
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val address=intent.getStringExtra("base")?:run{finish();return}
        runCatching { base=URI(address);require(base.scheme=="https" || (BuildConfig.DEBUG && base.scheme=="http" && base.host in listOf("localhost","127.0.0.1","10.0.2.2")));require(base.userInfo==null && base.rawQuery==null && base.rawFragment==null) }.getOrElse { finish();return }
        val url=intent.getStringExtra("url")?:run{finish();return}
        if(!allowed(Uri.parse(url))){finish();return}
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { v,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        val close=Button(this).apply { text="결제 결과 확인으로 돌아가기";minimumHeight=(64*resources.displayMetrics.density).toInt();setOnClickListener { setResult(RESULT_OK);finish() } }
        web=WebView(this)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                val target=request.url
                if(target.scheme=="https"||target.scheme=="http") {
                    if(isBackend(target) && target.path=="/" && target.fragment?.startsWith("order/")==true){setResult(RESULT_OK);finish();return true}
                    return !allowed(target)
                }
                return openKakao(target)
            }
            override fun onPageFinished(view:WebView,url:String) {
                val u=Uri.parse(url)
                if(isBackend(u) && u.path=="/" && (u.fragment?.startsWith("order/")==true || u.fragment?.startsWith("payment/")==true)){setResult(RESULT_OK);finish()}
            }
        }
        layout.addView(close,LinearLayout.LayoutParams(-1,-2));layout.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(layout);ViewCompat.requestApplyInsets(layout)
        CookieManager.getInstance().apply {
            setAcceptCookie(true);setAcceptThirdPartyCookies(web,false)
            val cookie=intent.getStringExtra("cookie").orEmpty()+"; Path=/; HttpOnly; SameSite=Lax"+(if(base.scheme=="https")"; Secure" else "")
            setCookie(address,cookie) { success ->
                if(!success){Toast.makeText(this@PaymentActivity,"결제 세션을 준비하지 못했어요. 다시 시도해 주세요.",Toast.LENGTH_LONG).show();finish()}
                else{flush();if(savedInstanceState!=null)web.restoreState(savedInstanceState) else web.loadUrl(url)}
            }
        }
    }
    private fun isBackend(u:Uri)=u.scheme==base.scheme&&u.host==base.host&&(if(u.port==-1)if(u.scheme=="https")443 else 80 else u.port)==(if(base.port==-1)if(base.scheme=="https")443 else 80 else base.port)
    private fun allowed(u:Uri):Boolean {
        if(isBackend(u))return true
        val h=u.host?:return false
        return u.scheme=="https"&&(h=="kakaopay.com"||h.endsWith(".kakaopay.com")||h=="kakao.com"||h.endsWith(".kakao.com"))
    }
    private fun openKakao(uri:Uri):Boolean {
        val target=if(uri.scheme=="intent")runCatching { Intent.parseUri(uri.toString(),Intent.URI_INTENT_SCHEME).data }.getOrNull() else uri
        if(target?.scheme!="kakaotalk")return true
        try { startActivity(Intent(Intent.ACTION_VIEW,target).setPackage("com.kakao.talk").addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch(e:ActivityNotFoundException){Toast.makeText(this,"카카오톡을 설치한 뒤 다시 시도해 주세요.",Toast.LENGTH_LONG).show()}
        return true
    }
    override fun onSaveInstanceState(outState:Bundle){if(::web.isInitialized)web.saveState(outState);super.onSaveInstanceState(outState)}
    override fun onDestroy(){if(::web.isInitialized)web.destroy();super.onDestroy()}
}
