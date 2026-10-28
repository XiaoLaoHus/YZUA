package cn.yzu.timetable.data.urp

/** 2026-10-28 根据扬大网页核对的入口；成绩接口从页面逐次发现。 */
data class UrpConfig(
    val baseUrl: String = "http://ydjwxs.yzu.edu.cn",
    val loginPath: String = "/sigin",
    val schedulePath: String = "/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback",
    val homePath: String = "/index",
    val scorePagePath: String = "/student/integratedQuery/scoreQuery/thisTermScores/index",
    val allScorePagePath: String = "/student/integratedQuery/scoreQuery/allTermScores/index",
    val examPagePath: String = "/student/examinationManagement/examPlan/index",
    val examPath: String = "/student/examinationManagement/examPlan/detail",
)
