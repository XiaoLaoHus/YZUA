package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.assistant.AssistantEngine
import cn.yzu.timetable.assistant.AssistantSettings
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AssistantSettingsSheet(settings: AssistantSettings, onDismiss: () -> Unit) {
    val glass = LocalGlass.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.baseUrl) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var modelMenu by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(18.dp)
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("模型接入设置", style = MaterialTheme.typography.headlineSmall)
            Text("默认使用 DeepSeek 接口。请先前往 DeepSeek 开放平台注册并创建 API Key：",
                style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
            Row(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(AssistantSettings.DOCS_URL) } }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("点击此处打开 api-docs.deepseek.com/zh-cn", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text("也支持任何 OpenAI 兼容接口。密钥仅保存在本机应用私有目录，不会上传。",
                style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
            OutlinedTextField(url, { url = it }, label = { Text("接口地址 Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = shape, placeholder = { Text(AssistantSettings.DEFAULT_URL) })
            OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = shape, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(model, { model = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = shape, placeholder = { Text("例如 deepseek-chat") },
                    trailingIcon = {
                        IconButton({
                            fetching = true; fetchError = null
                            scope.launch {
                                val list = AssistantEngine.fetchModels(url.trim(), key.trim())
                                models = list
                                fetching = false
                                if (list == null) fetchError = "获取模型列表失败：请检查地址与 Key"
                                else if (list.isNotEmpty()) modelMenu = true
                            }
                        }, enabled = !fetching && url.isNotBlank() && key.isNotBlank()) {
                            if (fetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.CloudDownload, "获取模型列表")
                        }
                    })
                DropdownMenu(modelMenu, { modelMenu = false }, modifier = Modifier.widthIn(min = 220.dp)) {
                    models.orEmpty().forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelMenu = false },
                            trailingIcon = if (m == model) {{ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }} else null)
                    }
                }
            }
            fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryCapsuleButton("保存", { settings.update(url.trim(), key.trim(), model.trim()); onDismiss() }, Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && key.isNotBlank() && model.isNotBlank(), icon = Icons.Default.Key)
        }
    }
}
