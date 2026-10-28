package cn.yzu.timetable.data.model

enum class ScheduleSource(val label: String) { DEMO("演示数据"), URP("扬大 URP") }

data class Term(val id: String, val label: String, val totalWeeks: Int = 20)

data class UserSession(val studentId: String, val displayName: String, val token: String, val source: ScheduleSource)

data class ScheduleLesson(
    val id: String,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val classroom: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: IntRange,
    val colorIndex: Int = 0,
    val weekNumbers: Set<Int> = weeks.toSet(),
) {
    fun occursInWeek(week: Int): Boolean = week in weekNumbers
}

data class ScoreRecord(
    val courseCode: String,
    val courseSequence: String,
    val courseName: String,
    val credit: String,
    val property: String,
    val maxScore: String,
    val minScore: String,
    val averageScore: String,
    val score: String,
    val unpassedReason: String,
    val englishCourseName: String,
    val termLabel: String = "",
)

data class ExamSchedule(
    val courseName: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val note: String,
)
