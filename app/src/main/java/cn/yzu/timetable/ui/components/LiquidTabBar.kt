package cn.yzu.timetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yzu.timetable.ui.theme.GlassBackdrop
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * 浮空椭圆玻璃底栏：
 * - 选中块两条边使用不同刚度的弹簧，前缘先到、后缘跟随，呈现液体拉伸；
 * - 按住时选中块像透镜一样放大，可左右拖动切换，松手吸附到最近一项；
 * - 底栏实时模糊并折射下方内容。
 */
@Composable fun LiquidTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, backdrop: GlassBackdrop?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val inset = with(density) { 5.dp.toPx() }
    var width by remember { mutableIntStateOf(0) }
    val itemWidth = if (width == 0) 0f else (width - inset * 2) / items.size
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun edgesFor(index: Int) = (inset + itemWidth * index) to (inset + itemWidth * (index + 1))
    fun settle(index: Int) {
        val (l, r) = edgesFor(index)
        val forward = l >= left.value
        scope.launch { left.animateTo(l, spring(dampingRatio = 0.72f, stiffness = if (forward) 260f else 720f)) }
        scope.launch { right.animateTo(r, spring(dampingRatio = 0.72f, stiffness = if (forward) 720f else 260f)) }
    }
    LaunchedEffect(selected, itemWidth) {
        if (itemWidth <= 0f) return@LaunchedEffect
        if (!initialized) { val (l, r) = edgesFor(selected); left.snapTo(l); right.snapTo(r); initialized = true } else settle(selected)
    }
    val lens by animateFloatAsState(if (pressed) 1.10f else 1f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "lens")

    Box(
        modifier.height(66.dp).fillMaxWidth()
            .liquidGlass(CircleShape, backdrop = backdrop, cornerRadius = 33.dp, blur = 18.dp, refraction = 16.dp, elevation = 22.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(items.size, itemWidth) {
                if (itemWidth <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var x = down.position.x
                    var dragging = false
                    var hovered = ((x - inset) / itemWidth).toInt().coerceIn(0, items.lastIndex)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        x = change.position.x
                        if (!dragging && abs(x - down.position.x) > slop) dragging = true
                        if (dragging) {
                            change.consume()
                            val half = itemWidth / 2f
                            val center = x.coerceIn(inset + half, width - inset - half)
                            scope.launch { left.animateTo(center - half, spring(dampingRatio = 0.8f, stiffness = 1400f)) }
                            scope.launch { right.animateTo(center + half, spring(dampingRatio = 0.8f, stiffness = 1400f)) }
                            val now = ((center - inset) / itemWidth).toInt().coerceIn(0, items.lastIndex)
                            if (now != hovered) { hovered = now; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }
                    }
                    pressed = false
                    val target = ((x - inset) / itemWidth).toInt().coerceIn(0, items.lastIndex)
                    if (target != currentSelected) {
                        if (!dragging) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(target)
                    } else settle(target)
                }
            },
    ) {
        if (initialized) {
            val glass = LocalGlass.current
            Box(
                Modifier
                    .offset { IntOffset(left.value.roundToInt(), 0) }
                    .width(with(density) { (right.value - left.value).coerceAtLeast(0f).toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 5.dp)
                    .graphicsLayer { scaleX = lens; scaleY = lens }
                    .liquidGlass(CircleShape, cornerRadius = 28.dp, strong = true,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (glass.dark) 0.20f else 0.10f)),
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 5.dp)) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val tint by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), label = "tabTint")
                val scale by animateFloatAsState(if (active) 1f else 0.92f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "tabScale")
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics {
                        role = Role.Tab; this.selected = active
                        onClick(label = item.label) { currentOnSelect(index); true }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (active) item.selectedIcon else item.icon, null, tint = tint,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, color = tint, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
