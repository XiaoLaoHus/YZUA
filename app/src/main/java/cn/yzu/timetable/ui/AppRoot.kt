package cn.yzu.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.data.model.ScheduleLesson
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.screens.*
import cn.yzu.timetable.ui.theme.*

private enum class MoreRoute { Hub, Scores, Exams, Classroom, About }

private const val TAB_TODAY = 0
private const val TAB_TIMETABLE = 1
private const val TAB_ASSISTANT = 2
private const val TAB_MORE = 3
private const val TAB_PROFILE = 4

private val tabs = listOf(
    TabItem("今日", Icons.Outlined.Today, Icons.Filled.Today),
    TabItem("课表", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TabItem("助手", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    TabItem("更多", Icons.Outlined.Apps, Icons.Filled.Apps),
    TabItem("我的", Icons.Outlined.Person, Icons.Filled.Person),
)

private val pushSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.92f, stiffness = 380f)

@Composable fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val prefs = rememberDisplayPreferences()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    var moreRoute by rememberSaveable { mutableStateOf(MoreRoute.Hub) }
    var detail by remember { mutableStateOf(false) }
    var calibrating by rememberSaveable { mutableStateOf(false) }
    var themeSheet by rememberSaveable { mutableStateOf(false) }
    var updateSheet by rememberSaveable { mutableStateOf(false) }
    fun profile() { tab = TAB_PROFILE; viewModel.dismissOnboarding() }
    fun login() { tab = TAB_PROFILE; viewModel.openLogin() }
    val onLesson: (ScheduleLesson) -> Unit = { viewModel.selectLesson(it); detail = true }
    val backdrop = rememberGlassBackdrop()
    val holder = rememberSaveableStateHolder()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = 66.dp + 12.dp + navBottom

    val context = androidx.compose.ui.platform.LocalContext.current
    // 课表、学期或提醒设置变化后重新安排本地提醒（只读本机缓存，不联网）
    LaunchedEffect(state.lessons, state.semester, state.periods, prefs.reminderEnabled, prefs.reminderLead, prefs.autoMute, prefs.seasonMode) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { cn.yzu.timetable.reminder.ClassReminders.reschedule(context) } }
    }
    // 开屏：仅冷启动显示约 2 秒
    var showSplash by remember { mutableStateOf(!cn.yzu.timetable.ui.screens.ColdStartGate.splashShown) }
    LaunchedEffect(showSplash) {
        if (showSplash) {
            kotlinx.coroutines.delay(1000)
            cn.yzu.timetable.ui.screens.ColdStartGate.splashShown = true
            showSplash = false
        }
    }
    // 每次冷启动自动检查更新（可在“更多”中关闭）
    LaunchedEffect(Unit) {
        if (prefs.updateCheckEnabled && !UpdateCenter.checkedThisProcess) {
            UpdateCenter.checkedThisProcess = true
            val outcome = UpdateCenter.run()
            if (outcome is UpdateChecker.Result.Failed) android.widget.Toast.makeText(context, UpdateChecker.failureText(context, outcome), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val updateResult = UpdateCenter.result.value
    if (updateResult is UpdateChecker.Result.UpdateAvailable && !UpdateCenter.dismissed.value) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        androidx.compose.material3.AlertDialog(onDismissRequest = { UpdateCenter.dismissed.value = true },
            icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("发现新版本 v${updateResult.version}") },
            text = { Text("本机版本 v${UpdateChecker.currentVersion()}。是否前往 GitHub Releases 下载更新？") },
            confirmButton = { TextButton({ UpdateCenter.dismissed.value = true; runCatching { uriHandler.openUri(updateResult.url) } }) { Text("去更新") } },
            dismissButton = { TextButton({ UpdateCenter.dismissed.value = true }) { Text("暂不") } })
    }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(prefs.disclaimerAccepted) {
        if (prefs.disclaimerAccepted && prefs.reminderEnabled && !prefs.notificationAsked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            prefs.markNotificationAsked()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    CompositionLocalProvider(LocalDisplayPreferences provides prefs, LocalBottomBarSpace provides barSpace) {
        Box(Modifier.fillMaxSize()) {
            // 内容层：录制为玻璃底栏的采样源（背景也在其中，保证模糊后完整覆盖）
            AmbientBackground(Modifier.glassBackdropSource(backdrop)) {
                AnimatedContent(tab, Modifier.fillMaxSize(), transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(240, delayMillis = 40)) + slideInHorizontally(pushSpring) { it / 12 * dir } + scaleIn(spring(0.9f, 400f), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(pushSpring) { -it / 16 * dir } + scaleOut(spring(0.9f, 400f), targetScale = 0.985f))
                }, label = "tabs") { current ->
                    holder.SaveableStateProvider("tab-$current") {
                        when (current) {
                            TAB_TODAY -> TodayScreen(state, ::profile, viewModel::dismissOnboarding, onLesson)
                            TAB_TIMETABLE -> TimetableScreen(state, viewModel, onLessonClick = onLesson, onProfile = { tab = TAB_PROFILE }, onCalibrate = { calibrating = true })
                            TAB_ASSISTANT -> AssistantScreen(state, viewModel, onBack = null)
                            TAB_MORE -> MoreTab(state, viewModel, moreRoute, { moreRoute = it }, ::login, holder, { themeSheet = true }, { updateSheet = true })
                            else -> ProfileScreen(state, viewModel, onLogin = ::login, onCalibrate = { calibrating = true })
                        }
                    }
                }
            }
            BackHandler(enabled = tab == TAB_MORE && moreRoute != MoreRoute.Hub) { moreRoute = MoreRoute.Hub }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp).widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(state.cacheError != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    FloatingNotice(state.cacheError.orEmpty(), Icons.Default.ErrorOutline, backdrop, error = true)
                }
                AnimatedVisibility(state.needsLogin && tab != TAB_PROFILE, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    FloatingNotice("登录后可刷新，已保存的信息仍可离线查看。", Icons.Default.CloudOff, backdrop, action = "登录刷新", onAction = ::login)
                }
                LiquidTabBar(tabs, tab, { tab = it }, backdrop)
            }

            val selected = state.selectedLesson
            var shownLesson by remember { mutableStateOf<ScheduleLesson?>(null) }
            if (selected != null) shownLesson = selected
            AnimatedVisibility(detail && selected != null, enter = slideInHorizontally(pushSpring) { it } + fadeIn(), exit = slideOutHorizontally(pushSpring) { it } + fadeOut()) {
                BackHandler { detail = false }
                shownLesson?.let { CourseDetailScreen(it, onBack = { detail = false }, periods = state.periods, currentWeek = state.semester?.weekOn(state.today), today = state.today) }
            }
            AnimatedVisibility(calibrating, enter = slideInVertically(spring(0.9f, 340f)) { it / 5 } + fadeIn(), exit = slideOutVertically(spring(0.9f, 340f)) { it / 5 } + fadeOut()) {
                SemesterCalibrationScreen(state.today, state.semester, state.settingsError, onSave = viewModel::saveSemester, onDismiss = { calibrating = false })
            }
            AnimatedVisibility(state.screen == AppScreen.Login, enter = slideInVertically(spring(0.9f, 340f)) { it / 6 } + fadeIn(), exit = slideOutVertically(spring(0.9f, 340f)) { it / 6 } + fadeOut()) {
                BackHandler { viewModel.closeLogin() }
                AmbientBackground {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回我的", viewModel::closeLogin, enabled = !state.loading, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("返回我的", style = MaterialTheme.typography.titleMedium)
                        }
                        Box(Modifier.weight(1f)) { LoginScreen(state, viewModel) }
                    }
                }
            }
            // 开屏期间隐藏预渲染课表页， warmed 后切tab不卡顿
            if (showSplash) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = 0f }) {
                    TimetableScreen(state, viewModel, onLessonClick = onLesson, onProfile = { tab = TAB_PROFILE }, onCalibrate = { calibrating = true })
                }
            }
            AnimatedVisibility(showSplash, enter = fadeIn(), exit = fadeOut(tween(450))) { SplashScreen() }
            if (themeSheet) ThemeModeSheet { themeSheet = false }
            if (updateSheet) UpdateSheet { updateSheet = false }
            AnimatedVisibility(!prefs.disclaimerAccepted, enter = fadeIn(), exit = fadeOut() + scaleOut(targetScale = 1.04f)) {
                DisclaimerScreen(onAccept = prefs::acceptDisclaimer)
            }
        }
    }
}

/** “更多”栏目：成绩、考试与智能助手的二级页面，使用 iOS 式推入/返回动画。 */
@Composable private fun MoreTab(
    state: AppUiState, viewModel: AppViewModel, route: MoreRoute, onRoute: (MoreRoute) -> Unit, onLogin: () -> Unit,
    holder: androidx.compose.runtime.saveable.SaveableStateHolder,
    onTheme: () -> Unit, onUpdate: () -> Unit,
) {
    AnimatedContent(route, Modifier.fillMaxSize(), transitionSpec = {
        if (targetState != MoreRoute.Hub) (slideInHorizontally(pushSpring) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(pushSpring) { -it / 4 } + fadeOut(tween(200)))
        else (slideInHorizontally(pushSpring) { -it / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(pushSpring) { it } + fadeOut(tween(200)))
    }, label = "more") { current ->
        holder.SaveableStateProvider("more-$current") {
            val back = { onRoute(MoreRoute.Hub) }
            when (current) {
                MoreRoute.Hub -> MoreScreen(state, { onRoute(MoreRoute.Scores) }, { onRoute(MoreRoute.Exams) }, { onRoute(MoreRoute.About) },
                        onTheme = onTheme, onUpdate = onUpdate, onClassroom = { onRoute(MoreRoute.Classroom) })
                MoreRoute.Scores -> ScoresScreen(state, viewModel::refresh, viewModel::setAllScoreTerms, onLogin, onBack = back)
                MoreRoute.Exams -> ExamsScreen(state, viewModel::refresh, onLogin, onBack = back)
                MoreRoute.Classroom -> ClassroomSearchScreen(viewModel, onBack = back)
                MoreRoute.About -> AboutScreen(onBack = back)
            }
        }
    }
}

@Composable private fun FloatingNotice(text: String, icon: ImageVector, backdrop: GlassBackdrop, error: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().liquidGlass(shape, backdrop = backdrop, cornerRadius = 22.dp, refraction = 8.dp, elevation = 12.dp,
        tint = color.copy(alpha = 0.08f)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (action != null && onAction != null) TextButton(onAction) { Text(action, color = color) }
    }
}
