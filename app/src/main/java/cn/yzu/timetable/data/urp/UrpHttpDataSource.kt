package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 学校 WebView 登录后导入的 Cookie 仅保存在进程内。 */
class UrpHttpDataSource(
    private val config: UrpConfig = UrpConfig(),
    private val client: OkHttpClient = defaultClient(),
) : UrpScheduleDataSource {
    private val baseUrl = config.baseUrl.trimEnd('/').toHttpUrl()
    private val sessionLock = Any()
    @Volatile private var transport: SessionTransport? = null
    private class SessionTransport(val client: OkHttpClient, val jar: MemoryCookieJar) {
        val token = java.util.UUID.randomUUID().toString()
        val calls = mutableSetOf<Call>()
        var retired = false
        fun retire() = synchronized(calls) {
            retired = true
            jar.retire()
            calls.forEach(Call::cancel)
        }
    }

    override suspend fun login(account: String, password: String): UserSession =
        throw UrpLoginException("扬大教务使用统一认证，请使用统一认证登录")

    override suspend fun loginWithSsoCookie(account: String, cookie: String): UserSession = withContext(Dispatchers.IO) {
        require(cookie.isNotBlank()) { "统一认证会话为空，请重新登录" }
        val jar = MemoryCookieJar()
        jar.replaceFromHeader(baseUrl, cookie)
        val current = SessionTransport(client.newBuilder().cookieJar(jar).build(), jar)
        synchronized(sessionLock) { transport?.retire(); transport = current }
        try {
            val html = read(current, config.homePath)
            val document = Jsoup.parse(html)
            if (document.select("a[href='/logout']").isEmpty()) sessionExpired(current)
            synchronized(sessionLock) {
                if (transport !== current) throw UrpLoginException("登录已取消，请重新登录")
                UserSession(account.trim().ifBlank { "统一认证用户" }, "扬大学生", current.token, ScheduleSource.URP)
            }
        } catch (error: Exception) {
            synchronized(sessionLock) { if (transport === current) transport = null }
            current.retire()
            throw error
        }
    }

    override suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson> = withContext(Dispatchers.IO) {
        val current = ensureSession(session)
        UrpParser.parse(read(current, config.schedulePath, xhr = true)).lessons
    }

    override suspend fun getScores(session: UserSession): List<ScoreRecord> = withContext(Dispatchers.IO) {
        val current = ensureSession(session)
        val page = read(current, config.scorePagePath)
        val path = Regex("""/student/integratedQuery/scoreQuery/[A-Za-z0-9_-]+/thisTermScores/data""")
            .find(page)?.value ?: throw UrpParseException("成绩页面未提供查询接口，请稍后重试")
        UrpParser.parseScores(read(current, path, xhr = true))
    }

    override suspend fun getExams(session: UserSession): List<ExamSchedule> = withContext(Dispatchers.IO) {
        val current = ensureSession(session)
        val page = read(current, config.examPagePath)
        if (Jsoup.parse(page).text().contains("还没有和你相关的考试信息")) return@withContext emptyList()
        if (!page.contains(config.examPath)) throw UrpParseException("考试页面未提供事件接口")
        // 与网页 FullCalendar 一样查询当前月份前后各三个月，避免无界查询。
        val today = LocalDate.now()
        val url = resolve(config.examPath).newBuilder()
            .addQueryParameter("start", today.minusMonths(3).withDayOfMonth(1).toString())
            .addQueryParameter("end", today.plusMonths(4).withDayOfMonth(1).toString()).build()
        UrpParser.parseExams(read(current, url, xhr = true))
    }

    override suspend fun getAllScores(session: UserSession): List<ScoreRecord> = withContext(Dispatchers.IO) {
        val current = ensureSession(session)
        val page = read(current, config.allScorePagePath)
        val path = Regex("""/student/integratedQuery/scoreQuery/[A-Za-z0-9_-]+/allTermScores/data""").find(page)?.value
            ?: throw UrpParseException("历年成绩页面未提供查询接口")
        val form = Jsoup.parse(page).selectFirst("form[name=queryInfo]") ?: throw UrpParseException("历年成绩缺少查询表单")
        val fields = linkedMapOf<String, String>()
        form.select("input[name]").forEach { fields[it.attr("name")] = it.attr("value") }
        form.select("select[name]").forEach { fields[it.attr("name")] = (it.selectFirst("option[selected]") ?: it.selectFirst("option"))?.attr("value").orEmpty() }
        fields["zxjxjhh"] = ""
        val records = mutableListOf<ScoreRecord>()
        var number = 1
        do {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) }; add("pageNum", number.toString()); add("pageSize", "300") }.build()
            val result = UrpParser.parseAllTermScores(read(current, resolve(path), xhr = true, form = body))
            if (result.totalCount > 10000) throw UrpParseException("历年成绩记录过多，请在学校网站查询")
            records += result.records
            if (records.size >= result.totalCount) break
            if (result.records.isEmpty() || number >= 100) throw UrpParseException("历年成绩分页不完整，请重试")
            number++
        } while (true)
        records
    }

    override fun logout() { synchronized(sessionLock) { transport?.retire(); transport = null } }

    private fun ensureSession(session: UserSession): SessionTransport {
        val current = transport
        if (current == null || current.token != session.token) throw UrpLoginException("教务会话已失效，请重新登录")
        return current
    }

    private fun sessionExpired(current: SessionTransport): Nothing {
        synchronized(sessionLock) { if (transport === current) transport = null }
        current.retire()
        throw UrpLoginException("教务会话已失效，请重新登录")
    }

    private fun resolve(path: String): HttpUrl {
        val url = baseUrl.resolve(path) ?: throw UrpParseException("教务接口地址无效")
        require(url.host == baseUrl.host && url.port == baseUrl.port && url.scheme == baseUrl.scheme) { "教务接口必须同源" }
        return url
    }

    private fun read(current: SessionTransport, path: String, xhr: Boolean = false) = read(current, resolve(path), xhr)

    private fun read(current: SessionTransport, url: HttpUrl, xhr: Boolean, form: FormBody? = null): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", if (xhr) "application/json, text/javascript, */*; q=0.01" else "text/html,application/xhtml+xml")
            .apply { if (xhr) header("X-Requested-With", "XMLHttpRequest"); if (form == null) get() else post(form) }.build()
        val call = current.client.newCall(request)
        synchronized(current.calls) {
            if (current.retired) throw UrpLoginException("教务会话已结束")
            current.calls.add(call)
        }
        val response = try { call.execute() } catch (_: IOException) {
            throw IOException("无法连接扬大教务，请检查校园网或 VPN")
        } finally { synchronized(current.calls) { current.calls.remove(call) } }
        return response.use {
            if (transport !== current) throw UrpLoginException("教务会话已结束")
            if (it.code == 401 || it.code == 403 || it.code in 300..399) sessionExpired(current)
            if (!it.isSuccessful) throw IOException("教务查询失败（HTTP ${it.code}），请稍后重试")
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) throw UrpParseException("教务系统返回空响应")
            if (body.trimStart().startsWith("<")) {
                val document = Jsoup.parse(body)
                if (document.select("input[type=password], form[action*=sso], form[action*=login]").isNotEmpty() ||
                    document.title().contains("统一身份认证")) sessionExpired(current)
            }
            body
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36 YZUA/0.3"
        fun defaultClient(jar: MemoryCookieJar = MemoryCookieJar()): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(jar).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}

class UrpLoginException(message: String) : IOException(message)

class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    private var retired = false
    override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) { synchronized(cookies) {
        if (retired) return
        cookies.removeAll { old -> values.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
        cookies += values.filter { it.expiresAt > System.currentTimeMillis() }
    } }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        cookies.filter { it.matches(url) }
    }
    fun replaceFromHeader(url: HttpUrl, header: String) { synchronized(cookies) {
        cookies.clear()
        header.split(';').map(String::trim).mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            Cookie.Builder().name(pair.substring(0, index)).value(pair.substring(index + 1))
                .hostOnlyDomain(url.host).path("/").apply { if (url.isHttps) secure() }.build()
        }.forEach(cookies::add)
    } }
    fun clear() = synchronized(cookies) { cookies.clear() }
    fun retire() = synchronized(cookies) { retired = true; cookies.clear() }
}
