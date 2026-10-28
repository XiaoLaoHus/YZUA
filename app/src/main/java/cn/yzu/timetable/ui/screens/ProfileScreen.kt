package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel

@Composable fun ProfileScreen(state: AppUiState, vm: AppViewModel) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text("我的", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(18.dp)); Card { ListItem(leadingContent = { Icon(Icons.Default.Storage, null) }, headlineContent = { Text(state.session?.displayName ?: "扬大学生") }, supportingContent = { Text(state.session?.studentId ?: "未登录") }) }; Spacer(Modifier.height(16.dp)); Text("数据来源", style = MaterialTheme.typography.labelLarge); Text(state.session?.source?.label ?: "演示数据", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.height(22.dp)); OutlinedButton(vm::logout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("退出登录") } } }
