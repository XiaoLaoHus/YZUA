package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.*

data class UrpScheduleResult(
    val lessons: List<ScheduleLesson>,
    val periods: List<PeriodTime> = emptyList(),
    val academicYear: String? = null,
    val termName: String? = null,
    val currentWeek: Int? = null,
)

interface UrpScheduleDataSource {
    suspend fun login(account: String, password: String): UserSession
    suspend fun loginWithSsoCookie(account: String, cookie: String): UserSession
    suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson>
    suspend fun getScores(session: UserSession): List<ScoreRecord>
    suspend fun getAllScores(session: UserSession): List<ScoreRecord> = getScores(session)
    suspend fun getExams(session: UserSession): List<ExamSchedule>
    suspend fun getSemesterDates(session: UserSession, date: java.time.LocalDate): cn.yzu.timetable.data.SemesterDates? = null
    fun logout() {}
}

/** Optional richer contract used by URP implementations that expose period and term metadata. */
interface UrpScheduleResultSource {
    suspend fun getScheduleResult(session: UserSession, term: Term): UrpScheduleResult
}
