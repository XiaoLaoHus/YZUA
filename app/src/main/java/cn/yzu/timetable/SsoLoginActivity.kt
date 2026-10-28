package cn.yzu.timetable

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import cn.yzu.timetable.data.urp.SsoCookieHandoff
import cn.yzu.timetable.data.urp.SsoPolicy
import cn.yzu.timetable.data.urp.UrpConfig
import cn.yzu.timetable.data.urp.UrpHttpDataSource
import cn.yzu.timetable.data.urp.UrpLoginException
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Uses the official SSO handlers; users can reveal the browser for challenges. */
class SsoLoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var browserCover: TextView
    private lateinit var manualButton: Button
    private lateinit var returnButton: Button
    private lateinit var flow: SsoLoginWaitPolicy
    private var completed = false
    private var manual = false
    private var pageReady = false
    private var evaluating = false
    private var pageGeneration = 0
    private var account = ""
    private var password = ""
    private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var verificationJob: Job? = null
    private var verificationSource: UrpHttpDataSource? = null
    private val verifying get() = flow.phase == SsoLoginWaitPolicy.Phase.VERIFYING
    private val active get() = !completed && !isFinishing && !isDestroyed

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        account = intent.getStringExtra("account").orEmpty().trim()
        password = intent.getStringExtra("password").orEmpty()
        intent.removeExtra("password")
        // Restored activities must not resubmit credentials saved by Android.
        manual = savedInstanceState != null || intent.getBooleanExtra("manual", false)
        flow = SsoLoginWaitPolicy(SystemClock.elapsedRealtime())
        if (manual) { flow.useManualLogin(); password = "" }
        SsoCookieHandoff.clear()
        val padding = (12 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply {
            setText(if (manual) R.string.sso_manual_hint else R.string.sso_connecting)
            textSize = 16f
            setPadding(padding, padding, padding, padding)
        }
        progress = ProgressBar(this).apply { visibility = if (manual) View.GONE else View.VISIBLE }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            addView(status)
            addView(progress)
        }
        manualButton = Button(this).apply {
            text = if (manual) "重新打开学校网页登录" else "切换学校网页登录"
            setOnClickListener { showManualLogin(reload = manual) }
        }
        returnButton = Button(this).apply {
            setText(R.string.sso_return_to_app)
            visibility = if (manual) View.VISIBLE else View.GONE
            setOnClickListener {
                if (active && !verifying) {
                    flow.useManualLogin()
                    val url = webView.url.orEmpty()
                    if (SsoPolicy.isReturnUrl(url)) completeLogin(url)
                    else webView.loadUrl(UrpConfig().baseUrl + UrpConfig().homePath)
                }
            }
        }
        root.addView(manualButton)
        root.addView(returnButton)
        root.addView(Button(this).apply {
            text = "取消登录"
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        })
        webView = WebView(this).apply {
            // A real phone viewport remains laid out beneath the native cover.
            alpha = if (manual) 1f else 0f
            importantForAccessibility = if (manual) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = UrpHttpDataSource.USER_AGENT
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.saveFormData = false
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageGeneration++
                    pageReady = false
                    evaluating = false
                }
                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                    if (SsoPolicy.isReturnUrl(url)) completeLogin(url)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    val allowed = uri.scheme in setOf("http", "https") &&
                        uri.host in setOf("sso.yzu.edu.cn", "ydjwxs.yzu.edu.cn") && uri.userInfo == null &&
                        (uri.port == -1 || uri.port == if (uri.scheme == "https") 443 else 80) &&
                        (uri.host != "sso.yzu.edu.cn" || uri.scheme == "https")
                    if (!allowed) fail("学校认证跳转到未支持的页面，请返回重试（SSO-NAVIGATION）")
                    return !allowed
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fail("认证页面无法连接，请检查校园网或 VPN（SSO-NETWORK）")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame) fail("认证服务暂时不可用（SSO-HTTP-${errorResponse.statusCode}）")
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    fail("学校认证证书校验失败，请检查设备时间和网络（SSO-TLS）")
                }
            }
        }
        browserCover = TextView(this).apply {
            setText(R.string.sso_native_hint)
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.WHITE)
            isClickable = true
            visibility = if (manual) View.GONE else View.VISIBLE
        }
        root.addView(FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(-1, -1))
            addView(browserCover, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(webView, false)
        loginScope.launch {
            try {
                withTimeout(8_000) { clearBrowserCookies() }
                if (active && flow.phase != SsoLoginWaitPolicy.Phase.FAILED) webView.loadUrl(UrpConfig().ssoLoginUrl())
            } catch (error: TimeoutCancellationException) {
                fail("初始化认证会话超时，请返回重试（SSO-COOKIE-TIMEOUT）")
            }
        }
        loginScope.launch {
            while (active) {
                delay(400)
                if (flow.expire(SystemClock.elapsedRealtime())) {
                    fail("统一认证未在限定时间内完成，请切换学校网页登录查看提示（SSO-TIMEOUT）")
                }
                if (flow.phase in setOf(SsoLoginWaitPolicy.Phase.COMPLETE, SsoLoginWaitPolicy.Phase.FAILED)) continue
                val url = webView.url.orEmpty()
                if (SsoPolicy.isReturnUrl(url) && pageReady) completeLogin(url)
                else if (!manual && pageReady && SsoPolicy.isCredentialUrl(url)) pollSsoPage()
            }
        }
        if (!manual && (account.isBlank() || password.isBlank())) fail("请输入学号和统一认证密码，或切换学校网页登录")
    }

    private fun pollSsoPage() {
        if (!active || evaluating || verifying) return
        evaluating = true
        val generation = pageGeneration
        // Return fixed status codes; never export the school's error text or credentials.
        webView.evaluateJavascript(SsoLoginScript.inspect) inspect@{ result ->
            if (!active || generation != pageGeneration || manual || verifying ||
                flow.phase == SsoLoginWaitPolicy.Phase.FAILED) return@inspect
            evaluating = false
            when (result?.trim('"')) {
                "CAPTCHA" -> { fail("学校要求验证码或二次认证，请切换学校网页登录完成验证（SSO-CHALLENGE）"); return@inspect }
                "ERROR" -> { fail("统一认证页面提示登录未完成，请切换学校网页登录查看原因（SSO-REJECTED）"); return@inspect }
            }
            if (!flow.canSubmit) return@inspect
            evaluating = true
            webView.evaluateJavascript(SsoLoginScript.build(account, password)) submit@{ response ->
                if (!active || generation != pageGeneration || manual || verifying ||
                    flow.phase == SsoLoginWaitPolicy.Phase.FAILED) return@submit
                evaluating = false
                when (response?.trim('"')) {
                    "FILLED" -> status.text = "正在等待学校表单校验…"
                    "READY" -> if (flow.buttonClicked()) {
                        password = ""
                        status.text = "正在等待统一认证返回…"
                        // A redirect may invalidate the JS callback. Claim the attempt first.
                        webView.evaluateJavascript(SsoLoginScript.click) click@{ clicked ->
                            if (!active || generation != pageGeneration || manual || verifying ||
                                flow.phase != SsoLoginWaitPolicy.Phase.WAITING_FOR_SSO) return@click
                            if (clicked?.trim('"') != "CLICKED") {
                                fail("学校登录表单状态发生变化，请切换学校网页登录（SSO-FORM）")
                            }
                        }
                    }
                    "UNTRUSTED" -> fail("登录页面地址发生变化，请切换学校网页登录（SSO-ORIGIN）")
                    "null" -> fail("学校认证页面未能执行，请切换学校网页登录（SSO-SCRIPT）")
                }
            }
        }
    }

    private fun showManualLogin(reload: Boolean) {
        if (!active || verifying) return
        manual = true
        password = ""
        flow.useManualLogin()
        progress.visibility = View.GONE
        webView.alpha = 1f
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        browserCover.visibility = View.GONE
        manualButton.text = "重新打开学校网页登录"
        returnButton.visibility = View.VISIBLE
        returnButton.isEnabled = true
        status.setText(R.string.sso_manual_hint)
        if (reload || webView.url.isNullOrBlank()) webView.loadUrl(UrpConfig().ssoLoginUrl())
    }

    private fun fail(message: String) {
        if (!active) return
        flow.fail()
        password = ""
        webView.stopLoading()
        verificationJob?.cancel()
        verificationSource?.logout()
        progress.visibility = View.GONE
        status.text = message
        manualButton.isEnabled = true
        returnButton.isEnabled = true
    }

    private fun completeLogin(url: String) {
        if (!active || !SsoPolicy.isReturnUrl(url) || !SsoPolicy.isReturnUrl(webView.url.orEmpty())) return
        val now = SystemClock.elapsedRealtime()
        if (flow.expire(now)) {
            fail("统一认证返回超时，请切换学校网页登录重试（SSO-TIMEOUT）")
            return
        }
        if (!flow.beginVerification(now)) return
        password = ""
        manualButton.isEnabled = false
        returnButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "正在验证教务会话…"
        verificationJob = loginScope.launch {
            val source = UrpHttpDataSource()
            verificationSource = source
            try {
                val cookie = withTimeout(25_000) {
                    val config = UrpConfig()
                    var verifiedCookie: String? = null
                    for (attempt in 0..2) {
                        val current = CookieManager.getInstance().getCookie(config.baseUrl + config.schedulePath).orEmpty()
                        if (current.isBlank()) throw UrpLoginException("未读取到教务会话，请重新打开学校网页登录（SSO-COOKIE-EMPTY）")
                        try {
                            val session = source.loginWithSsoCookie(account, current)
                            verifiedCookie = source.verifiedCookieHeader(session)
                            break
                        } catch (error: UrpLoginException) {
                            if (attempt == 2) throw error
                            delay(600)
                        }
                    }
                    val verified = verifiedCookie?.takeIf(String::isNotBlank)
                        ?: throw UrpLoginException("教务会话交接失败，请重试")
                    clearBrowserCookies()
                    verified
                }
                ensureActive()
                if (!active || flow.phase != SsoLoginWaitPolicy.Phase.VERIFYING) return@launch
                flow.complete()
                completed = true
                SsoCookieHandoff.put(cookie)
                setResult(RESULT_OK)
                finish()
            } catch (error: TimeoutCancellationException) {
                fail("教务会话验证超时，请检查校园网或 VPN 后重试（SSO-PROBE-TIMEOUT）")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error.message ?: "统一认证失败，请通过学校网页登录重试")
            } finally {
                source.logout()
                if (verificationSource === source) verificationSource = null
            }
        }
    }

    private suspend fun clearBrowserCookies() = suspendCancellableCoroutine<Unit> { continuation ->
        val manager = CookieManager.getInstance()
        manager.removeAllCookies {
            manager.flush()
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    override fun onDestroy() {
        password = ""
        loginScope.cancel()
        verificationSource?.logout()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearHistory()
            webView.destroy()
        }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        if (!completed) SsoCookieHandoff.clear()
        super.onDestroy()
    }
}
