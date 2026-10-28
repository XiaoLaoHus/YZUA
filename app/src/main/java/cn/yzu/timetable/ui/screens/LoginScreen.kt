package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel

@Composable fun LoginScreen(state: AppUiState, vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp)); Text("扬大课表", style = MaterialTheme.typography.headlineLarge); Text("Yangzhou University Timetable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp)); OutlinedTextField(state.account, vm::setAccount, label = { Text("学号") }, leadingIcon = { Icon(Icons.Default.School, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp)); OutlinedTextField(state.password, vm::setPassword, label = { Text("密码") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp)); Button(vm::login, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("登录课表") }
        Spacer(Modifier.height(14.dp)); Text("登录扬州大学 URP，课表仅保存在本机内存中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    } }
}
