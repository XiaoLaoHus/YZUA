package cn.yzu.timetable.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.SsoLoginActivity
import cn.yzu.timetable.data.urp.SsoCookieHandoff
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel

@Composable fun LoginScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            SsoCookieHandoff.take()?.let(vm::loginWithSsoCookie)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp)); Text("扬大课表", style = MaterialTheme.typography.headlineLarge); Text("Yangzhou University Timetable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp)); OutlinedTextField(state.account, vm::setAccount, label = { Text("学号（可选）") }, leadingIcon = { Icon(Icons.Default.School, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp)); Button({ launcher.launch(Intent(context, SsoLoginActivity::class.java).putExtra("account", state.account.trim())) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("使用扬大统一认证登录") }
            Spacer(Modifier.height(14.dp)); Text("请先连接校园网或手机 VPN。登录页由扬州大学统一认证提供，查询会话仅在本次运行中保留", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
