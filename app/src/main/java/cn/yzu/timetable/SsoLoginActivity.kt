package cn.yzu.timetable

import android.app.Activity
import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
                override fun onPageCommitVisible(view: WebView, url: String) {
                    completeLogin(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    completeLogin(url)
                }
            }
        }
        val padding = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            setText(R.string.sso_title)
            textSize = 18f
            setPadding(padding, padding, padding, padding / 2)
        })
        root.addView(LinearLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(Button(this@SsoLoginActivity).apply {
                setText(R.string.sso_return)
                setOnClickListener {
                    if (!completed) {
                        // 手机端可能停留在课表子页；先回同源首页，再交接并由 API 验证会话。
                        Toast.makeText(this@SsoLoginActivity, R.string.sso_verifying, Toast.LENGTH_SHORT).show()
                        webView.loadUrl(UrpConfig().baseUrl + UrpConfig().homePath)
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@SsoLoginActivity).apply {
                setText(R.string.sso_cancel)
                setOnClickListener {
                    if (!completed) {
                        SsoCookieHandoff.clear()
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        root.addView(TextView(this).apply {
            setText(R.string.sso_return_hint)
            setPadding(padding, padding / 2, padding, padding)
        })
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        // 新登录不复用另一位用户的 WebView 会话。证书错误沿用默认拒绝行为。
        manager.removeAllCookies {
            manager.flush()
            if (!isFinishing) webView.loadUrl(UrpConfig().baseUrl + UrpConfig().loginPath)
        }
    }

    private fun completeLogin(url: String) {
        if (completed || isFinishing || isDestroyed || !SsoPolicy.isReturnUrl(url)) return
        val manager = CookieManager.getInstance()
        val cookie = manager.getCookie(UrpConfig().baseUrl).orEmpty()
        if (cookie.isBlank()) return
        completed = true
        SsoCookieHandoff.put(cookie)
        webView.stopLoading()
        // Cookie 转入内存后清空 WebView 的持久 Cookie 存储。
        manager.removeAllCookies {
            manager.flush()
            if (!isFinishing && !isDestroyed) {
                setResult(RESULT_OK)
                finish()
            }
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
