package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.data.model.ScheduleSource
import cn.yzu.timetable.data.model.Term
import cn.yzu.timetable.data.model.UserSession
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cookie 会话只保存在进程内；不写入账号、密码或 Cookie。 */
class UrpHttpDataSource(
    private val config: UrpConfig = UrpConfig(),
    private val client: OkHttpClient = defaultClient(),
) : UrpScheduleDataSource {
    private val baseUrl = config.baseUrl.trimEnd('/')
    private var loggedInAccount: String? = null

    override suspend fun login(account: String, password: String): UserSession {
        require(account.isNotBlank()) { "请输入学号" }
        require(password.isNotBlank()) { "请输入密码" }
        val form = FormBody.Builder().add(config.accountField, account.trim()).add(config.passwordField, password).build()
        val request = Request.Builder().url(baseUrl + config.loginPath).post(form).header("User-Agent", USER_AGENT).build()
        val response = execute(request)
        val body = response.body?.string().orEmpty()
        if (response.code in 300..399 || body.contains("验证码") || body.contains("captcha", ignoreCase = true)) throw UrpLoginException("教务系统需要验证码，请先在校园网页完成登录")
        if (!response.isSuccessful || looksLikeLoginPage(body)) throw UrpLoginException("学号或密码错误，或登录地址需要调整")
        loggedInAccount = account.trim()
        return UserSession(account.trim(), "扬大学生", "cookie-session", ScheduleSource.URP)
    }

    override suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson> {
        check(loggedInAccount == session.studentId) { "教务会话已失效，请重新登录" }
        val request = Request.Builder().url(baseUrl + config.schedulePath).post(FormBody.Builder().add(config.termField, term.id).build()).header("Accept", "application/json, text/javascript, */*; q=0.01").header("X-Requested-With", "XMLHttpRequest").header("User-Agent", USER_AGENT).build()
        val response = execute(request)
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || looksLikeLoginPage(body)) { loggedInAccount = null; throw UrpLoginException("教务会话已失效，请重新登录") }
        return UrpParser.parse(body).lessons
    }

    private fun execute(request: Request) = try { client.newCall(request).execute() } catch (error: IOException) { throw UrpLoginException("连接扬大教务系统失败：${error.message ?: "网络不可用"}") }
    private fun looksLikeLoginPage(body: String) = body.contains("<input", true) && body.contains("password", true) && !body.contains("courseName")

    companion object {
        private const val USER_AGENT = "YZUA/0.2 (Android; Yangzhou University timetable)"
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().cookieJar(MemoryCookieJar()).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    }
}

class UrpLoginException(message: String) : IOException(message)

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) { synchronized(cookies) { cookies.removeAll { it.matches(url) }; cookies += values.filter { it.expiresAt > System.currentTimeMillis() } } }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) { cookies.filter { it.expiresAt > System.currentTimeMillis() }.filter { it.matches(url) } }
}
