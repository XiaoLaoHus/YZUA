package cn.yzu.timetable.data.urp

object SsoPolicy {
    fun isCredentialUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host == "sso.yzu.edu.cn" && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.path == "/login"
    }.getOrDefault(false)

    fun isReturnUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme in setOf("http", "https") && uri.host == "ydjwxs.yzu.edu.cn" &&
            uri.userInfo == null && (uri.port == -1 || uri.port == if (uri.scheme == "https") 443 else 80) &&
            uri.path in setOf("/index", "/mobile/index")
    }.getOrDefault(false)
}

/** Activity 之间的单次内存交接，不把 Cookie 放进 Intent/Bundle。 */
object SsoCookieHandoff {
    private var cookie: String? = null
    @Synchronized fun put(value: String) { cookie = value }
    @Synchronized fun take(): String? = cookie.also { cookie = null }
    @Synchronized fun clear() { cookie = null }
}
