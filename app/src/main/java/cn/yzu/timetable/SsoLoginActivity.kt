package cn.yzu.timetable

import android.app.Activity
import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import cn.yzu.timetable.data.urp.SsoCookieHandoff
import cn.yzu.timetable.data.urp.SsoPolicy
import cn.yzu.timetable.data.urp.UrpConfig

/** 用户在学校的 HTTPS SSO 页面登录，不读取密码，不注入登录脚本。 */
class SsoLoginActivity : Activity() {
    private lateinit var webView: WebView
    private var completed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SsoCookieHandoff.clear()
        val manager = CookieManager.getInstance()
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.saveFormData = false
            manager.setAcceptCookie(true)
            manager.setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!completed && SsoPolicy.isReturnUrl(url)) {
                        val cookie = manager.getCookie(UrpConfig().baseUrl).orEmpty()
                        if (cookie.isNotBlank()) {
                            completed = true
                            SsoCookieHandoff.put(cookie)
                            // Cookie 转入内存后清空 WebView 的持久 Cookie 存储。
                            manager.removeAllCookies {
                                manager.flush()
                                setResult(RESULT_OK)
                                finish()
                            }
                        }
                    }
                }
            }
        }
        setContentView(webView)
        // 新登录不复用另一位用户的 WebView 会话。证书错误沿用默认拒绝行为。
        manager.removeAllCookies {
            manager.flush()
            if (!isFinishing) webView.loadUrl(UrpConfig().baseUrl + UrpConfig().loginPath)
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearHistory()
        webView.destroy()
        android.webkit.WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        if (!completed) SsoCookieHandoff.clear()
        super.onDestroy()
    }
}
