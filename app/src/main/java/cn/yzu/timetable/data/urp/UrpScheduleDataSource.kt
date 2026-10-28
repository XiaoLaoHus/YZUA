package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.data.model.Term
import cn.yzu.timetable.data.model.UserSession

interface UrpScheduleDataSource {
    suspend fun login(account: String, password: String): UserSession
    suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson>
}
