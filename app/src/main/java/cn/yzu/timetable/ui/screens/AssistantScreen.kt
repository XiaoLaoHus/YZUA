package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun AssistantScreen() { Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(58.dp)); Spacer(Modifier.height(18.dp)); Text("AI 助手", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp)); Text("智能问答功能即将上线", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("先用课表页管理你的每一节课", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
