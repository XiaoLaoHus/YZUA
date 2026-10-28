package cn.yzu.timetable.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.SsoLoginActivity
import cn.yzu.timetable.data.urp.SsoCookieHandoff
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass

@Composable fun LoginScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            SsoCookieHandoff.take()?.let(vm::loginWithSsoCookie)
        }
    }
    val glass = LocalGlass.current
    var showPassword by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(18.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.35f else 0.65f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.25f else 0.45f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF3D7BFF), Color(0xFF7C6CFF), Color(0xFF45C6D6)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("YZUA", style = MaterialTheme.typography.headlineLarge)
        Text("Yangzhou University Agent", style = MaterialTheme.typography.bodyMedium, color = glass.textMuted)
        Spacer(Modifier.height(28.dp))
        GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(20.dp)) {
            Text("统一认证登录", style = MaterialTheme.typography.titleLarge)
            Text("使用扬州大学统一身份认证账号", style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(state.account, vm::setAccount, label = { Text("学号") }, leadingIcon = { Icon(Icons.Default.Badge, null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.password, vm::setPassword, label = { Text("统一认证密码") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPassword) "隐藏密码" else "显示密码") } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = fieldShape, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            PrimaryCapsuleButton("登录扬州大学统一认证", {
                val login = Intent(context, SsoLoginActivity::class.java).putExtra("account", state.account.trim()).putExtra("password", state.password)
                launcher.launch(login)
                vm.setPassword("")
            }, Modifier.fillMaxWidth().height(54.dp), enabled = !state.loading && state.account.isNotBlank() && state.password.isNotBlank(), loading = state.loading)
            Spacer(Modifier.height(4.dp))
            TextButton({
                vm.setPassword("")
                launcher.launch(Intent(context, SsoLoginActivity::class.java).putExtra("account", state.account.trim()).putExtra("manual", true))
            }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("使用学校网页登录（验证码 / 登录异常）") }
        }
        state.error?.let { Spacer(Modifier.height(12.dp)); StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error) }
        Spacer(Modifier.height(12.dp))
        StatusBanner("请先连接校园网或手机 VPN。密码只用于本次认证，不会保存到本机；遇到验证码或二次认证时会提示改用学校网页登录。", Icons.Default.Wifi)
        Spacer(Modifier.height(24.dp))
    }
}
