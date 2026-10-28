package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.*

class DemoUrpDataSource : UrpScheduleDataSource {
    override suspend fun login(account: String, password: String): UserSession {
        require(account.isNotBlank()) { "请输入学号" }
        require(password.isNotBlank()) { "请输入密码" }
        return UserSession(account.trim(), "扬大学生", "demo-token", ScheduleSource.DEMO)
    }

    override suspend fun loginWithSsoCookie(account: String, cookie: String): UserSession {
        require(cookie.isNotBlank()) { "统一认证会话为空" }
        return UserSession(account.trim().ifBlank { "sso" }, "扬大学生", "demo-sso", ScheduleSource.DEMO)
    }

    override suspend fun getSchedule(session: UserSession, term: Term): List<ScheduleLesson> = listOf(
        ScheduleLesson("math", "MATH101", "高等数学（一）", "王老师", "荷花池校区 1-201", 1, 1, 2, 1..16, 0),
        ScheduleLesson("english", "ENG201", "大学英语", "李老师", "扬子津校区 A-302", 2, 3, 4, 1..16, 1),
        ScheduleLesson("programming", "CS105", "程序设计基础", "陈老师", "扬子津校区 B-108", 3, 5, 6, 2..18, 2),
        ScheduleLesson("pe", "PE110", "体育（篮球）", "赵老师", "文汇路体育馆", 4, 7, 8, 1..12, 3),
        ScheduleLesson("marx", "MARX001", "思想道德与法治", "周老师", "荷花池校区 2-101", 5, 9, 10, 1..16, 4),
        ScheduleLesson("lab", "PHY108", "大学物理实验", "孙老师", "扬子津校区 实验楼", 6, 3, 4, 4..12, 5),
        ScheduleLesson("design", "ART220", "设计思维", "顾老师", "瘦西湖校区 创意工坊", 3, 9, 10, 8..16, 6),
    )

    override suspend fun getScores(session: UserSession): List<ScoreRecord> = listOf(
        ScoreRecord("MATH101", "01", "高等数学（一）", "5", "必修", "96", "60", "82.5", "88", "", "Advanced Mathematics I")
    )

    override suspend fun getExams(session: UserSession): List<ExamSchedule> = emptyList()
}
