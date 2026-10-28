package cn.yzu.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.screens.*

@Composable fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var assistant by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf(false) }
    fun profile() { tab = 4; viewModel.dismissOnboarding() }
    fun login() { tab = 4; viewModel.openLogin() }
    val onLesson: (cn.yzu.timetable.data.model.ScheduleLesson) -> Unit = { viewModel.selectLesson(it); detail = true }
    if (state.screen == AppScreen.Login) {
        BackHandler { viewModel.closeLogin() }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TextButton(viewModel::closeLogin, enabled = !state.loading) { Icon(Icons.Default.ArrowBack, null); Text("返回我的") }
            Box(Modifier.weight(1f)) { LoginScreen(state, viewModel) }
        }
        return
    }
    val selected = state.selectedLesson
    if (detail && selected != null) {
        BackHandler { detail = false }
        CourseDetailScreen(selected, onBack = { detail = false }); return
    }
    if (assistant) {
        BackHandler { assistant = false }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TextButton({ assistant = false }) { Icon(Icons.Default.ArrowBack, null); Text("返回我的") }
            AssistantScreen()
        }
        return
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Today, null) }, label = { Text("今日") })
            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("课表") })
            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Assessment, null) }, label = { Text("成绩") })
            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.EventAvailable, null) }, label = { Text("考试") })
            NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Person, null) }, label = { Text("我的") })
        }
    }) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
        state.cacheError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        if (state.needsLogin && tab != 4) {
            Row(Modifier.padding(horizontal = 12.dp)) {
                Text("登录后可刷新，已保存的信息仍可离线查看。", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(::login) { Text("登录刷新") }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TodayScreen(state, ::profile, viewModel::dismissOnboarding, onLesson)
                1 -> TimetableScreen(state, viewModel, onLessonClick = onLesson)
                2 -> ScoresScreen(state, viewModel::refresh, viewModel::setAllScoreTerms, ::login)
                3 -> ExamsScreen(state, viewModel::refresh, ::login)
                else -> ProfileScreen(state, viewModel, onLogin = ::login, onAssistant = { assistant = true })
            }
        }
    } }
}
