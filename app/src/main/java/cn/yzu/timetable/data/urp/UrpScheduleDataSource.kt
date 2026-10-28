package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.data.model.ExamSchedule
import cn.yzu.timetable.data.model.ScoreRecord
import cn.yzu.timetable.data.model.Term
import cn.yzu.timetable.data.model.UserSession

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
