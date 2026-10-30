package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.AppUiState
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.components.CustomLessonDraft
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimetableSettingsSheet(state: AppUiState, season: ScheduleSeason, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismiss, sheetState = sheet, containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("课表设置", style = MaterialTheme.typography.headlineSmall)
            GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), strong = true) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Weekend, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("隐藏周末", style = MaterialTheme.typography.titleMedium)
                        Text("只显示周一至周五，列宽更大", style = MaterialTheme.typography.bodySmall, color = glass.textMuted)
                    }
                    Switch(prefs.hideWeekend, prefs::updateHideWeekend)
                }
            }
            Text("作息时间", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
            GlassSegmented(SeasonMode.entries.map { it.label }, SeasonMode.entries.indexOf(prefs.seasonMode), { prefs.updateSeasonMode(SeasonMode.entries[it]) }, Modifier.fillMaxWidth())
            val (switchDate, nextSeason) = YzuTimetable.nextSwitch(state.today)
            Text(
                if (prefs.seasonMode == SeasonMode.Auto) "当前按${season.label}时间，${switchDate.format(DateTimeFormatter.ofPattern("M月d日"))}起自动切换为${nextSeason.label}。"
                else "已固定使用${prefs.seasonMode.label}时间，选择“自动”可按 5 月 1 日 / 10 月 1 日切换。",
                style = MaterialTheme.typography.bodySmall, color = glass.textMuted,
            )
            PeriodTableCard(season)
            CustomLessonSection()
            GlassCapsuleButton("刷新课表", onRefresh, Modifier.fillMaxWidth(), enabled = !state.loading, icon = Icons.Default.Refresh)
            Text(state.scheduleUpdated?.let { "本地课表更新于 $it。刷新需要本次已登录；未登录时会提示前往“我的”。" } ?: "尚未导入课表。",
                style = MaterialTheme.typography.bodySmall, color = glass.textFaint)
        }
    }
}

/** 扬大上课时间表，按大节分组，一眼可读。 */
@Composable fun PeriodTableCard(season: ScheduleSeason) {
    val glass = LocalGlass.current
    val slots = YzuTimetable.slots(season)
    GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("扬州大学上课时间", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            InfoChip(season.label)
        }
        Spacer(Modifier.height(8.dp))
        YzuTimetable.blocks.forEach { block ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(76.dp)) {
                    Text(block.name, style = MaterialTheme.typography.labelLarge)
                    Text(block.part, style = MaterialTheme.typography.labelSmall, color = glass.textFaint)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.periods.forEach { n ->
                        val s = slots[n - 1]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                                Text("$n", style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${s.startText} – ${s.endText}", style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
                        }
                    }
                }
            }
            if (block != YzuTimetable.blocks.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

/** 自定义课表：按名称、星期、节次、地点、教师添加，仅保存在本机界面偏好中。 */
@Composable fun CustomLessonSection() {
    val prefs = LocalDisplayPreferences.current
    val glass = LocalGlass.current
    var editorOpen by remember { mutableStateOf(false) }
    SectionLabel("自定义课表", trailing = if (prefs.customLessons.isNotEmpty()) "${prefs.customLessons.size} 门" else null)
    GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (prefs.customLessons.isEmpty()) {
            Text("还没有自定义课程。添加后会和教务课表一起显示在课表与今日中。", style = MaterialTheme.typography.bodySmall, color = glass.textMuted,
                modifier = Modifier.padding(vertical = 6.dp))
        } else prefs.customLessons.forEach { lesson ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lesson.courseName, style = MaterialTheme.typography.titleSmall)
                    Text("周${lesson.dayOfWeek} · 第 ${lesson.startPeriod}-${lesson.endPeriod} 节" + (if (lesson.classroom.isNotBlank()) " · ${lesson.classroom}" else ""),
                        style = MaterialTheme.typography.labelMedium, color = glass.textMuted)
                }
                GlassIconButton(Icons.Default.DeleteOutline, "删除 ${lesson.courseName}", { prefs.removeCustomLesson(lesson.id) }, size = 34.dp,
                    tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(6.dp))
        GlassCapsuleButton("添加课程", { editorOpen = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Add)
    }
    if (editorOpen) CustomLessonDialog({ editorOpen = false }) { prefs.addCustomLesson(it) }
}

@Composable private fun CustomLessonDialog(onDismiss: () -> Unit, onSave: (CustomLessonDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var classroom by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(1) }
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(2) }
    var parity by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加自定义课程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodDropdown("开始节", start) { start = it; if (end < it) end = it }
                    PeriodDropdown("结束节", end) { end = it.coerceAtLeast(start) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        val selected = d == day
                        Box(Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable { day = d }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("${"一二三四五六日"[d - 1]}", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                OutlinedTextField(classroom, { classroom = it }, label = { Text("地点（如 扬子津东校区 文津楼 N104）") }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教师（可选）") }, singleLine = true)
                GlassSegmented(listOf("每周", "单周", "双周"), parity, { parity = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(CustomLessonDraft(name.trim(), teacher.trim(), classroom.trim(), day, start, end, parity)); onDismiss() },
            enabled = name.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

@Composable private fun PeriodDropdown(label: String, value: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField("$value", {}, label = { Text(label) }, readOnly = true, modifier = Modifier.width(110.dp),
            trailingIcon = { IconButton({ open = true }) { Icon(Icons.Default.ArrowDropDown, null) } })
        DropdownMenu(open, { open = false }) {
            (1..12).forEach { p -> DropdownMenuItem(text = { Text("第 $p 节") }, onClick = { onSelect(p); open = false }) }
        }
    }
}
