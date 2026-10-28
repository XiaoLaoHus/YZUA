package cn.yzu.timetable.data

import cn.yzu.timetable.data.model.ScheduleLesson
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import org.jsoup.Jsoup

data class SemesterDates(
    val firstMonday: String,
    val lastDay: String,
    val label: String = "当前学期",
    val confirmed: Boolean = false,
) {
    fun start(): LocalDate = LocalDate.parse(firstMonday)
    fun end(): LocalDate = LocalDate.parse(lastDay)
    fun validate(): SemesterDates {
        require(start().dayOfWeek == DayOfWeek.MONDAY) { "首周开始日期必须是周一" }
        require(!end().isBefore(start()) && ChronoUnit.DAYS.between(start(), end()) < 210) { "学期结束日期需在首周开始后的 30 周内" }
        return this
    }
    fun weekOn(date: LocalDate): Int? =
        if (date.isBefore(start()) || date.isAfter(end())) null else (ChronoUnit.DAYS.between(start(), date) / 7 + 1).toInt()
    fun totalWeeks(): Int = (ChronoUnit.DAYS.between(start(), end()) / 7 + 1).toInt()
}

object AcademicCalendar {
    fun todayLessons(date: LocalDate, semester: SemesterDates?, lessons: List<ScheduleLesson>): List<ScheduleLesson> {
        val week = semester?.weekOn(date) ?: return emptyList()
        return lessons.filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(week) }
            .sortedWith(compareBy<ScheduleLesson> { it.startPeriod }.thenBy { it.endPeriod }.thenBy { it.courseName })
    }

    fun reminder(date: LocalDate, semester: SemesterDates?, importedOn: String?): String? {
        if (importedOn == null) return null
        if (semester == null) return "请在“我的”中设置本学期首周和结束日期，才能准确显示今日课程与到期提醒。"
        if (date.isAfter(semester.end())) return "已超过保存的学期结束日期，课表已过期。请在新学期登录刷新，并校准新学期日期。"
        if (date.isEqual(semester.end())) return "今天是保存的学期结束日，课表即将过期。请在新学期登录刷新。"
        if (!date.isBefore(semester.start()) && LocalDate.parse(importedOn).isBefore(semester.start()))
            return "新学期已开始，请登录刷新本学期课表。"
        if (!semester.confirmed) return "首周日期由学校当前周次推算，结束日期暂按 20 周建议，请在“我的”中核对校准。"
        return null
    }

    /** 只使用首页实际返回的教学周次；没有学校信息时不猜首周。 */
    fun fromSchoolHome(html: String, date: LocalDate): SemesterDates? {
        val text = Jsoup.parse(html).text()
        val full = Regex("""(\d{4}-\d{4})\s*(?:学年)?\s*([春秋])(?:季|学期)?\s*第\s*(\d{1,2})\s*(?:教学)?周""").find(text)
        val short = Regex("""第\s*(\d{1,2})\s*(?:教学)?周""").find(text)
        val week = (full?.groupValues?.getOrNull(3) ?: short?.groupValues?.getOrNull(1))?.toIntOrNull()?.takeIf { it in 1..30 } ?: return null
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((week - 1).toLong())
        val label = full?.let { "${it.groupValues[1]} ${it.groupValues[2]}学期" } ?: "当前学期"
        return SemesterDates(start.toString(), start.plusWeeks(20).minusDays(1).toString(), label)
    }
}
