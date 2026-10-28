package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.ScheduleLesson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.util.Locale

class UrpParseException(message: String) : IllegalArgumentException(message)
data class UrpPeriod(val number: Int, val startTime: String, val endTime: String)
data class ParsedSchedule(val lessons: List<ScheduleLesson>, val periods: List<UrpPeriod> = emptyList())

object WeekPattern {
    fun parse(value: String): Set<Int> {
        val raw = value.trim()
        if (raw.matches(Regex("[01]{4,}"))) return raw.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()
        val result = linkedSetOf<Int>()
        val normalized = raw.replace('，', ',').replace('、', ',').replace("周", "")
        normalized.split(',', ';', '；').map(String::trim).filter(String::isNotEmpty).forEach { token ->
            val odd = token.contains("单"); val even = token.contains("双")
            val range = Regex("(\\d+)\\s*[-~至](?:\\s*)(\\d+)").find(token)
            if (range != null) {
                val first = range.groupValues[1].toInt(); val last = range.groupValues[2].toInt()
                if (first <= 0 || last < first) throw UrpParseException("周次范围无效：$value")
                (first..last).filter { (!odd && !even) || (odd && it % 2 == 1) || (even && it % 2 == 0) }.forEach(result::add)
            } else result += Regex("\\d+").find(token)?.value?.toIntOrNull() ?: throw UrpParseException("无法识别周次：$value")
        }
        if (result.isEmpty()) throw UrpParseException("课表周次为空")
        return result
    }
}

object UrpParser {
    private val fallbackPeriods = listOf(UrpPeriod(1, "08:00", "08:45"), UrpPeriod(2, "08:55", "09:40"), UrpPeriod(3, "09:55", "10:40"), UrpPeriod(4, "10:50", "11:35"), UrpPeriod(5, "11:45", "12:30"), UrpPeriod(6, "14:30", "15:15"), UrpPeriod(7, "15:25", "16:10"), UrpPeriod(8, "16:20", "17:05"), UrpPeriod(9, "17:15", "18:00"), UrpPeriod(10, "18:10", "18:55"), UrpPeriod(11, "19:30", "20:15"), UrpPeriod(12, "20:25", "21:10"))
    fun parse(payload: String): ParsedSchedule = when {
        payload.trim().startsWith("{") -> parseJson(payload)
        payload.trim().startsWith("<") -> parseHtml(payload)
        else -> throw UrpParseException("教务系统返回了无法识别的内容")
    }
    private fun parseJson(payload: String): ParsedSchedule {
        val root = try { JsonParser.parseString(payload).asJsonObject } catch (_: Exception) { throw UrpParseException("课表 JSON 格式错误") }
        if (root.has("error") || root.get("login")?.asBoolean == true) throw UrpParseException("教务会话已失效，请重新登录")
        val lessons = mutableListOf<ScheduleLesson>()
        root.getAsJsonArray("xkxx")?.forEach { group -> group.asJsonObject.entrySet().forEach { (_, item) ->
            val course = item.asJsonObject; val name = text(course, "courseName") ?: return@forEach
            val code = text(course, "courseNumber", "courseCode").orEmpty(); val teacher = text(course, "attendClassTeacher", "teacher").orEmpty().replace("*", "").trim()
            course.getAsJsonArray("timeAndPlaceList")?.forEachIndexed { index, slot ->
                val s = slot.asJsonObject; val day = text(s, "classDay")?.toIntOrNull() ?: throw UrpParseException("课程星期无效：$name"); val start = text(s, "classSessions")?.toIntOrNull() ?: throw UrpParseException("课程节次无效：$name"); val count = text(s, "continuingSession")?.toIntOrNull() ?: 1
                val weeks = WeekPattern.parse(text(s, "classWeek") ?: throw UrpParseException("课程周次缺失：$name"))
                if (day !in 1..7 || start !in 1..12 || count !in 1..12 || start + count - 1 > 12) throw UrpParseException("课程时间超出范围：$name")
                val place = listOf(text(s, "campusName").orEmpty().trim(), text(s, "classroomName", "classroom").orEmpty().trim()).filter(String::isNotEmpty).joinToString(" ").ifBlank { "待定" }
                lessons += ScheduleLesson("$code-$day-$start-$index", code, name, teacher, place, day, start, start + count - 1, weeks.minOrNull()!!..weeks.maxOrNull()!!, lessons.size % 10, weeks)
            }
        } }
        if (lessons.isEmpty()) throw UrpParseException("没有解析到有效课程")
        val periods = root.getAsJsonArray("jcsjbs")?.mapNotNull { item -> val o = item.asJsonObject; val n = text(o, "jc")?.toIntOrNull() ?: return@mapNotNull null; val st = clock(text(o, "kssj")) ?: return@mapNotNull null; val et = clock(text(o, "jssj")) ?: return@mapNotNull null; UrpPeriod(n, st, et) }.orEmpty()
        return ParsedSchedule(lessons, periods.sortedBy(UrpPeriod::number).ifEmpty { fallbackPeriods })
    }
    private fun parseHtml(payload: String): ParsedSchedule {
        val rows = Jsoup.parse(payload).select("table tr"); if (rows.size < 2) throw UrpParseException("课表页面缺少课程表格")
        val lessons = mutableListOf<ScheduleLesson>(); var code = ""; var name = ""; var teacher = ""
        rows.drop(1).forEachIndexed { index, row ->
            val cells = row.select("th,td").map { it.text().trim() }.filter(String::isNotEmpty); if (cells.size >= 7) { code = cells[0]; name = cells[1]; teacher = cells[2] }; if (cells.size < 4 || name.isBlank()) return@forEachIndexed
            val offset = if (cells.size >= 7) 3 else 0; val weeks = WeekPattern.parse(cells[offset]); val day = parseDay(cells[offset + 1]); val range = Regex("(\\d+)\\s*[-~至](\\d+)").find(cells[offset + 2]) ?: throw UrpParseException("节次无效")
            val start = range.groupValues[1].toInt(); val end = range.groupValues[2].toInt(); if (day !in 1..7 || start !in 1..12 || end !in start..12) throw UrpParseException("课程时间超出范围")
            lessons += ScheduleLesson("$code-$index", code, name, teacher, cells[offset + 3], day, start, end, weeks.minOrNull()!!..weeks.maxOrNull()!!, lessons.size % 10, weeks)
        }
        if (lessons.isEmpty()) throw UrpParseException("没有解析到有效课程"); return ParsedSchedule(lessons, fallbackPeriods)
    }
    private fun parseDay(value: String): Int = when (value.trim().lowercase(Locale.ROOT)) { "一", "周一", "星期一", "1" -> 1; "二", "周二", "星期二", "2" -> 2; "三", "周三", "星期三", "3" -> 3; "四", "周四", "星期四", "4" -> 4; "五", "周五", "星期五", "5" -> 5; "六", "周六", "星期六", "6" -> 6; "日", "周日", "星期日", "7" -> 7; else -> 0 }
    private fun clock(value: String?): String? = value?.takeIf { it.matches(Regex("\\d{4}")) }?.let { "${it.substring(0, 2)}:${it.substring(2)}" }
    private fun text(obj: JsonObject, vararg names: String): String? = names.firstNotNullOfOrNull { name -> obj.get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.takeIf(String::isNotBlank) }
}
