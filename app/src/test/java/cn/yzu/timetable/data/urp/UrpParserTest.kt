package cn.yzu.timetable.data.urp

import org.junit.Assert.*
import org.junit.Test

class UrpParserTest {
    @Test fun parsesDiscontinuousAndOddWeeks() {
        assertEquals(setOf(1, 3, 5, 8, 10, 12), WeekPattern.parse("1-6周(单),8,10-12周(双)"))
        assertEquals(setOf(1, 3, 5), WeekPattern.parse("1010100000"))
    }

    @Test fun rejectsUnknownWeeksInsteadOfInventingSemester() {
        assertThrows(UrpParseException::class.java) { WeekPattern.parse("待定") }
    }

    @Test fun parsesYangzhouJsonWithMaskAndPeriodCount() {
        val payload = """{"xkxx":[{"a":{"courseName":"高等数学","courseNumber":"M01","attendClassTeacher":"王老师*","timeAndPlaceList":[{"classDay":"1","classSessions":"3","continuingSession":"2","classWeek":"101010","campusName":"扬子津","classroomName":"A101"}]}}],"jcsjbs":[{"jc":"3","kssj":"0955","jssj":"1040"}]}"""
        val result = UrpParser.parse(payload)
        val lesson = result.lessons.single()
        assertEquals(3, lesson.startPeriod)
        assertEquals(4, lesson.endPeriod)
        assertTrue(lesson.occursInWeek(3))
        assertFalse(lesson.occursInWeek(2))
        assertEquals("王老师", lesson.teacher)
        assertEquals("扬子津 A101", lesson.classroom)
        assertEquals("09:55", result.periods.single().startTime)
    }

    @Test fun htmlRowspanPreservesCourseAcrossMeetings() {
        val html = """<table><tr><th>课程号</th><th>课程名</th><th>教师</th><th>周次</th><th>星期</th><th>节次</th><th>教室</th></tr><tr><td rowspan="2">M01</td><td rowspan="2">数学</td><td rowspan="2">王老师</td><td>1-16周(单)</td><td>一</td><td>1-2</td><td>A101</td></tr><tr><td>2-16周(双)</td><td>三</td><td>5-6</td><td>A102</td></tr></table>"""
        val lessons = UrpParser.parse(html).lessons
        assertEquals(2, lessons.size)
        assertEquals(setOf(1, 3), lessons.map { it.dayOfWeek }.toSet())
        assertTrue(lessons.all { it.courseName == "数学" })
        assertFalse(lessons.first().occursInWeek(2))
    }

    @Test fun loginAndUnknownPagesAreNeverEmptySchedules() {
        assertThrows(UrpParseException::class.java) { UrpParser.parse("<form><input type=password></form>") }
        assertThrows(UrpParseException::class.java) { UrpParser.parse("{\"error\":\"expired\"}") }
        assertThrows(UrpParseException::class.java) { UrpParser.parse("{\"xkxx\":[]}") }
    }

    @Test fun rejectsPartlyBrokenSchedulesWithoutSilentDataLoss() {
        val json = """{"xkxx":[{"a":{"courseName":"数学","timeAndPlaceList":[{"classDay":1,"classSessions":1,"continuingSession":2,"classWeek":"1111"},{"classDay":9,"classSessions":3,"continuingSession":2,"classWeek":"1111"}]}}]}"""
        assertThrows(UrpParseException::class.java) { UrpParser.parse(json) }
    }
}
