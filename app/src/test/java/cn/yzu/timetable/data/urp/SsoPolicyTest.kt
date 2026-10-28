package cn.yzu.timetable.data.urp

import org.junit.Assert.*
import org.junit.Test

class SsoPolicyTest {
    @Test fun desktopAndMobileUrpHomesCompleteAuthentication() {
        assertTrue(SsoPolicy.isReturnUrl("http://ydjwxs.yzu.edu.cn/index"))
        assertTrue(SsoPolicy.isReturnUrl("http://ydjwxs.yzu.edu.cn/mobile/index"))
        assertTrue(SsoPolicy.isReturnUrl("https://ydjwxs.yzu.edu.cn/mobile/index?from=sso"))
        listOf(
            "http://ydjwxs.yzu.edu.cn/mobile/index/other",
            "http://ydjwxs.yzu.edu.cn.evil.example/index",
            "http://ydjwxs.yzu.edu.cn@evil.example/index",
            "http://ydjwxs.yzu.edu.cn:9000/index",
            "http://ydjwxs.yzu.edu.cn/student/test",
            "https://sso.yzu.edu.cn/login",
            "https://ydjwxs.yzu.edu.cn.evil.example/mobile/index",
            "http://ydjwxs.yzu.edu.cn:9000/mobile/index",
        ).forEach { assertFalse(it, SsoPolicy.isReturnUrl(it)) }
    }

    @Test fun cookieHandoffIsConsumedOnceAndIsNeverInToString() {
        SsoCookieHandoff.clear()
        SsoCookieHandoff.put("JSESSIONID=fixture")
        assertFalse(SsoCookieHandoff.toString().contains("fixture"))
        assertEquals("JSESSIONID=fixture", SsoCookieHandoff.take())
        assertNull(SsoCookieHandoff.take())
    }
}
