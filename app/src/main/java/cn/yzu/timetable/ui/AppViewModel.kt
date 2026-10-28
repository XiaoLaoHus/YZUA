package cn.yzu.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yzu.timetable.data.SessionRepository
import cn.yzu.timetable.data.model.*
import cn.yzu.timetable.data.urp.DemoUrpDataSource
import cn.yzu.timetable.data.urp.UrpScheduleDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppScreen { data object Login : AppScreen; data object Home : AppScreen }
data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val account: String = "",
    val password: String = "",
    val session: UserSession? = null,
    val term: Term = Term("2026-spring", "2026 春季学期"),
    val week: Int = 1,
    val lessons: List<ScheduleLesson> = emptyList(),
    val selectedLesson: ScheduleLesson? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(
    private val source: UrpScheduleDataSource = DemoUrpDataSource(),
    private val sessions: SessionRepository = SessionRepository(),
) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()

    fun setAccount(value: String) { mutable.value = mutable.value.copy(account = value, error = null) }
    fun setPassword(value: String) { mutable.value = mutable.value.copy(password = value, error = null) }
    fun login() { val s = mutable.value; if (s.account.isBlank() || s.password.isBlank()) { mutable.value = s.copy(error = "请输入学号和密码"); return }; viewModelScope.launch { mutable.value = s.copy(loading = true, error = null); runCatching { val session = source.login(s.account, s.password); val lessons = source.getSchedule(session, s.term); sessions.save(session); mutable.value = mutable.value.copy(screen = AppScreen.Home, session = session, lessons = lessons, loading = false, password = "") }.onFailure { mutable.value = mutable.value.copy(loading = false, error = it.message ?: "登录失败") } } }
    fun refresh() { val s = mutable.value; val session = s.session ?: return; viewModelScope.launch { mutable.value = s.copy(loading = true); runCatching { source.getSchedule(session, s.term) }.onSuccess { mutable.value = mutable.value.copy(lessons = it, loading = false) }.onFailure { mutable.value = mutable.value.copy(loading = false, error = "刷新失败") } } }
    fun shiftWeek(delta: Int) { mutable.value = mutable.value.copy(week = (mutable.value.week + delta).coerceIn(1, mutable.value.term.totalWeeks)) }
    fun resetWeek() { mutable.value = mutable.value.copy(week = 1) }
    fun selectLesson(lesson: ScheduleLesson?) { mutable.value = mutable.value.copy(selectedLesson = lesson) }
    fun logout() { sessions.clear(); mutable.value = AppUiState() }
}
