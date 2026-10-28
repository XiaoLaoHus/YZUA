package cn.yzu.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yzu.timetable.data.*
import cn.yzu.timetable.data.model.*
import cn.yzu.timetable.data.urp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

sealed interface AppScreen { data object Login : AppScreen; data object Home : AppScreen }
data class AppUiState(
    val screen: AppScreen = AppScreen.Home, val account: String = "", val password: String = "",
    val session: UserSession? = null, val term: Term = Term("current", "本地课表"), val week: Int = 1,
    val today: LocalDate = LocalDate.now(), val semester: SemesterDates? = null,
    val periods: List<PeriodTime> = emptyList(), val academicYear: String? = null, val currentWeek: Int? = null,
    val lessons: List<ScheduleLesson> = emptyList(), val scores: List<ScoreRecord> = emptyList(),
    val exams: List<ExamSchedule> = emptyList(), val scoreError: String? = null, val examError: String? = null,
    val scoresLoaded: Boolean = false, val examsLoaded: Boolean = false, val allScoreTerms: Boolean = false,
    val selectedLesson: ScheduleLesson? = null, val loading: Boolean = false, val error: String? = null,
    val cacheError: String? = null, val needsLogin: Boolean = false, val onboarded: Boolean = false,
    val scheduleUpdated: String? = null, val scoresUpdated: String? = null, val examsUpdated: String? = null,
    val cachedOwner: String = "", val settingsError: String? = null,
) {
    val todayLessons get() = AcademicCalendar.todayLessons(today, semester, lessons)
    val semesterReminder get() = AcademicCalendar.reminder(today, semester, scheduleUpdated)
}

class AppViewModel(
    private val source: UrpScheduleDataSource = UrpHttpDataSource(),
    private val sessions: SessionRepository = SessionRepository(),
    private val cache: AcademicCache = AcademicCache(),
    private val dateProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val initial = cache.read()
    private var snapshot = initial.snapshot
    private val mutable = MutableStateFlow(restore(snapshot).copy(cacheError = initial.error))
    val state: StateFlow<AppUiState> = mutable.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    private fun restore(s: AcademicSnapshot): AppUiState {
        val today = dateProvider()
        return AppUiState(today = today, account = s.owner, cachedOwner = s.owner, semester = s.semester, periods = s.periods,
            academicYear = s.academicYear, currentWeek = s.currentWeek,
            term = Term("cached", s.semester?.label ?: "本地课表 · 请校准学期日期", s.semester?.totalWeeks() ?: 20),
            week = s.currentWeek ?: s.semester?.weekOn(today) ?: 1, lessons = s.lessons, scores = s.termScores, exams = s.exams,
            scoresLoaded = s.termScoresUpdated != null, examsLoaded = s.examsUpdated != null, onboarded = s.onboarded,
            scheduleUpdated = s.scheduleUpdated, scoresUpdated = s.termScoresUpdated, examsUpdated = s.examsUpdated)
    }
    fun setAccount(value: String) { mutable.value = mutable.value.copy(account = value, error = null) }
    fun setPassword(value: String) { mutable.value = mutable.value.copy(password = value, error = null) }
    fun openLogin() { mutable.value = mutable.value.copy(screen = AppScreen.Login, needsLogin = false, error = null) }
    fun closeLogin() { mutable.value = mutable.value.copy(screen = AppScreen.Home, error = null) }
    fun dismissOnboarding() {
        snapshot = snapshot.copy(onboarded = true); mutable.value = mutable.value.copy(onboarded = true); persist()
    }
    fun setAllScoreTerms(value: Boolean) {
        if (mutable.value.loading || mutable.value.allScoreTerms == value) return
        mutable.value = mutable.value.copy(allScoreTerms = value, scores = if (value) snapshot.allScores else snapshot.termScores,
            scoresUpdated = if (value) snapshot.allScoresUpdated else snapshot.termScoresUpdated,
            scoresLoaded = (if (value) snapshot.allScoresUpdated else snapshot.termScoresUpdated) != null, scoreError = null)
    }
    fun login() {
        val s = mutable.value
        if (s.account.isBlank() || s.password.isBlank()) { mutable.value = s.copy(error = "请输入学号和密码"); return }
        authenticate(replaceCache = false) { source.login(s.account, s.password) }
    }
    fun loginWithSsoCookie(cookie: String) {
        if (cookie.isBlank()) { mutable.value = mutable.value.copy(error = "统一认证未完成"); return }
        val account = mutable.value.account
        authenticate(replaceCache = true) { source.loginWithSsoCookie(account, cookie) }
    }
    private fun authenticate(replaceCache: Boolean, login: suspend () -> UserSession) {
        if (mutable.value.loading) return
        val version = ++generation
        mutable.value = mutable.value.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val session = login(); ensureActive()
                if (version != generation) return@launch
                if (replaceCache) {
                    snapshot = AcademicSnapshot(onboarded = true)
                    mutable.value = restore(snapshot).copy(loading = true)
                }
                sessions.save(session)
                snapshot = snapshot.copy(owner = session.studentId, onboarded = true)
                mutable.value = mutable.value.copy(screen = AppScreen.Home, session = session, password = "",
                    cachedOwner = session.studentId, onboarded = true, needsLogin = false)
                persist(); loadData(session, version)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == generation) mutable.value = mutable.value.copy(loading = false, error = error.message ?: "统一认证登录失败")
            }
        }
    }
    fun refresh() {
        if (mutable.value.loading) return
        val session = mutable.value.session
        if (session == null) { mutable.value = mutable.value.copy(needsLogin = true); return }
        val version = generation
        mutable.value = mutable.value.copy(loading = true, error = null, scoreError = null, examError = null, needsLogin = false)
        request = viewModelScope.launch { loadData(session, version) }
    }
    private suspend fun loadData(session: UserSession, version: Int) = coroutineScope {
        val term = mutable.value.term; val allTerms = mutable.value.allScoreTerms
        val schedule = async { query { (source as? UrpScheduleResultSource)?.getScheduleResult(session, term) ?: UrpScheduleResult(source.getSchedule(session, term)) } }
        val scores = async { query { if (allTerms) source.getAllScores(session) else source.getScores(session) } }
        val exams = async { query { source.getExams(session) } }
        val dates = async { query { source.getSemesterDates(session, dateProvider()) } }
        val a = schedule.await(); val b = scores.await(); val c = exams.await(); val d = dates.await()
        ensureActive(); if (version != generation) return@coroutineScope
        val today = dateProvider(); val previous = mutable.value
        var semester = snapshot.semester
        val suggestion = d.getOrNull()?.let { runCatching { it.validate() }.getOrNull() }
        if (a.isSuccess && suggestion != null && (semester == null || !semester.confirmed || today.isAfter(semester.end()))) semester = suggestion
        val expired = listOf(a.exceptionOrNull(), b.exceptionOrNull(), c.exceptionOrNull()).any { it is UrpLoginException }
        if (expired) {
            source.logout(); sessions.clear()
            mutable.value = previous.copy(today = today, session = null, needsLogin = true, loading = false,
                error = a.exceptionOrNull()?.message, scoreError = b.exceptionOrNull()?.message, examError = c.exceptionOrNull()?.message)
            persist()
            return@coroutineScope
        }
        snapshot = snapshot.copy(semester = semester,
            periods = a.getOrNull()?.periods?.takeIf { it.isNotEmpty() } ?: if (a.isSuccess) emptyList() else snapshot.periods,
            academicYear = a.getOrNull()?.academicYear ?: if (a.isSuccess) null else snapshot.academicYear,
            currentWeek = a.getOrNull()?.currentWeek ?: if (a.isSuccess) null else snapshot.currentWeek,
            lessons = a.getOrNull()?.lessons ?: snapshot.lessons, scheduleUpdated = if (a.isSuccess) today.toString() else snapshot.scheduleUpdated,
            termScores = if (!allTerms) b.getOrDefault(snapshot.termScores) else snapshot.termScores,
            termScoresUpdated = if (!allTerms && b.isSuccess) today.toString() else snapshot.termScoresUpdated,
            allScores = if (allTerms) b.getOrDefault(snapshot.allScores) else snapshot.allScores,
            allScoresUpdated = if (allTerms && b.isSuccess) today.toString() else snapshot.allScoresUpdated,
            exams = c.getOrDefault(snapshot.exams), examsUpdated = if (c.isSuccess) today.toString() else snapshot.examsUpdated)
        mutable.value = previous.copy(today = today, semester = semester, periods = snapshot.periods,
            academicYear = snapshot.academicYear, currentWeek = snapshot.currentWeek,
            term = Term("cached", semester?.label ?: previous.term.label, semester?.totalWeeks() ?: 20),
            week = snapshot.currentWeek ?: semester?.weekOn(today) ?: previous.week, lessons = snapshot.lessons,
            scores = if (allTerms) snapshot.allScores else snapshot.termScores, exams = snapshot.exams,
            scheduleUpdated = snapshot.scheduleUpdated, scoresUpdated = if (allTerms) snapshot.allScoresUpdated else snapshot.termScoresUpdated,
            examsUpdated = snapshot.examsUpdated, scoresLoaded = (if (allTerms) snapshot.allScoresUpdated else snapshot.termScoresUpdated) != null,
            examsLoaded = snapshot.examsUpdated != null, error = a.exceptionOrNull()?.message, scoreError = b.exceptionOrNull()?.message,
            examError = c.exceptionOrNull()?.message, session = previous.session, needsLogin = false, loading = false)
        persist()
    }
    private suspend fun <T> query(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    private fun persist() {
        try { cache.write(snapshot); mutable.value = mutable.value.copy(cacheError = null) }
        catch (_: Exception) { mutable.value = mutable.value.copy(cacheError = "本地保存失败，当前信息仅本次可见，请检查存储空间后刷新。") }
    }
    fun updateLocalDate() {
        val today = dateProvider()
        if (mutable.value.today != today) mutable.value = mutable.value.copy(today = today, week = snapshot.semester?.weekOn(today) ?: 1)
    }
    fun saveSemester(start: String, end: String): Boolean = try {
        val dates = SemesterDates(start.trim(), end.trim(), snapshot.semester?.label ?: "自定义学期", true).validate()
        snapshot = snapshot.copy(semester = dates)
        mutable.value = mutable.value.copy(semester = dates, week = dates.weekOn(dateProvider()) ?: 1,
            term = Term("cached", dates.label, dates.totalWeeks()), settingsError = null)
        persist(); true
    } catch (error: Exception) { mutable.value = mutable.value.copy(settingsError = error.message ?: "日期格式应为 YYYY-MM-DD"); false }
    fun shiftWeek(delta: Int) { mutable.value = mutable.value.copy(week = (mutable.value.week + delta).coerceIn(1, mutable.value.term.totalWeeks)) }
    fun resetWeek() { mutable.value = mutable.value.copy(week = snapshot.semester?.weekOn(dateProvider()) ?: 1) }
    fun selectLesson(lesson: ScheduleLesson?) { mutable.value = mutable.value.copy(selectedLesson = lesson) }
    fun logout() {
        generation++; request?.cancel(); source.logout(); sessions.clear()
        mutable.value = mutable.value.copy(session = null, password = "", loading = false, screen = AppScreen.Home, needsLogin = false, error = null, scoreError = null, examError = null)
    }
    fun clearLocalData() {
        logout(); snapshot = AcademicSnapshot(onboarded = true); mutable.value = restore(snapshot); persist()
    }
    override fun onCleared() { source.logout(); super.onCleared() }
}
