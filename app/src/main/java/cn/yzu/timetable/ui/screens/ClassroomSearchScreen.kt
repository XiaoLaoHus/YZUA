package cn.yzu.timetable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.classroom.*
import cn.yzu.timetable.ui.AppViewModel
import cn.yzu.timetable.ui.components.*
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.liquidGlass
import cn.yzu.timetable.ui.theme.TabularNumbers
import cn.yzu.timetable.ui.theme.pressScale
import kotlinx.coroutines.launch

private val dayOptions = listOf("今天", "明天", "后天")

@Composable fun ClassroomSearchScreen(vm: AppViewModel, onBack: () -> Unit) {
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val source = remember(vm) { UrpClassroomDataSource(vm::verifiedCookieHeader) }
    var campuses by remember { mutableStateOf<List<CampusOption>?>(null) }
    var campus by remember { mutableStateOf<CampusOption?>(null) }
    var buildings by remember { mutableStateOf<List<BuildingOption>?>(null) }
    var building by remember { mutableStateOf<BuildingOption?>(null) }
    var dayplus by remember { mutableIntStateOf(0) }
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(2) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FreeRoomResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val loggedIn = vm.state.collectAsState().value.session != null

    fun loadCampuses() = scope.launch {
        error = null; loading = true
        try {
            val list = source.campuses()
            campuses = list
            campus = list.firstOrNull()
        } catch (e: Exception) { error = e.message } finally { loading = false }
    }
    LaunchedEffect(source) { if (loggedIn) loadCampuses() }
    LaunchedEffect(campus) {
        buildings = null; building = null
        val c = campus ?: return@LaunchedEffect
        try { buildings = source.buildings(c.number) } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(building, dayplus, start, end) {
        val c = campus; val b = building
        if (c == null || b == null) return@LaunchedEffect
        error = null; loading = true; result = null
        try {
            source.selectBuilding(c, b)
            val periods = (minOf(start, end)..maxOf(start, end)).toList()
            result = source.freeRooms(periods, dayplus)
        } catch (e: Exception) { error = e.message } finally { loading = false }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = LocalBottomBarSpace.current + 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubPageHeader("查找空教室", "按校区、教学楼、日期与节次查询", onBack)
        GlassCard(Modifier.fillMaxWidth(), strong = true, contentPadding = PaddingValues(18.dp)) {
            Text("筛选", style = MaterialTheme.typography.titleSmall, color = glass.textMuted)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionCapsule("校区", campus?.name ?: "加载中", campuses?.map { it.name } ?: emptyList(), Modifier.weight(1f)) { name ->
                    campus = campuses?.first { it.name == name }
                }
                OptionCapsule("教学楼", building?.name ?: "选择校区", buildings?.map { it.name } ?: emptyList(), Modifier.weight(1f)) { name ->
                    building = buildings?.first { it.name == name }
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassSegmented(dayOptions, dayplus, { dayplus = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PeriodCapsule("开始", start, { start = it; if (end < it) end = it }, Modifier.weight(1f))
                PeriodCapsule("结束", end, { end = it.coerceAtLeast(start) }, Modifier.weight(1f))
            }
        }
        LoadingStrip(loading)
        error?.let { StatusBanner(it, Icons.Default.ErrorOutline, BannerTone.Error, action = if (campuses == null) "重试" else null, onAction = { loadCampuses() }) }
        if (!loggedIn && !loading) {
            EmptyState(Icons.Default.Login, "需要登录教务会话", "空教室查询使用本次登录的教务会话，请先在“我的”中登录。")
        }
        val r = result
        if (r != null && !loading) {
            if (r.buildings.isEmpty()) {
                EmptyState(Icons.Default.MeetingRoom, "该时段没有空闲教室", dayOptions[dayplus] + "第 " + start + "–" + end + " 节，" + building?.name.orEmpty() + " 已全部排课。")
            } else r.buildings.forEach { b ->
                SectionLabel(b.name, trailing = b.rooms.size.toString() + " 间空闲")
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    b.rooms.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { room -> RoomChip(room, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Text("结果来自教务系统实时查询，仅表示该楼在所选节次无排课。", style = MaterialTheme.typography.labelSmall, color = glass.textFaint)
        }
    }
}

@Composable private fun OptionCapsule(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().height(50.dp).pressScale(interaction, 0.96f).clip(CircleShape).liquidGlass(CircleShape, strong = true)
            .clickable(interaction, ripple(color = MaterialTheme.colorScheme.primary), role = Role.Button, onClick = { open = options.isNotEmpty() })
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = LocalGlass.current.textFaint)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = LocalGlass.current.textMuted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.widthIn(min = 180.dp)) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable private fun RoomChip(room: FreeRoom, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        .padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(room.name, style = MaterialTheme.typography.titleSmall.merge(TabularNumbers))
            if (room.seats.isNotBlank()) Text(room.seats + " 座", style = MaterialTheme.typography.labelSmall, color = LocalGlass.current.textMuted)
        }
    }
}
