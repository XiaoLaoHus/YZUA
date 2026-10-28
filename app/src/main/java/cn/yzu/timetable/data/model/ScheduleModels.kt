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
) {
    fun occursInWeek(week: Int): Boolean = week in weeks
}
