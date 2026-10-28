package cn.yzu.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yzu.timetable.data.SessionRepository
import cn.yzu.timetable.data.model.*
import cn.yzu.timetable.data.urp.UrpHttpDataSource
import cn.yzu.timetable.data.urp.UrpScheduleDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

sealed interface AppScreen { data object Login : AppScreen; data object Home : AppScreen }
data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val account: String = "",
    val password: String = "",
    val session: UserSession? = null,
    val term: Term = currentTerm(),
    val week: Int = 1,
    val lessons: List<ScheduleLesson> = emptyList(),
    val scores: List<ScoreRecord> = emptyList(),
    val exams: List<ExamSchedule> = emptyList(),
    val scoreError: String? = null,
    val examError: String? = null,
    val scoresLoaded: Boolean = false,
    val examsLoaded: Boolean = false,
    val allScoreTerms: Boolean = false,
    val selectedLesson: ScheduleLesson? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

private fun currentTerm(): Term {
    val date = LocalDate.now()
    val autumn = date.monthValue >= 8
    val start = if (autumn) date.year else date.year - 1
    return Term("current", "$start-${start + 1} ${if (autumn) "秋季" else "春季"}（按当前日期）")
}

class AppViewModel(
    private val source: UrpScheduleDataSource = UrpHttpDataSource(),
    private val sessions: SessionRepository = SessionRepository(),
) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    fun setAccount(value: String) { mutable.value = mutable.value.copy(account = value, error = null) }
    fun setPassword(value: String) { mutable.value = mutable.value.copy(password = value, error = null) }
    fun setAllScoreTerms(value: Boolean) {
        if (mutable.value.loading || mutable.value.allScoreTerms == value) return
        mutable.value = mutable.value.copy(allScoreTerms = value, scores = emptyList(), scoresLoaded = false, scoreError = null)
        refresh()
    }

    // 保留离线演示数据源入口；真实数据源不接收密码。
    fun login() {
        val state = mutable.value
        if (state.account.isBlank() || state.password.isBlank()) {
            mutable.value = state.copy(error = "请输入学号和密码"); return
        }
        authenticate { source.login(state.account, state.password) }
    }

    fun loginWithSsoCookie(cookie: String) {
        if (cookie.isBlank()) { mutable.value = mutable.value.copy(error = "统一认证未完成"); return }
        val account = mutable.value.account
        authenticate { source.loginWithSsoCookie(account, cookie) }
    }

    private fun authenticate(login: suspend () -> UserSession) {
        if (mutable.value.loading) return
        val version = ++generation
        mutable.value = mutable.value.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val session = login()
                ensureActive()
                if (version != generation) return@launch
                sessions.save(session)
                mutable.value = mutable.value.copy(screen = AppScreen.Home, session = session, password = "")
                loadData(session, version)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == generation) mutable.value = mutable.value.copy(loading = false, error = error.message ?: "统一认证登录失败")
            }
        }
    }

    fun refresh() {
        val session = mutable.value.session ?: return
        if (mutable.value.loading) return
        val version = generation
        mutable.value = mutable.value.copy(loading = true, error = null, scoreError = null, examError = null)
        request = viewModelScope.launch { loadData(session, version) }
    }

    private suspend fun loadData(session: UserSession, version: Int) = coroutineScope {
        val term = mutable.value.term
        // 各接口独立失败，不把错误转换成“无成绩/无考试”。
        val schedule = async { query { source.getSchedule(session, term) } }
        val allTerms = mutable.value.allScoreTerms
        val scores = async { query { if (allTerms) source.getAllScores(session) else source.getScores(session) } }
        val exams = async { query { source.getExams(session) } }
        val a = schedule.await(); val b = scores.await(); val c = exams.await()
        ensureActive()
        if (version == generation) {
            val previous = mutable.value
            mutable.value = previous.copy(
                lessons = a.getOrDefault(previous.lessons),
                scores = b.getOrDefault(previous.scores),
                exams = c.getOrDefault(previous.exams),
                scoresLoaded = b.isSuccess || previous.scoresLoaded,
                examsLoaded = c.isSuccess || previous.examsLoaded,
                error = a.exceptionOrNull()?.message,
                scoreError = b.exceptionOrNull()?.message,
                examError = c.exceptionOrNull()?.message,
                loading = false,
            )
        }
    }

    private suspend fun <T> query(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }

    fun shiftWeek(delta: Int) { mutable.value = mutable.value.copy(week = (mutable.value.week + delta).coerceIn(1, mutable.value.term.totalWeeks)) }
    fun resetWeek() { mutable.value = mutable.value.copy(week = 1) }
    fun selectLesson(lesson: ScheduleLesson?) { mutable.value = mutable.value.copy(selectedLesson = lesson) }
    fun logout() {
        generation++
        request?.cancel()
        source.logout()
        sessions.clear()
        mutable.value = AppUiState()
    }
    override fun onCleared() { source.logout(); super.onCleared() }
}
