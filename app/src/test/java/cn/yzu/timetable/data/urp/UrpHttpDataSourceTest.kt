package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.Term
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UrpHttpDataSourceTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun keepsCookieAndPostsSelectedTerm() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "JSESSIONID=abc; Path=/").setBody("<form action='/login'><input name='username'><input type='password' name='password'></form>"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>登录成功</html>"))
        server.enqueue(MockResponse().setBody("""{"xkxx":[{"a":{"courseName":"数学","timeAndPlaceList":[{"classDay":"1","classSessions":"1","continuingSession":"1","classWeek":"1"}]}}]}"""))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/'), "/login", "/schedule", "username", "password", "planCode"))
        val session = source.login("221001", "pw")
        assertEquals("221001", session.studentId)
        assertEquals(1, source.getSchedule(session, Term("2026-spring", "2026 春季")).size)
        val landing = server.takeRequest(); assertEquals("/login", landing.path)
        val login = server.takeRequest(); assertTrue(login.body.readUtf8().contains("username=221001"))
        val schedule = server.takeRequest(); assertEquals("planCode=2026-spring", schedule.body.readUtf8()); assertTrue(schedule.getHeader("Cookie")!!.contains("JSESSIONID=abc"))
    }

    @Test fun loginPageIsAnError() {
        runBlocking {
        server.enqueue(MockResponse().setBody("<form><input type=password></form>"))
        val source = UrpHttpDataSource(UrpConfig(server.url("/").toString().trimEnd('/'), "/login", "/schedule"))
        assertThrows(UrpLoginException::class.java) { runBlocking { source.login("id", "pw") } }
        }
    }
}
