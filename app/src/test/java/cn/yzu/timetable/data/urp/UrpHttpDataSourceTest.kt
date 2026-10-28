package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.Term
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UrpHttpDataSourceTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun loginChecksProtectedScheduleApiWithoutDesktopLogoutMarkup() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"xkxx\":[]}"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val session = source.loginWithSsoCookie("", "JSESSIONID=browser")
        assertEquals("统一认证用户", session.studentId)
        val request = server.takeRequest()
        assertEquals("/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback", request.path)
        assertEquals("XMLHttpRequest", request.getHeader("X-Requested-With"))
        assertEquals("JSESSIONID=browser", request.getHeader("Cookie"))
    }

    @Test fun desktopLogoutLinkDoesNotProveApiSessionIsReady() = runBlocking {
        server.enqueue(MockResponse().setBody("<a href='/logout'>注销</a>"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val error = runCatching { source.loginWithSsoCookie("", "JSESSIONID=browser") }.exceptionOrNull()
        assertTrue(error is UrpParseException)
        assertTrue(error!!.message!!.contains("SSO-PROBE-FORMAT"))
    }

    @Test fun ssoRedirectReportsStageWithoutLeakingTicket() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://sso.yzu.edu.cn/login?ticket=fixture-secret"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val error = runCatching { source.loginWithSsoCookie("", "JSESSIONID=browser") }.exceptionOrNull()
        assertTrue(error is UrpLoginException)
        assertTrue(error!!.message!!.contains("SSO-HTTP-302"))
        assertTrue(!error.message!!.contains("fixture-secret"))
    }

    @Test fun verifiedCookieHandoffKeepsServerRotationForNextApiSession() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"xkxx\":[]}").addHeader("Set-Cookie", "JSESSIONID=rotated; Path=/; HttpOnly"))
        val config = UrpConfig(server.url("/").toString().trimEnd('/'))
        val verifier = UrpHttpDataSource(config)
        val first = verifier.loginWithSsoCookie("", "JSESSIONID=initial")
        val header = verifier.verifiedCookieHeader(first)
        verifier.logout()
        server.enqueue(MockResponse().setBody("{\"xkxx\":[]}"))
        val source = UrpHttpDataSource(config)
        source.loginWithSsoCookie("", header)
        server.takeRequest()
        assertEquals("JSESSIONID=rotated", server.takeRequest().getHeader("Cookie"))
        source.logout()
        assertTrue(runCatching { source.verifiedCookieHeader(first) }.exceptionOrNull() is UrpLoginException)
    }

    @Test fun loginFlagCannotBeAcceptedAsAnEmptyTimetable() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"login\":true,\"xkxx\":[]}"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val error = runCatching { source.loginWithSsoCookie("", "JSESSIONID=browser") }.exceptionOrNull()
        assertTrue(error is UrpLoginException)
        assertTrue(error!!.message!!.contains("SSO-PROBE-LOGIN"))
    }

    @Test fun ssoCookieIsReusedForScheduleScoresAndExams() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        server.enqueue(MockResponse().setBody("""{"xkxx":[{"a":{"courseName":"数学","timeAndPlaceList":[{"classDay":"1","classSessions":"1","continuingSession":"1","classWeek":"1"}]}}]}"""))
        server.enqueue(MockResponse().setBody("<script>var url=\"/student/integratedQuery/scoreQuery/ABC/thisTermScores/data\"</script>"))
        server.enqueue(MockResponse().setBody("[{\"state\":\"1\",\"list\":[{\"id\":{\"courseNumber\":\"M01\"},\"courseName\":\"数学\",\"courseScore\":\"90\"}]}]"))
        server.enqueue(MockResponse().setBody("<script>var events='/student/examinationManagement/examPlan/detail';</script>"))
        server.enqueue(MockResponse().setBody("[]"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/'), "/sigin", "/schedule"))
        val session = source.loginWithSsoCookie("221001", "JSESSIONID=abc; other=value")
        assertEquals(1, source.getSchedule(session, Term("2026-spring", "2026 春季")).size)
        assertEquals("90", source.getScores(session).single().score)
        assertTrue(source.getExams(session).isEmpty())
        val expectedPaths = listOf("/schedule", "/schedule", "/student/integratedQuery/scoreQuery/thisTermScores/index", "/student/integratedQuery/scoreQuery/ABC/thisTermScores/data", "/student/examinationManagement/examPlan/index", "/student/examinationManagement/examPlan/detail")
        repeat(6) { index ->
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(expectedPaths[index], request.requestUrl!!.encodedPath)
            if (index == 5) {
                val start = java.time.LocalDate.parse(request.requestUrl!!.queryParameter("start"))
                val end = java.time.LocalDate.parse(request.requestUrl!!.queryParameter("end"))
                assertTrue(java.time.temporal.ChronoUnit.DAYS.between(start, end) in 28..366)
            }
            assertTrue(request.getHeader("Cookie")!!.contains("JSESSIONID=abc"))
        }
    }

    @Test fun examEmptyPageIsAValidEmptyResultWithoutParsingBrokenDetail() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        server.enqueue(MockResponse().setBody("<a href='/logout'>注销</a><div>还没有和你相关的考试信息！</div>"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val session = source.loginWithSsoCookie("", "JSESSIONID=first")
        assertTrue(source.getExams(session).isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun lateOldResponseCannotReplaceNewSessionCookie() = runBlocking {
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        val old = source.loginWithSsoCookie("old", "JSESSIONID=old")
        server.takeRequest()
        server.enqueue(MockResponse().setHeadersDelay(700, java.util.concurrent.TimeUnit.MILLISECONDS).addHeader("Set-Cookie", "JSESSIONID=late; Path=/").setBody("{}"))
        val pending = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).async { runCatching { source.getSchedule(old, Term("current", "当前")) } }
        assertEquals("/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback", server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)!!.path)
        source.logout()
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        val fresh = source.loginWithSsoCookie("fresh", "JSESSIONID=fresh")
        pending.await()
        server.enqueue(MockResponse().setBody("<script>var url='/student/integratedQuery/scoreQuery/NEW/thisTermScores/data';</script>"))
        server.enqueue(MockResponse().setBody("[{\"state\":\"1\",\"list\":[]}]"))
        assertTrue(source.getScores(fresh).isEmpty())
        repeat(3) { assertEquals("JSESSIONID=fresh", server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)!!.getHeader("Cookie")) }
    }

    @Test fun rejectsHttp200LoginPageAndClearsCookie() = runBlocking {
        server.enqueue(MockResponse().setBody("<form><input type='password' name='password'></form>"))
        val jar = MemoryCookieJar()
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')), UrpHttpDataSource.defaultClient(jar))
        assertTrue(runCatching { source.loginWithSsoCookie("", "JSESSIONID=test") }.exceptionOrNull() is UrpLoginException)
        assertTrue(jar.loadForRequest(server.url("/")).isEmpty())
    }

    @Test fun missingScoreEndpointIsAnErrorInsteadOfEmptyScores() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        server.enqueue(MockResponse().setBody("<html>系统维护</html>"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val session = source.loginWithSsoCookie("", "JSESSIONID=test")
        assertTrue(runCatching { source.getScores(session) }.exceptionOrNull() is UrpParseException)
        assertEquals(2, server.requestCount)
    }

    @Test fun passwordLoginIsRejectedBecauseYangzhouUsesSso() {
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        assertTrue(runCatching { runBlocking { source.login("id", "pw") } }.exceptionOrNull() is UrpLoginException)
    }

    @Test fun allTermScoresUseDynamicPostEndpointAndFetchEveryPage() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"xkxx":[]}"""))
        server.enqueue(MockResponse().setBody("<script>var url='/student/integratedQuery/scoreQuery/ROTATING/allTermScores/data';</script><form name='queryInfo'><select name='zxjxjhh'><option value=''>全部</option></select><select name='cjlx'><option value='1'>替代前成绩</option></select><input name='kcm'><input name='kch'></form>"))
        server.enqueue(MockResponse().setBody("""{"list":{"pageContext":{"totalCount":2},"records":[{"KCH":"TEST1","KCM":"测试甲","KCCJ":80,"LRZTDM":"05"}]}}"""))
        server.enqueue(MockResponse().setBody("""{"list":{"pageContext":{"totalCount":2},"records":[{"KCH":"TEST2","KCM":"测试乙","KCCJ":90,"LRZTDM":"05"}]}}"""))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/')))
        val session = source.loginWithSsoCookie("", "JSESSIONID=test")
        assertEquals(listOf("测试甲", "测试乙"), source.getAllScores(session).map { it.courseName })
        server.takeRequest(); server.takeRequest()
        repeat(2) { i ->
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/student/integratedQuery/scoreQuery/ROTATING/allTermScores/data", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("pageNum=${i + 1}"))
            assertTrue(body.contains("zxjxjhh=&"))
            assertTrue(body.contains("cjlx=1"))
        }
    }
}
