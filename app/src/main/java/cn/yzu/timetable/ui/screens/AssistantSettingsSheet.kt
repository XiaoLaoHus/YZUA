package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.assistant.AssistantSettings
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AssistantSettingsSheet(settings: AssistantSettings, onDismiss: () -> Unit) {
    val glass = LocalGlass.current
    var url by remember { mutableStateOf(settings.baseUrl) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("模型接入设置", style = MaterialTheme.typography.headlineSmall)
            Text("支持任意 OpenAI 兼容接口（chat/completions）。密钥仅保存在本机应用私有目录，不会上传。",
                style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            OutlinedTextField(url, { url = it }, label = { Text("接口地址 Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = shape, placeholder = { Text("例如 https://open.bigmodel.cn/api/paas/v4") })
            OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = shape, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
            OutlinedTextField(model, { model = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = shape, placeholder = { Text("例如 glm-4-flash / deepseek-chat") })
            PrimaryCapsuleButton("保存", { settings.update(url.trim(), key.trim(), model.trim()); onDismiss() }, Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && key.isNotBlank() && model.isNotBlank(), icon = Icons.Default.Key)
        }
    }
}
