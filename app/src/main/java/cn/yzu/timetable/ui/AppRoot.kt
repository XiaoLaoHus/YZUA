package cn.yzu.timetable.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.screens.*

@Composable fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.screen == AppScreen.Login) LoginScreen(state, viewModel) else MainScaffold(state, viewModel)
}

@Composable private fun MainScaffold(state: AppUiState, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf(false) }
    if (detail && state.selectedLesson != null) {
        CourseDetailScreen(state.selectedLesson, onBack = { detail = false })
        return
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("课表") })
            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Assessment, null) }, label = { Text("成绩") })
            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.EventAvailable, null) }, label = { Text("考试") })
            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("AI 助手") })
            NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Person, null) }, label = { Text("我的") })
        }
    }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) {
        when (tab) {
            0 -> TimetableScreen(state, vm, onLessonClick = { vm.selectLesson(it); detail = true })
            1 -> ScoresScreen(state, vm::refresh, vm::setAllScoreTerms)
            2 -> ExamsScreen(state, vm::refresh)
            3 -> AssistantScreen()
            else -> ProfileScreen(state, vm)
        }
    } }
}
