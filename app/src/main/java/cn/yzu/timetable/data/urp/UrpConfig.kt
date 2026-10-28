package cn.yzu.timetable.data.urp

/** Real URP values are intentionally supplied at build/deployment time. */
data class UrpConfig(
    val baseUrl: String = "",
    val loginPath: String = "/api/login",
    val schedulePath: String = "/api/student/schedule",
)
