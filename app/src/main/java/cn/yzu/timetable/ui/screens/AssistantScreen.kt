package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.assistant.*
import cn.yzu.timetable.classroom.UrpClassroomDataSource
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/** 智能助手：用户自配 OpenAI 兼容模型，ReAct 循环调用本机课表/成绩/考试/空教室工具。 */
@Composable fun AssistantScreen(state: AppUiState, vm: AppViewModel, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val settings = remember { AssistantSettings.get(context) }
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val classroom = remember(vm) { UrpClassroomDataSource(vm::verifiedCookieHeader) }
    val prefs = LocalDisplayPreferences.current
    val season = YzuTimetable.season(state.today, prefs.seasonMode)

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        messages += ChatMessage("user", text)
        busy = true
        step = "思考中…"
        scope.launch {
            val history = messages.filter { it.role != "system" }.toList()
            val reply = AssistantEngine.chat(settings, history.dropLast(1), text, AssistantContext(state, classroom, season, prefs)) { step = it }
            messages += ChatMessage("assistant", reply)
            busy = false
            step = null
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回更多", onBack, size = 34.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("智能助手", style = MaterialTheme.typography.headlineMedium)
                Text(if (settings.configured) settings.model else "未配置模型接口", style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            }
            GlassIconButton(Icons.Default.Tune, "助手设置", { settingsOpen = true }, size = 40.dp)
        }
        if (!settings.configured) {
            Box(Modifier.padding(18.dp)) {
                EmptyState(Icons.Default.SmartToy, "先配置模型接口", "助手使用你自己提供的 OpenAI 兼容接口（如智谱、DeepSeek、OpenAI）。密钥只保存在本机应用目录。",
                    action = "去配置", onAction = { settingsOpen = true })
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(messages) { i, m ->
                    if (m.role == "user") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                                .background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(m.content, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        GlassCard(Modifier.fillMaxWidth().widthIn(max = 340.dp), contentPadding = PaddingValues(14.dp)) {
                            MarkdownText(m.content, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(step ?: "思考中…", style = MaterialTheme.typography.labelMedium, color = glass.textMuted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("问问课程、成绩、考试或空教室…") }, maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.35f else 0.65f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.25f else 0.45f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Send, "发送", { send() }, enabled = !busy && input.isNotBlank(), size = 48.dp)
            }
        }
        Spacer(Modifier.height(LocalBottomBarSpace.current))
    }
    if (settingsOpen) AssistantSettingsSheet(settings) { settingsOpen = false }
}
