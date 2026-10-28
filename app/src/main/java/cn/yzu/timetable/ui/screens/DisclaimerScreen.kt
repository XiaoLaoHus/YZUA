package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.AmbientBackground
import cn.yzu.timetable.ui.theme.LocalGlass

val disclaimerPoints = listOf(
    "YZUA（Yangzhou University Agent）是个人开发的学习与研究性质项目，不是扬州大学官方应用，与扬州大学及其任何部门均无关联。",
    "登录使用学校官方统一认证页面，账号密码仅用于当次认证，不会保存；查询到的课表、成绩和考试信息只保存在本机。",
    "软件显示的信息可能存在延迟或错误，请以学校官方系统和通知为准。",
    "因使用本软件产生的任何问题与后果，由用户自行承担。",
)

/** 首次打开时的使用须知：必须勾选“已阅读并同意”才能继续。 */
@Composable fun DisclaimerScreen(onAccept: () -> Unit) {
    val glass = LocalGlass.current
    var agreed by remember { mutableStateOf(false) }
    AmbientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                YzuaLogo(76.dp)
                Spacer(Modifier.height(16.dp))
                Text("使用须知", style = MaterialTheme.typography.headlineMedium)
                Text("开始使用 YZUA 前，请仔细阅读", style = MaterialTheme.typography.bodyMedium, color = glass.textMuted)
                Spacer(Modifier.height(22.dp))
                GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(20.dp)) {
                    disclaimerPoints.forEachIndexed { i, text ->
                        Row(Modifier.padding(vertical = 7.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 14.dp), strong = true, contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Checkbox) { agreed = !agreed }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(agreed, { agreed = it })
                    Text("我已阅读并同意以上内容", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(10.dp))
                PrimaryCapsuleButton("继续", onAccept, Modifier.fillMaxWidth(), enabled = agreed)
            }
        }
    }
}
