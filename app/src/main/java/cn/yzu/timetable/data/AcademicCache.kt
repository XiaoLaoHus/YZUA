package cn.yzu.timetable.data

import cn.yzu.timetable.data.model.*
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.LocalDate

/** 仅保存查询结果和设置，不包含 UserSession、密码、Cookie 或 token。 */
data class AcademicSnapshot(
    val owner: String = "",
    val semester: SemesterDates? = null,
    val lessons: List<ScheduleLesson> = emptyList(),
    val scheduleUpdated: String? = null,
    val termScores: List<ScoreRecord> = emptyList(),
    val termScoresUpdated: String? = null,
    val allScores: List<ScoreRecord> = emptyList(),
    val allScoresUpdated: String? = null,
    val exams: List<ExamSchedule> = emptyList(),
    val examsUpdated: String? = null,
    val onboarded: Boolean = false,
)
data class CacheRead(val snapshot: AcademicSnapshot, val error: String? = null)

class AcademicCache(private val file: File? = null) {
    private var memory = AcademicSnapshot()
    private val gson = Gson()
    private data class LessonDto(
        val id: String, val code: String, val name: String, val teacher: String, val room: String,
        val day: Int, val start: Int, val end: Int, val weeks: List<Int>, val color: Int,
    )
    private data class Document(
        val version: Int = 1, val owner: String = "", val semester: SemesterDates? = null,
        val lessons: List<LessonDto>? = emptyList(), val scheduleUpdated: String? = null,
        val termScores: List<ScoreRecord>? = emptyList(), val termScoresUpdated: String? = null,
        val allScores: List<ScoreRecord>? = emptyList(), val allScoresUpdated: String? = null,
        val exams: List<ExamSchedule>? = emptyList(), val examsUpdated: String? = null,
        val onboarded: Boolean = false,
    )

    @Synchronized fun read(): CacheRead {
        if (file == null) return CacheRead(memory)
        if (!file.exists()) return CacheRead(AcademicSnapshot())
        return try {
            require(file.length() <= 10_000_000)
            val d = gson.fromJson(file.readText(Charsets.UTF_8), Document::class.java) ?: error("empty")
            require(d.version == 1)
            require(d.lessons != null)
            d.semester?.validate()
            listOfNotNull(d.scheduleUpdated, d.termScoresUpdated, d.allScoresUpdated, d.examsUpdated).forEach { LocalDate.parse(it) }
            val lessons = d.lessons.orEmpty().map { l ->
                l.id.length; l.code.length; l.name.length; l.teacher.length; l.room.length
                require(l.day in 1..7 && l.start in 1..12 && l.end in l.start..12 && l.weeks.isNotEmpty() && l.weeks.all { it in 1..30 })
                ScheduleLesson(l.id, l.code, l.name, l.teacher, l.room, l.day, l.start, l.end, l.weeks.min()..l.weeks.max(), l.color.coerceAtLeast(0), l.weeks.toSet())
            }
            d.owner.length
            val termScores = d.termScores.orEmpty()
            val allScores = d.allScores.orEmpty()
            val exams = d.exams.orEmpty()
            (termScores + allScores).forEach { score ->
                score.courseCode.length; score.courseSequence.length; score.courseName.length; score.credit.length
                score.property.length; score.maxScore.length; score.minScore.length; score.averageScore.length
                score.score.length; score.unpassedReason.length; score.englishCourseName.length; score.termLabel.length
            }
            exams.forEach { exam -> exam.courseName.length; exam.startTime.length; exam.endTime.length; exam.location.length; exam.note.length }
            CacheRead(AcademicSnapshot(d.owner, d.semester, lessons, d.scheduleUpdated, termScores, d.termScoresUpdated, allScores, d.allScoresUpdated, exams, d.examsUpdated, d.onboarded))
        } catch (_: Exception) { CacheRead(AcademicSnapshot(), "本地数据损坏或格式不兼容，请在“我的”中登录重新导入。") }
    }

    @Synchronized fun write(s: AcademicSnapshot) {
        if (file == null) { memory = s; return }
        val d = Document(owner = s.owner, semester = s.semester,
            lessons = s.lessons.map { LessonDto(it.id, it.courseCode, it.courseName, it.teacher, it.classroom, it.dayOfWeek, it.startPeriod, it.endPeriod, it.weekNumbers.sorted(), it.colorIndex) },
            scheduleUpdated = s.scheduleUpdated, termScores = s.termScores, termScoresUpdated = s.termScoresUpdated,
            allScores = s.allScores, allScoresUpdated = s.allScoresUpdated, exams = s.exams, examsUpdated = s.examsUpdated, onboarded = s.onboarded)
        file.parentFile?.mkdirs()
        val temp = File(file.absolutePath + ".tmp")
        try {
            temp.outputStream().use { out -> out.write(gson.toJson(d).toByteArray(Charsets.UTF_8)); out.fd.sync() }
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }
}
