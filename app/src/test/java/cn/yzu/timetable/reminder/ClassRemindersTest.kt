package cn.yzu.timetable.reminder

import cn.yzu.timetable.data.AcademicSnapshot
import cn.yzu.timetable.data.SemesterDates
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.components.SeasonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClassRemindersTest {
    private val semester = SemesterDates("2026-08-31", "2027-01-17", "test", true)
    private fun lesson(day: Int, start: Int, end: Int, weeks: Set<Int> = (1..16).toSet()) =
        ScheduleLesson("id$day$start", "C", "课程$start", "老师", "教室", day, start, end, 1..16, 0, weeks)

    @Test fun autumnAfternoonUsesWinterTimesAndLead() {
        // 2026-10-28 是周三、第 9 周；秋冬季第 6 节 14:00 开始，第 8 节 16:35 结束
        val snapshot = AcademicSnapshot(semester = semester, lessons = listOf(lesson(3, 6, 8)))
        val events = ClassReminders.events(snapshot, LocalDateTime.of(2026, 10, 28, 7, 0), 0, ClassReminders.Settings(true, 15, true, SeasonMode.Auto))
        assertEquals(listOf(ClassReminders.Kind.Remind, ClassReminders.Kind.Mute, ClassReminders.Kind.Unmute), events.map { it.kind })
        assertEquals(LocalDateTime.of(2026, 10, 28, 13, 45), events[0].at)
        assertEquals(LocalDateTime.of(2026, 10, 28, 14, 0), events[1].at)
        assertEquals(LocalDateTime.of(2026, 10, 28, 16, 35), events[2].at)
    }

    @Test fun septemberUsesSummerTimes() {
        // 2026-09-02 周三第 1 周，春夏季第 6 节 14:30
        val snapshot = AcademicSnapshot(semester = semester, lessons = listOf(lesson(3, 6, 7)))
        val events = ClassReminders.events(snapshot, LocalDateTime.of(2026, 9, 2, 7, 0), 0, ClassReminders.Settings(true, 10, false, SeasonMode.Auto))
        assertEquals(listOf(LocalDateTime.of(2026, 9, 2, 14, 20)), events.map { it.at })
    }

    @Test fun skipsWeeksWithoutLessonAndDisabledFeatures() {
        val snapshot = AcademicSnapshot(semester = semester, lessons = listOf(lesson(3, 1, 2, weeks = setOf(1, 3, 5))))
        val week9 = ClassReminders.events(snapshot, LocalDateTime.of(2026, 10, 28, 6, 0), 0, ClassReminders.Settings(true, 15, true, SeasonMode.Auto))
        assertTrue(week9.isEmpty())
        val off = ClassReminders.events(snapshot, LocalDateTime.of(2026, 9, 2, 6, 0), 0, ClassReminders.Settings(false, 15, false, SeasonMode.Auto))
        assertTrue(off.isEmpty())
    }
}
