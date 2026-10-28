package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel

@Composable fun ProfileScreen(state: AppUiState, vm: AppViewModel, onLogin: () -> Unit, onAssistant: () -> Unit) {
    var datesOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var start by remember(state.semester) { mutableStateOf(state.semester?.firstMonday.orEmpty()) }
    var end by remember(state.semester) { mutableStateOf(state.semester?.lastDay.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Card { ListItem(leadingContent = { Icon(Icons.Default.Person, null) },
            headlineContent = { Text(state.session?.displayName ?: "YZUA") },
            supportingContent = { Text(if (state.session != null) "本次登录有效 · 可在线刷新" else if (state.scheduleUpdated != null) "离线查看本地数据" else "登录一次，导入课表与查询结果") }) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.session == null) Button(onLogin, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) { Text("统一认证登录 / 刷新信息") }
        else {
            Button(vm::refresh, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) { Text("刷新课表、成绩与考试") }
            OutlinedButton(vm::logout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("退出登录（保留本地数据）") }
        }
        Text("启动不自动登录。成绩和考试需要更新时，登录一次再刷新即可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card { Column {
            ListItem(headlineContent = { Text("课表") }, supportingContent = { Text(state.scheduleUpdated?.let { "更新于 $it" } ?: "尚未导入") })
            ListItem(headlineContent = { Text("当前范围成绩") }, supportingContent = { Text(state.scoresUpdated?.let { "更新于 $it" } ?: "尚未导入") })
            ListItem(headlineContent = { Text("考试安排") }, supportingContent = { Text(state.examsUpdated?.let { "更新于 $it" } ?: "尚未导入") })
        } }
        Card(Modifier.clickable { datesOpen = true }) { ListItem(leadingContent = { Icon(Icons.Default.DateRange, null) },
            headlineContent = { Text("学期日期与周次校准") }, supportingContent = { Text(state.semester?.let { "首周 ${it.firstMonday} · 结束 ${it.lastDay}" } ?: "设置首周周一和学期结束日期") }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) }
        state.semesterReminder?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        Card(Modifier.clickable(onClick = onAssistant)) { ListItem(leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
            headlineContent = { Text("智能助手") }, supportingContent = { Text("预留页面") }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) }
        OutlinedButton({ clearOpen = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) { Text("清除本地数据 / 更换账号") }
        Text("查询结果仅保存在应用私有目录，不备份到云端；账号密码与 API Cookie 不随查询结果保存。更换账号前请先清除本地数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (datesOpen) AlertDialog(onDismissRequest = { datesOpen = false }, title = { Text("校准学期日期") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("请按学校校历填写。首周开始必须是周一，日期格式 YYYY-MM-DD。日期用于今日课程与学期到期提醒。")
            OutlinedTextField(start, { start = it }, label = { Text("首周周一") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("学期结束日期") }, singleLine = true)
            state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ if (vm.saveSemester(start, end)) datesOpen = false }) { Text("保存") } }, dismissButton = { TextButton({ datesOpen = false }) { Text("取消") } })
    if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, title = { Text("清除本机查询结果？") },
        text = { Text("会清除课表、成绩、考试安排和学期设置，并退出当前登录。之后可重新登录导入。") },
        confirmButton = { TextButton({ vm.clearLocalData(); clearOpen = false }) { Text("清除") } }, dismissButton = { TextButton({ clearOpen = false }) { Text("取消") } })
}
