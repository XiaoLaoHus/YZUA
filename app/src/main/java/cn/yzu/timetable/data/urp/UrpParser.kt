package cn.yzu.timetable.data.urp

import cn.yzu.timetable.data.model.ExamSchedule
import cn.yzu.timetable.data.model.PeriodTime
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.data.model.ScoreRecord
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.util.Locale

class UrpParseException(message: String) : IllegalArgumentException(message)
data class UrpPeriod(val number: Int, val startTime: String, val endTime: String) {
    fun toModel() = PeriodTime(number, startTime, endTime)
}
data class ParsedSchedule(
    val lessons: List<ScheduleLesson>,
    val periods: List<UrpPeriod> = emptyList(),
    val academicYear: String? = null,
    val termName: String? = null,
    val currentWeek: Int? = null,
)
data class ScorePage(val records: List<ScoreRecord>, val totalCount: Int)

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
    fun parseAllTermScores(payload: String): ScorePage {
        val root = try { JsonParser.parseString(payload).asJsonObject } catch (_: Exception) { throw UrpParseException("历年成绩格式错误") }
        val list = root.get("list")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw UrpParseException("历年成绩缺少分页信息")
        val context = list.get("pageContext")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val count = text(context, "totalCount")?.toIntOrNull()?.takeIf { it >= 0 } ?: throw UrpParseException("历年成绩总数无效")
        val records = list.get("records")
        if (records == null || records.isJsonNull) {
            if (count == 0) return ScorePage(emptyList(), 0)
            throw UrpParseException("历年成绩缺少记录")
        }
        if (!records.isJsonArray) throw UrpParseException("历年成绩记录格式错误")
        val mapped = records.asJsonArray.map { row ->
            val o = row.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw UrpParseException("历年成绩记录格式错误")
            val normalized = JsonObject()
            mapOf("KCH" to "courseNumber", "KXH" to "coureSequenceNumber", "KCM" to "courseName",
                "YWKCM" to "englishCourseName", "XF" to "credit", "KCSXMC" to "coursePropertyName",
                "KCCJ" to "courseScore", "LRZTDM" to "inputStatusCode", "CJLRFSDM" to "inputMethodCode",
                "DJM" to "levelName", "WTGYYSM" to "unpassedReasonExplain").forEach { (from, to) -> o.get(from)?.let { normalized.add(to, it) } }
            parseScores("[$normalized]").single().copy(termLabel = text(o, "ZXJXJHM").orEmpty())
        }
        return ScorePage(mapped, count)
    }
    private val fallbackPeriods = listOf(UrpPeriod(1, "08:00", "08:45"), UrpPeriod(2, "08:55", "09:40"), UrpPeriod(3, "09:55", "10:40"), UrpPeriod(4, "10:50", "11:35"), UrpPeriod(5, "11:45", "12:30"), UrpPeriod(6, "14:30", "15:15"), UrpPeriod(7, "15:25", "16:10"), UrpPeriod(8, "16:20", "17:05"), UrpPeriod(9, "17:15", "18:00"), UrpPeriod(10, "18:10", "18:55"), UrpPeriod(11, "19:30", "20:15"), UrpPeriod(12, "20:25", "21:10"))
    fun parse(payload: String): ParsedSchedule = when {
        payload.trim().startsWith("{") -> parseJson(payload)
        payload.trim().startsWith("<") -> parseHtml(payload)
        else -> throw UrpParseException("教务系统返回了无法识别的内容")
    }

    fun parseScores(payload: String): List<ScoreRecord> {
        val root = try { JsonParser.parseString(payload) } catch (_: Exception) { throw UrpParseException("成绩 JSON 格式错误") }
        val envelope = root.asJsonArrayOrNull()?.firstOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        if (text(envelope, "state") == "0") throw UrpParseException("该学期成绩查询已关闭，暂时不能查看成绩")
        val list = when {
            envelope?.get("list")?.isJsonArray == true -> envelope.getAsJsonArray("list")
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.get("list")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("list")
            else -> throw UrpParseException("成绩响应缺少列表")
        }
        return list.map { item ->
            val o = item.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw UrpParseException("成绩记录格式错误")
            val id = o.get("id")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            val published = text(o, "inputStatusCode")?.let { it == "05" } ?: true
            val rawScore = text(o, "courseScore", "score").orEmpty()
            val displayScore = when {
                !published -> "未公布"
                rawScore == "-999.999" -> "未评估"
                text(o, "inputMethodCode") == "002" -> text(o, "levelName") ?: rawScore
                else -> rawScore.ifBlank { text(o, "levelName").orEmpty() }
            }
            ScoreRecord(
                courseCode = text(id, "courseNumber") ?: text(o, "courseNumber", "courseCode").orEmpty(),
                courseSequence = text(o, "coureSequenceNumber", "courseSequenceNumber").orEmpty().takeUnless { it == "NONE" }.orEmpty(),
                courseName = text(o, "courseName") ?: throw UrpParseException("成绩缺少课程名"),
                credit = text(o, "credit", "credits").orEmpty(),
                property = text(o, "coursePropertyName", "courseProperty").orEmpty(),
                maxScore = if (published) text(o, "maxcj", "maxScore").orEmpty() else "",
                minScore = if (published) text(o, "mincj", "minScore").orEmpty() else "",
                averageScore = if (published) text(o, "avgcj", "averageScore").orEmpty() else "",
                score = displayScore,
                unpassedReason = text(o, "unpassedReasonExplain", "unpassedReason").orEmpty(),
                englishCourseName = text(o, "englishCourseName", "englishName").orEmpty(),
            )
        }
    }

    fun parseExams(payload: String): List<ExamSchedule> {
        val root = try { JsonParser.parseString(payload) } catch (_: Exception) { throw UrpParseException("考试安排 JSON 格式错误") }
        val list = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.get("list")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("list")
            root.isJsonObject && root.asJsonObject.get("events")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("events")
            else -> throw UrpParseException("考试响应缺少事件列表")
        }
        return list.map { item ->
            val o = item.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw UrpParseException("考试事件格式错误")
            ExamSchedule(
                courseName = text(o, "courseName", "title", "name")?.let { Jsoup.parse(it).text() } ?: throw UrpParseException("考试事件缺少名称"),
                startTime = text(o, "start", "startTime", "examTime") ?: throw UrpParseException("考试事件缺少时间"),
                endTime = text(o, "end", "endTime").orEmpty(),
                location = text(o, "location", "place", "classroom", "examPlace").orEmpty(),
                note = text(o, "description", "note", "remark").orEmpty(),
            )
        }
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
                val place = placeOf(s)
                lessons += ScheduleLesson("$code-$day-$start-$index", code, name, teacher, place, day, start, start + count - 1, weeks.minOrNull()!!..weeks.maxOrNull()!!, lessons.size % 10, weeks)
            }
        } }
        if (lessons.isEmpty()) throw UrpParseException("没有解析到有效课程")
        val periods = root.getAsJsonArray("jcsjbs")?.mapNotNull { item -> val o = item.asJsonObject; val n = text(o, "jc", "section", "number")?.toIntOrNull() ?: return@mapNotNull null; val st = clock(text(o, "kssj", "startTime", "start")) ?: return@mapNotNull null; val et = clock(text(o, "jssj", "endTime", "end")) ?: return@mapNotNull null; UrpPeriod(n, st, et) }.orEmpty()
        val orderedPeriods = periods.sortedBy(UrpPeriod::number).ifEmpty { fallbackPeriods }
        val term = text(root, "xnxq", "xnxqmc", "term", "termName", "semester")
        return ParsedSchedule(lessons, orderedPeriods, academicYear(term), termName(term), currentWeek(root))
    }
    /** 校区 + 教学楼 + 教室拼接；不同教务部署把楼名放在不同字段，这里按候选键补全，避免丢楼名。 */
    private fun placeOf(s: com.google.gson.JsonObject): String {
        val campus = text(s, "campusName", "campus", "xqmc").orEmpty().trim()
        val room = text(s, "classroomName", "classroom", "jsmc", "jxcdmc", "classroomFullName").orEmpty().trim()
        val building = text(s, "teachingBuildingName", "teachBuildingName", "buildingName", "jxlmc", "jxlName", "buildName", "louming").orEmpty().trim()
        val parts = listOf(campus, building.takeIf { b -> b.isNotEmpty() && !room.contains(b) && !campus.contains(b) }.orEmpty(), room)
        return parts.filter(String::isNotEmpty).joinToString(" ").ifBlank { "待定" }
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
    private fun academicYear(value: String?): String? = value?.let { Regex("(\\d{4})\\s*[-~至]\\s*(\\d{4})").find(it)?.let { match -> "${match.groupValues[1]}-${match.groupValues[2]}" } }
    private fun termName(value: String?): String? = value?.let {
        when {
            it.contains("秋") || it.contains("秋季") -> "秋"
            it.contains("春") || it.contains("春季") -> "春"
            it.contains("夏") || it.contains("夏季") -> "夏"
            it.contains("冬") || it.contains("冬季") -> "冬"
            else -> null
        }
    }
    private fun currentWeek(root: JsonObject): Int? = listOf("currentWeek", "week", "jxz", "教学周", "当前周次")
        .asSequence().mapNotNull { key -> root.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString }
        .mapNotNull { Regex("第?\\s*(\\d{1,2})\\s*(?:教学)?周?").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: it.toIntOrNull() }
        .firstOrNull { it in 1..30 }
    private fun text(obj: JsonObject?, vararg names: String): String? = obj?.let { names.firstNotNullOfOrNull { name -> it.get(name)?.takeIf(JsonElement::isJsonPrimitive)?.asString?.takeIf(String::isNotBlank) } }
    private fun JsonElement.asJsonArrayOrNull() = if (isJsonArray) asJsonArray else null
}
