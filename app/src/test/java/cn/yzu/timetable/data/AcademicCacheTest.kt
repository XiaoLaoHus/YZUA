package cn.yzu.timetable.data

import cn.yzu.timetable.data.model.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class AcademicCacheTest {
    @Test fun fileRoundTripKeepsWeekSetAndBothScoreScopes() {
        val dir = Files.createTempDirectory("yzua-cache-test").toFile()
        try {
            val file = dir.resolve("academic.json")
            val score = ScoreRecord("fixture", "1", "合成课程", "2", "必修", "", "", "", "优秀", "", "")
            val snapshot = AcademicSnapshot(owner = "fixture", semester = SemesterDates("2026-08-31", "2027-01-17"),
                periods = listOf(PeriodTime(3, "09:55", "10:40")), academicYear = "2026-2027", currentWeek = 9,
                lessons = listOf(ScheduleLesson("fixture", "F", "合成课程", "教师", "教室", 3, 1, 2, 1..19, weekNumbers = setOf(1, 3, 9, 19))),
                scheduleUpdated = "2026-10-28", termScores = listOf(score), termScoresUpdated = "2026-10-28",
                allScoresUpdated = "2026-10-28", examsUpdated = "2026-10-28", onboarded = true)
            AcademicCache(file).write(snapshot)
            val read = AcademicCache(file).read()
            assertNull(read.error)
            assertEquals(snapshot, read.snapshot)
            assertFalse(file.readText().contains("token"))
            assertFalse(file.readText().contains("Cookie"))
            assertEquals(emptyList<ScoreRecord>(), read.snapshot.allScores)
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptOrFutureCacheIsReportedWithoutCrashing() {
        val dir = Files.createTempDirectory("yzua-cache-test").toFile()
        try {
            val file = dir.resolve("academic.json")
            for (payload in listOf("broken", "{\"version\":99}", "{\"version\":1,\"lessons\":null}")) {
                file.writeText(payload)
                assertNotNull(AcademicCache(file).read().error)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun explicitNullScoreAndExamListsAreRejectedOrNormalized() {
        val dir = Files.createTempDirectory("yzua-cache-test").toFile()
        try {
            val file = dir.resolve("academic.json")
            file.writeText("""{"version":1,"lessons":[],"termScores":null,"allScores":null,"exams":null}""")
            val read = AcademicCache(file).read()
            assertNull(read.error)
            assertTrue(read.snapshot.termScores.isEmpty())
            assertTrue(read.snapshot.allScores.isEmpty())
            assertTrue(read.snapshot.exams.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
