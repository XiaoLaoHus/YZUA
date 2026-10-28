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
import cn.yzu.timetable.data.urp.UrpHttpDataSource
import cn.yzu.timetable.data.urp.UrpLoginException
import kotlinx.coroutines.*

/** 用户在学校的 HTTPS SSO 页面登录，不读取密码，不注入登录脚本。 */
class SsoLoginActivity : Activity() {
    private lateinit var webView: WebView
    private var completed = false
    private var verifying = false
    private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var verificationSource: UrpHttpDataSource? = null
    private lateinit var status: TextView
    private val account: String get() = intent.getStringExtra("account").orEmpty()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SsoCookieHandoff.clear()
        val manager = CookieManager.getInstance()
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = UrpHttpDataSource.USER_AGENT
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.saveFormData = false
            manager.setAcceptCookie(true)
            manager.setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
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
                    if (!completed && !verifying) {
                        if (SsoPolicy.isReturnUrl(webView.url.orEmpty())) completeLogin(webView.url.orEmpty())
                        else {
                            Toast.makeText(this@SsoLoginActivity, R.string.sso_verifying, Toast.LENGTH_SHORT).show()
                            webView.loadUrl(UrpConfig().baseUrl + UrpConfig().homePath)
                        }
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
        status = TextView(this).apply {
            setText(R.string.sso_return_hint)
            setPadding(padding, padding / 2, padding, padding)
        }
        root.addView(status)
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        // 新登录不复用另一位用户的 WebView 会话。证书错误沿用默认拒绝行为。
        manager.removeAllCookies {
            manager.flush()
            if (!isFinishing) webView.loadUrl(UrpConfig().baseUrl + UrpConfig().loginPath)
        }
    }

    private fun completeLogin(url: String) {
        if (completed || verifying || isFinishing || isDestroyed || !SsoPolicy.isReturnUrl(url) ||
            !SsoPolicy.isReturnUrl(webView.url.orEmpty())) return
        val manager = CookieManager.getInstance()
        val config = UrpConfig()
        verifying = true
        status.setText(R.string.sso_verifying)
        loginScope.launch {
            try {
                val source = UrpHttpDataSource()
                verificationSource = source
                var verifiedCookie: String? = null
                // 等待主页脚本可能触发的会话轮换；失败时保留网页登录现场。
                for (attempt in 0..2) {
                    val cookie = manager.getCookie(config.baseUrl + config.schedulePath).orEmpty()
                    if (cookie.isBlank()) throw UrpLoginException("未读取到教务 Cookie（SSO-COOKIE-EMPTY），请完成学校网页登录后重试")
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
                if (isFinishing || isDestroyed) return@launch
                val cookie = verifiedCookie?.takeIf(String::isNotBlank)
                    ?: throw UrpLoginException("教务会话交接失败（SSO-HANDOFF-EMPTY）")
                completed = true
                SsoCookieHandoff.put(cookie)
                webView.stopLoading()
                // 仅在受保护接口确认成功后，才清理网页登录并返回 App。
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
                status.text = error.message ?: "登录验证失败，请点击返回 App 重试（SSO-VERIFY）"
            } finally {
                verificationSource?.logout()
                verificationSource = null
                verifying = false
            }
        }
    }

    override fun onDestroy() {
        loginScope.cancel()
        verificationSource?.logout()
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
