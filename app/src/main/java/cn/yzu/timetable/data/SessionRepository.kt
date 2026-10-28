package cn.yzu.timetable.data

import cn.yzu.timetable.data.model.UserSession

class SessionRepository {
    private var session: UserSession? = null
    fun current(): UserSession? = session
    fun save(value: UserSession) { session = value }
    fun clear() { session = null }
}
