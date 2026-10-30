package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import cn.yzu.timetable.ui.theme.AmbientBackground
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import cn.yzu.timetable.ui.theme.pressScale
import cn.yzu.timetable.ui.theme.liquidGlass
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
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

/** 自定义课表：按名称、星期、节次、地点、教师与周次添加，仅保存在本机界面偏好中。 */
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
    if (editorOpen) CustomLessonSheet(onDismiss = { editorOpen = false }, onSave = { prefs.addCustomLesson(it) })
}

private val fieldShape = RoundedCornerShape(18.dp)

@Composable private fun GlassField(value: String, onValue: (String) -> Unit, label: String) {
    val glass = LocalGlass.current
    OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = fieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.35f else 0.65f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (glass.dark) 0.25f else 0.45f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ))
}

/** 玻璃胶囊下拉，用于节次选择。 */
@Composable internal fun PeriodCapsule(label: String, value: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().height(48.dp).pressScale(interaction, 0.96f).clip(CircleShape).liquidGlass(CircleShape, strong = true)
            .clickable(interaction, glassRippleLocal(), role = Role.Button, onClick = { open = true }).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textMuted)
            Text("第 $value 节", style = MaterialTheme.typography.titleSmall.merge(TabularNumbers))
            Icon(Icons.Default.ArrowDropDown, null, tint = LocalGlass.current.textMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.width(150.dp)) {
            (1..12).forEach { p ->
                DropdownMenuItem(text = { Text("第 $p 节", style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers)) },
                    onClick = { onSelect(p); open = false },
                    trailingIcon = if (p == value) {{ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }} else null)
            }
        }
    }
}

@Composable private fun glassRippleLocal() = ripple(color = MaterialTheme.colorScheme.primary)

@Composable private fun WeekChip(week: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), label = "weekChip")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else LocalGlass.current.textMuted, label = "weekChipText")
    Box(Modifier.size(40.dp, 34.dp).pressScale(interaction, 0.9f).clip(RoundedCornerShape(11.dp))
        .background(bg).clickable(interaction, glassRippleLocal(), role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        Text("$week", style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = fg)
    }
}

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable internal fun CustomLessonEditorContent(onSave: (CustomLessonDraft) -> Unit) {
    val glass = LocalGlass.current
    var name by remember { mutableStateOf("") }
    var classroom by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(1) }
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(2) }
    var weekMode by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    val weeks = when (weekMode) {
        1 -> (1..20 step 2).toList(); 2 -> (2..20 step 2).toList(); 3 -> picked.sorted(); else -> (1..20).toList()
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassField(name, { name = it }, "课程名称")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodCapsule("开始", start, { start = it; if (end < it) end = it }, Modifier.weight(1f))
            PeriodCapsule("结束", end, { end = it.coerceAtLeast(start) }, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("星期", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayNames.forEachIndexed { index, label ->
                    val d = index + 1
                    val interaction = remember(d) { MutableInteractionSource() }
                    val selected = d == day
                    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "dayChip")
                    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "dayChipText")
                    Box(Modifier.weight(1f).height(42.dp).pressScale(interaction, 0.92f).clip(CircleShape)
                        .liquidGlass(CircleShape, strong = true)
                        .background(bg)
                        .clickable(interaction, glassRippleLocal(), role = Role.RadioButton, onClick = { day = d }),
                        contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                    }
                }
            }
        }
        GlassField(classroom, { classroom = it }, "地点（如 扬子津东校区 文津楼 N104）")
        GlassField(teacher, { teacher = it }, "教师（可选）")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("上课周次", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
            GlassSegmented(listOf("每周", "单周", "双周", "自选"), weekMode, { weekMode = it }, Modifier.fillMaxWidth())
            if (weekMode == 3) {
                Spacer(Modifier.height(4.dp))
                (1..20).chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { w -> WeekChip(w, w in picked) { picked = if (w in picked) picked - w else picked + w } }
                    }
                }
                Text(if (picked.isEmpty()) "请至少选择一周" else "已选 " + picked.size + " 周", style = MaterialTheme.typography.labelSmall,
                    color = if (picked.isEmpty()) MaterialTheme.colorScheme.error else glass.textFaint)
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryCapsuleButton("添加", { onSave(CustomLessonDraft(name.trim(), teacher.trim(), classroom.trim(), day, start, end, weeks)) },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank() && (weekMode != 3 || picked.isNotEmpty()))
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CustomLessonSheet(onDismiss: () -> Unit, onSave: (CustomLessonDraft) -> Unit) {
    val glass = LocalGlass.current
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface) {
        Text("添加自定义课程", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp))
        CustomLessonEditorContent(onSave)
    }
}
