package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
