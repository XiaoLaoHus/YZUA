package cn.yzu.timetable.data

import cn.yzu.timetable.data.model.ScheduleLesson
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

class AcademicCalendarTest {
    private val dates = SemesterDates("2026-08-31", "2027-01-17", confirmed = true)
    private fun lesson(id: String, day: Int, start: Int, weeks: Set<Int>) =
        ScheduleLesson(id, id, id, "", "", day, start, start, 1..20, weekNumbers = weeks)

    @Test fun todayUsesLocalDateAndActualWeekSetThenSortsByPeriod() {
        val courses = listOf(lesson("late", 3, 8, setOf(9)), lesson("even", 3, 2, setOf(8,10)),
            lesson("early", 3, 1, setOf(9)), lesson("other-day", 4, 1, setOf(9)))
        assertEquals(listOf("early", "late"), AcademicCalendar.todayLessons(LocalDate.parse("2026-10-28"), dates, courses).map { it.id })
        assertTrue(AcademicCalendar.todayLessons(LocalDate.parse("2027-01-20"), dates, courses).isEmpty())
        assertTrue(AcademicCalendar.todayLessons(LocalDate.parse("2026-10-28"), null, courses).isEmpty())
    }

    @Test fun remindersCoverFirstDayLastDayAndExpiredWithoutMidtermNag() {
        assertNotNull(AcademicCalendar.reminder(LocalDate.parse("2026-08-31"), dates, "2026-08-30"))
        assertNull(AcademicCalendar.reminder(LocalDate.parse("2026-10-28"), dates, "2026-09-01"))
        assertNotNull(AcademicCalendar.reminder(LocalDate.parse("2027-01-17"), dates, "2026-09-01"))
        assertNotNull(AcademicCalendar.reminder(LocalDate.parse("2027-01-18"), dates, "2026-09-01"))
    }

    @Test fun schoolWeekCanSuggestDatesButMissingMetadataNeverInventsThem() {
        val dates = AcademicCalendar.fromSchoolHome("<a>2026-2027 秋 第9周 星期三</a>", LocalDate.parse("2026-10-28"))!!
        assertEquals("2026-08-31", dates.firstMonday)
        assertFalse(dates.confirmed)
        assertNull(AcademicCalendar.fromSchoolHome("<html>学校首页</html>", LocalDate.parse("2026-10-28")))
        assertEquals("2026-08-31", AcademicCalendar.fromSchoolHome("第9教学周", LocalDate.parse("2026-10-28"))!!.firstMonday)
    }
}
