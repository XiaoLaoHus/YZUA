package cn.yzu.timetable.data.urp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrpScheduleMetadataTest {
    @Test fun parsesAcademicYearTermWeekAndPeriodTimesFromSchedulePayload() {
        val payload = """{"xnxq":"2026-2027学年秋","currentWeek":"第9教学周","xkxx":[{"a":{"courseName":"高等数学","courseNumber":"M01","timeAndPlaceList":[{"classDay":"1","classSessions":"3","continuingSession":"2","classWeek":"111111111111111111"}]}}],"jcsjbs":[{"jc":"3","kssj":"0955","jssj":"1040"}]}"""

        val parsed = UrpParser.parse(payload)

        assertEquals("2026-2027", parsed.academicYear)
        assertEquals("秋", parsed.termName)
        assertEquals(9, parsed.currentWeek)
        assertEquals("09:55", parsed.periods.single().startTime)
        assertEquals("10:40", parsed.periods.single().endTime)
    }

    @Test fun missingMetadataRemainsUnknownInsteadOfGuessing() {
        val payload = """{"xkxx":[{"a":{"courseName":"课程","timeAndPlaceList":[{"classDay":1,"classSessions":1,"classWeek":"1"}]}}]}"""

        val parsed = UrpParser.parse(payload)

        assertTrue(parsed.academicYear == null)
        assertTrue(parsed.termName == null)
        assertTrue(parsed.currentWeek == null)
    }
}
