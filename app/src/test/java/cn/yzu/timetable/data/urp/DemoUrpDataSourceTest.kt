package cn.yzu.timetable.data.urp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoUrpDataSourceTest {
    private val source = DemoUrpDataSource()
    @Test fun loginReturnsDemoSession() = runBlocking { assertEquals("demo-token", source.login("2210010001", "pass").token) }
    @Test fun scheduleHasMultiWeekCourses() = runBlocking { assertTrue(source.getSchedule(source.login("id", "pw"), cn.yzu.timetable.data.model.Term("2026-spring", "2026 春季" )).any { it.weeks.count() > 1 }) }
    @Test fun blankAccountRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { source.login("", "pw") }
        }
    }
}
