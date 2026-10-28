package cn.yzu.timetable

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import cn.yzu.timetable.data.urp.SsoCookieHandoff
import cn.yzu.timetable.data.urp.SsoPolicy
import cn.yzu.timetable.data.urp.UrpConfig
import cn.yzu.timetable.data.urp.UrpHttpDataSource
import cn.yzu.timetable.data.urp.UrpLoginException
import kotlinx.coroutines.*

/** Native credential form with an invisible WebView used only as the SSO protocol client. */
class SsoLoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var completed = false
    private var verifying = false
    private var submitted = false
    private var submitAttempts = 0
    private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var verificationSource: UrpHttpDataSource? = null
    private val account: String get() = intent.getStringExtra("account").orEmpty().trim()
    private val password: String get() = intent.getStringExtra("password").orEmpty()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SsoCookieHandoff.clear()
        status = TextView(this).apply {
            text = "正在连接扬州大学统一认证…"
            textSize = 16f
            setPadding(36, 36, 36, 36)
        }
        val progress = ProgressBar(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
        if (account.isBlank() || password.isBlank()) {
            status.text = "请输入学号和统一认证密码"
            return
        }
        webView = WebView(this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
            layoutParams = LinearLayout.LayoutParams(1, 1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = UrpHttpDataSource.USER_AGENT
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.saveFormData = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (SsoPolicy.isReturnUrl(url)) completeLogin(url) else scheduleSubmit()
                }
            }
        }
        root.addView(webView)
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(webView, false)
        manager.removeAllCookies {
            manager.flush()
            if (!isFinishing && !isDestroyed) webView.loadUrl(UrpConfig().ssoLoginUrl())
        }
    }

    private fun scheduleSubmit() {
        if (submitted || isFinishing || isDestroyed) return
        if (submitAttempts++ > 120) {
            status.text = "统一认证页面未响应，请检查网络或改用学校网页登录"
            return
        }
        webView.postDelayed({
            if (isFinishing || isDestroyed || submitted) return@postDelayed
            webView.evaluateJavascript(SsoLoginScript.build(account, password)) { result ->
                when (result?.trim('"')) {
                    "SUBMITTED" -> {
                        submitted = true
                        status.text = "正在验证统一认证…"
                    }
                    else -> scheduleSubmit()
                }
            }
        }, 250L)
    }

    private fun completeLogin(url: String) {
        if (completed || verifying || isFinishing || isDestroyed || !SsoPolicy.isReturnUrl(url)) return
        val manager = CookieManager.getInstance()
        val config = UrpConfig()
        verifying = true
        status.text = "正在验证教务会话…"
        loginScope.launch {
            try {
                val source = UrpHttpDataSource()
                verificationSource = source
                var verifiedCookie: String? = null
                for (attempt in 0..2) {
                    val cookie = manager.getCookie(config.baseUrl + config.schedulePath).orEmpty()
                    if (cookie.isBlank()) throw UrpLoginException("统一认证已返回，但未读取到教务 Cookie")
                    try {
                        val session = source.loginWithSsoCookie(account, cookie)
                        verifiedCookie = source.verifiedCookieHeader(session)
                        break
                    } catch (error: UrpLoginException) {
                        if (attempt == 2) throw error
                        delay(600)
                    }
                }
                ensureActive()
                val cookie = verifiedCookie?.takeIf(String::isNotBlank)
                    ?: throw UrpLoginException("教务会话交接失败，请重试")
                completed = true
                SsoCookieHandoff.put(cookie)
                webView.stopLoading()
                manager.removeAllCookies {
                    manager.flush()
                    if (!isFinishing && !isDestroyed) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = error.message ?: "统一认证失败，请检查学号、密码或校园网/VPN"
                Toast.makeText(this@SsoLoginActivity, status.text, Toast.LENGTH_LONG).show()
            } finally {
                verificationSource?.logout()
                verificationSource = null
                verifying = false
            }
        }
    }

    override fun onBackPressed() {
        if (!completed) {
            SsoCookieHandoff.clear()
            setResult(RESULT_CANCELED)
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        loginScope.cancel()
        verificationSource?.logout()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearHistory()
            webView.destroy()
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        if (!completed) SsoCookieHandoff.clear()
        super.onDestroy()
    }
}
