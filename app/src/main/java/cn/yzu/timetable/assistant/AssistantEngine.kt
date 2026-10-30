package cn.yzu.timetable.assistant

import cn.yzu.timetable.classroom.UrpClassroomDataSource
import cn.yzu.timetable.data.AcademicCalendar
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.components.ScheduleSeason
import cn.yzu.timetable.ui.components.YzuTimetable
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

/** 助手可用的本地能力：只读取当前界面状态与教务只读接口。 */
class AssistantContext(
    val state: AppUiState,
    val classroom: UrpClassroomDataSource?,
    val season: ScheduleSeason,
)

object AssistantEngine {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    const val MAX_STEPS = 4

    private fun systemPrompt(ctx: AssistantContext): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE"))
        val week = ctx.state.semester?.weekOn(ctx.state.today)?.toString() ?: "未知（学期未校准）"
        return buildString {
            append("你是 YZUA（Yangzhou University Agent）的智能助手，服务扬州大学学生。当前时间：").append(now)
            append("；当前教学周：第 ").append(week).append(" 周；作息季节：").append(ctx.season.label).append("。\n")
            append("你可以调用以下工具获取真实数据，禁止编造课程、成绩、考试或教室信息：\n")
            append("- today_classes：查询某天课程。输入：{} 或 {\"weekday\":3}（1-7）\n")
            append("- week_timetable：查询某一周课表。输入：{\"week\":9}\n")
            append("- next_class：下一节课的时间与地点。输入：{}\n")
            append("- my_scores：查询成绩。输入：{\"scope\":\"term\"} 或 {\"scope\":\"all\"}\n")
            append("- my_exams：查询考试安排。输入：{}\n")
            append("- semester_info：学期首周、结束日与周次。输入：{}\n")
            append("- free_classroom：查询空闲教室。输入：{\"campus\":\"扬子津东校区\",\"building\":\"文津楼\",\"day\":0,\"start\":1,\"end\":2}，day 0=今天 1=明天 2=后天\n")
            append("需要工具时，单独两行输出：\nAction: 工具名\nAction Input: 一行 JSON\n")
            append("收到 Observation 后继续推理；信息足够时输出：\nFinal Answer: 给用户的中文回答\n")
            append("语气简洁友好，像同学间的交流。")
        }
    }

    private suspend fun runTool(name: String, input: JsonObject, ctx: AssistantContext): String {
      return try {
        when (name) {
            "today_classes" -> withContext(Dispatchers.IO) {
                val day = input.get("weekday")?.asInt ?: ctx.state.today.dayOfWeek.value
                val date = ctx.state.today.with(ChronoField.DAY_OF_WEEK, day.coerceIn(1, 7).toLong())
                val lessons = AcademicCalendar.todayLessons(date, ctx.state.semester, ctx.state.lessons)
                if (lessons.isEmpty()) "当天没有课程" else lessons.joinToString("\n") { l ->
                    val range = YzuTimetable.lessonRange(l, ctx.season, ctx.state.periods)
                    "第${l.startPeriod}-${l.endPeriod}节 ${range?.first?.startText.orEmpty()}-${range?.second?.endText.orEmpty()} ${l.courseName} @${l.classroom} ${l.teacher}"
                }
            }
            "week_timetable" -> {
                val week = input.get("week")?.asInt ?: ctx.state.semester?.weekOn(ctx.state.today) ?: ctx.state.week
                val ls = ctx.state.lessons.filter { it.occursInWeek(week) }
                if (ls.isEmpty()) "第 $week 周没有课程" else ls.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
                    .joinToString("\n") { "周${it.dayOfWeek} 第${it.startPeriod}-${it.endPeriod}节 ${it.courseName} @${it.classroom}" }
            }
            "next_class" -> {
                val now = LocalTime.now()
                val today = AcademicCalendar.todayLessons(ctx.state.today, ctx.state.semester, ctx.state.lessons)
                today.firstOrNull { l -> YzuTimetable.lessonRange(l, ctx.season, ctx.state.periods)?.first?.start?.isAfter(now) == true }
                    ?.let { l -> val r = YzuTimetable.lessonRange(l, ctx.season, ctx.state.periods); "下一节：${l.courseName} ${r?.first?.startText.orEmpty()} 开始 @${l.classroom}" }
                    ?: "今天后面没有课了"
            }
            "my_scores" -> {
                val list = ctx.state.scores
                if (list.isEmpty()) "没有可显示的成绩记录" else list.take(15).joinToString("\n") { "${it.courseName}：${it.score.ifBlank { "未公布" }}（学分 ${it.credit}）" }
            }
            "my_exams" -> if (ctx.state.exams.isEmpty()) "近期没有考试安排" else ctx.state.exams.joinToString("\n") { "${it.courseName} ${it.startTime.replace('T', ' ')} @${it.location}" }
            "semester_info" -> ctx.state.semester?.let { "首周周一 ${it.firstMonday}，结束 ${it.lastDay}，共 ${it.totalWeeks()} 周" } ?: "学期日期未校准"
            "free_classroom" -> {
                val src = ctx.classroom ?: return "当前没有教务会话，无法查询空教室"
                val campuses = src.campuses()
                val campus = campuses.firstOrNull { it.name.contains(input.get("campus")?.asString.orEmpty()) } ?: campuses.firstOrNull()
                    ?: return "未获取到校区列表"
                val buildings = src.buildings(campus.number)
                val building = buildings.firstOrNull { it.name.contains(input.get("building")?.asString.orEmpty()) } ?: buildings.firstOrNull()
                    ?: return "该校区未获取到教学楼"
                src.selectBuilding(campus, building)
                val start = input.get("start")?.asInt ?: 1
                val end = input.get("end")?.asInt ?: 2
                val day = input.get("day")?.asInt ?: 0
                val r = src.freeRooms((start.coerceAtMost(end)..start.coerceAtLeast(end)).toList(), day.coerceIn(0, 2))
                if (r.buildings.isEmpty()) "${building.name} 在该时段没有空闲教室" else r.buildings.joinToString("\n") { b -> b.name + "：" + b.rooms.joinToString("、") { it.name } }
            }
            else -> "未知工具：$name"
        }
    } catch (e: Exception) { "工具执行失败：${e.message}" }
    }

    suspend fun chat(settings: AssistantSettings, history: List<ChatMessage>, user: String, ctx: AssistantContext, onStep: (String) -> Unit): String {
        if (!settings.configured) return "尚未配置模型接口。请在助手页右上角设置中填写 OpenAI 兼容接口地址、API Key 与模型名。"
        val messages = mutableListOf(ChatMessage("system", systemPrompt(ctx)))
        messages += history.takeLast(8)
        messages += ChatMessage("user", user)
        repeat(MAX_STEPS) {
            val reply = callLlm(settings, messages) ?: return "模型请求失败：请检查接口地址、API Key 与网络（模型服务需在本机可访问）。"
            val final = Regex("Final Answer:\\s*([\\s\\S]+)").find(reply)?.groupValues?.get(1)?.trim()
            if (final != null) return final
            val action = Regex("Action:\\s*([\\w-]+)").find(reply)?.groupValues?.get(1)?.trim() ?: return reply.trim()
            val inputRaw = Regex("Action Input:\\s*(\\{[\\s\\S]*?\\}|[^\\n]+)").find(reply)?.groupValues?.get(1)?.trim() ?: "{}"
            val input = runCatching { JsonParser.parseString(inputRaw).asJsonObject }.getOrDefault(JsonObject())
            onStep("调用工具 $action …")
            val observation = runTool(action, input, ctx)
            messages += ChatMessage("assistant", reply.trim())
            messages += ChatMessage("user", "Observation: $observation")
        }
        return "我尝试了多次仍没有拿到足够信息，换个问法试试？"
    }

    private suspend fun callLlm(settings: AssistantSettings, messages: List<ChatMessage>): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                addProperty("model", settings.model)
                addProperty("temperature", 0.3)
                add("messages", Gson().toJsonTree(messages.map { mapOf("role" to it.role, "content" to it.content) }))
            }
            val base = settings.baseUrl.trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer " + settings.apiKey)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body?.string() ?: return@withContext null
                JsonParser.parseString(body).asJsonObject
                    .getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString
            }
        } catch (_: Exception) { null }
    }
}
