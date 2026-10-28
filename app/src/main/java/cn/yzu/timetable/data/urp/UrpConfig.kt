package cn.yzu.timetable.data.urp

/** Real URP values are intentionally supplied at build/deployment time. */
data class UrpConfig(
    val baseUrl: String = "http://ydjwxs.yzu.edu.cn",
    val loginPath: String = "/mobile/index",
    val schedulePath: String = "/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback",
    val accountField: String = "username",
    val passwordField: String = "password",
    val termField: String = "planCode",
)
