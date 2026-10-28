package cn.yzu.timetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.yzu.timetable.ui.theme.LocalGlass
import cn.yzu.timetable.ui.theme.liquidGlass
import cn.yzu.timetable.ui.theme.pressScale

/** 浮空底栏占用的高度，各页面据此留出底部空间。 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

val CardShape = RoundedCornerShape(24.dp)

@Composable private fun glassRipple() = ripple(color = MaterialTheme.colorScheme.primary)

@Composable fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardShape,
    tint: Color? = null,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val click = if (onClick != null) Modifier.pressScale(interaction).clip(shape).clickable(interaction, glassRipple(), role = Role.Button, onClick = onClick) else Modifier
    Column(modifier.then(click).liquidGlass(shape, tint = tint, strong = strong).padding(contentPadding), content = content)
}

@Composable fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable fun GlassIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 42.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size).pressScale(interaction, 0.9f).clip(CircleShape)
            .liquidGlass(CircleShape, strong = true, cornerRadius = size / 2)
            .clickable(interaction, glassRipple(), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.5f)) }
}

enum class BannerTone { Info, Warning, Error, Success }

@Composable fun StatusBanner(text: String, icon: ImageVector, tone: BannerTone = BannerTone.Info, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val accent = when (tone) { BannerTone.Info -> scheme.primary; BannerTone.Warning -> glass.warning; BannerTone.Error -> scheme.error; BannerTone.Success -> glass.success }
    GlassCard(modifier.fillMaxWidth(), tint = accent.copy(alpha = if (glass.dark) 0.14f else 0.09f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) TextButton(onAction) { Text(action, color = accent) }
        }
    }
}

@Composable fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textMuted)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            PrimaryCapsuleButton(action, onAction)
        }
    }
}

@Composable fun PrimaryCapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, loading: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick, modifier.heightIn(min = 50.dp).pressScale(interaction), enabled = enabled, interactionSource = interaction, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), disabledContentColor = Color.White.copy(alpha = 0.85f)),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp, strokeCap = StrokeCap.Round)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable fun GlassCapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = 48.dp).pressScale(interaction).clip(CircleShape).liquidGlass(CircleShape, strong = true)
            .clickable(interaction, glassRipple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val c = if (enabled) color else color.copy(alpha = 0.4f)
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = c)
    }
}

@Composable fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = LocalGlass.current.textMuted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textFaint)
    }
}

@Composable fun InfoChip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, icon: ImageVector? = null) {
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** 玻璃分段控件，选中块以弹簧动画滑动。 */
@Composable fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val segment = with(density) { (width / options.size.coerceAtLeast(1)).toDp() }
    val offset by animateDpAsState(segment * selected, spring(dampingRatio = 0.72f, stiffness = 420f), label = "segment")
    Box(modifier.height(42.dp).clip(CircleShape).liquidGlass(CircleShape).padding(3.dp).onSizeChanged { width = it.width }) {
        if (width > 0) Box(Modifier.offset(x = offset).width(segment).fillMaxHeight().clip(CircleShape)
            .liquidGlass(CircleShape, strong = true, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalGlass.current.dark) 0.22f else 0.08f)))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selected) MaterialTheme.colorScheme.primary else LocalGlass.current.textMuted, label = "segmentText")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable(enabled = enabled, role = Role.Tab) { onSelect(index) }, contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

@Composable fun GlassProgress(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.height(8.dp).clip(CircleShape).background(color.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
    }
}

@Composable fun LoadingStrip(visible: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(visible, modifier) {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    }
}

/** 课程配色：低饱和柔和色，按课程固定；深色模式自动压暗。 */
data class CourseColors(val container: Color, val content: Color, val accent: Color)

private val courseHues = listOf(212f, 168f, 128f, 38f, 12f, 330f, 266f, 232f, 62f, 192f)

fun courseColors(index: Int, dark: Boolean): CourseColors {
    val h = courseHues[((index % courseHues.size) + courseHues.size) % courseHues.size]
    return if (dark) CourseColors(Color.hsl(h, 0.22f, 0.20f), Color.hsl(h, 0.55f, 0.84f), Color.hsl(h, 0.45f, 0.58f))
    else CourseColors(Color.hsl(h, 0.48f, 0.93f), Color.hsl(h, 0.45f, 0.28f), Color.hsl(h, 0.42f, 0.56f))
}
