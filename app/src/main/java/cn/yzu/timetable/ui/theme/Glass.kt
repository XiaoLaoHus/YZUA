package cn.yzu.timetable.ui.theme

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 自绘液态玻璃，不引入第三方依赖：
 * - 内容区录制到 GraphicsLayer（GlassBackdrop），浮在其上的玻璃元素按自身位置采样这层内容；
 * - Android 12+ 使用真实背景模糊；Android 13+ 叠加 AGSL 边缘折射与轻微色散，形成液态玻璃的透镜边；
 * - 更低版本自动退化为高不透明度磨砂面，保证可读性。
 * 玻璃元素不能放在它所采样的内容层内部，否则会递归绘制。
 */
@Stable
class GlassBackdrop {
    internal var layer by mutableStateOf<GraphicsLayer?>(null)
    internal var origin by mutableStateOf(Offset.Zero)
    /** 内容层每次重绘递增，通知采样它的玻璃元素同步重绘。 */
    internal var version by mutableIntStateOf(0)
}

@Composable fun rememberGlassBackdrop() = remember { GlassBackdrop() }

fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = composed {
    val layer = rememberGraphicsLayer()
    DisposableEffect(backdrop, layer) {
        backdrop.layer = layer
        onDispose { if (backdrop.layer === layer) backdrop.layer = null }
    }
    onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { backdrop.version++ }
        }
}

private const val REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float band;

float sdRoundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = coord - c;
    float r = min(radius, min(c.x, c.y));
    float inside = -sdRoundRect(p, c, r);
    if (inside > band || inside < 0.0) { return content.eval(coord); }
    float2 g = float2(
        sdRoundRect(p + float2(1.0, 0.0), c, r) - sdRoundRect(p - float2(1.0, 0.0), c, r),
        sdRoundRect(p + float2(0.0, 1.0), c, r) - sdRoundRect(p - float2(0.0, 1.0), c, r));
    float2 n = g / max(length(g), 0.0001);
    float t = 1.0 - inside / band;
    float k = depth * t * t;
    float2 base = coord - n * k;
    half4 color = content.eval(base);
    color.r = content.eval(base - n * k * 0.12).r;
    color.b = content.eval(base + n * k * 0.12).b;
    return color;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun refractionEffect(width: Float, height: Float, radius: Float, blur: Float, depth: Float, band: Float): RenderEffect = try {
    lensEffect(width, height, radius, blur, depth, band)
} catch (_: RuntimeException) {
    // 个别 GPU 驱动不支持该着色器时退化为纯模糊，避免崩溃。
    BlurEffect(blur, blur, TileMode.Clamp)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(width: Float, height: Float, radius: Float, blur: Float, depth: Float, band: Float): RenderEffect {
    val shader = RuntimeShader(REFRACTION_SHADER).apply {
        setFloatUniform("size", width, height)
        setFloatUniform("radius", radius)
        setFloatUniform("depth", depth)
        setFloatUniform("band", band)
    }
    val lens = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    val frost = PlatformRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
    return PlatformRenderEffect.createChainEffect(lens, frost).asComposeRenderEffect()
}

/**
 * 液态玻璃表面。backdrop 为空时（卡片位于内容层内部）只绘制半透明磨砂面、顶部光泽与高光边。
 * cornerRadius 用于折射着色器计算边缘；视觉形状由 shape 决定。
 */
fun Modifier.liquidGlass(
    shape: Shape,
    backdrop: GlassBackdrop? = null,
    cornerRadius: Dp = 28.dp,
    blur: Dp = 20.dp,
    refraction: Dp = 12.dp,
    tint: Color? = null,
    strong: Boolean = false,
    elevation: Dp = 0.dp,
): Modifier = composed {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val sampling = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseFill = when {
        backdrop != null && !sampling -> glass.fillFallback
        backdrop != null -> if (glass.dark) glass.fill else Color.White.copy(alpha = 0.42f)
        strong -> glass.fillStrong
        else -> glass.fill
    }
    val shadowed = if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = glass.shadow, spotColor = glass.shadow) else Modifier
    this.then(shadowed)
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val rim = Brush.linearGradient(
                0f to glass.rimLight, 0.45f to glass.rimShade, 1f to glass.rimLight.copy(alpha = glass.rimLight.alpha * 0.55f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (glass.dark) 0.07f else 0.25f),
                0.55f to Color.Transparent,
                startY = 0f, endY = size.height,
            )
            val radiusPx = with(density) { cornerRadius.toPx() }
            val blurPx = with(density) { blur.toPx() }
            val depthPx = with(density) { refraction.toPx() }
            if (sampling && size.width > 0f && size.height > 0f) {
                blurLayer.renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && depthPx > 0f)
                    refractionEffect(size.width, size.height, radiusPx, blurPx, depthPx, minOf(radiusPx, size.minDimension / 2f) * 1.15f)
                else BlurEffect(blurPx, blurPx, TileMode.Clamp)
            }
            val stroke = Stroke(width = with(density) { 1.dp.toPx() })
            onDrawBehind {
                val source = backdrop?.layer
                backdrop?.version
                if (sampling && source != null) {
                    val dx = backdrop.origin.x - position.x
                    val dy = backdrop.origin.y - position.y
                    blurLayer.record { translate(dx, dy) { drawLayer(source) } }
                    clipPath(path) { drawLayer(blurLayer) }
                }
                drawPath(path, baseFill)
                if (tint != null) drawPath(path, tint)
                drawPath(path, sheen)
                drawPath(path, rim, style = stroke)
            }
        }
}

/** iOS 式按压：按下迅速收缩，松开带弹性回弹。 */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.965f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.5f, stiffness = if (pressed) 1400f else 400f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** 底色与柔和光斑，给玻璃提供可折射的层次；静态绘制，不持续刷新耗电。 */
@Composable fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val glass = LocalGlass.current
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(glass.canvas)
            val w = size.width; val h = size.height
            val spots = listOf(
                Triple(Offset(w * 0.05f, h * 0.05f), w * 0.90f, glass.blobs[0]),
                Triple(Offset(w * 1.0f, h * 0.24f), w * 0.80f, glass.blobs[1]),
                Triple(Offset(w * 0.0f, h * 0.68f), w * 0.85f, glass.blobs[2]),
                Triple(Offset(w * 0.95f, h * 0.96f), w * 0.75f, glass.blobs[3]),
            )
            spots.forEach { (center, radius, color) ->
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (glass.dark) 0.55f else 0.80f), Color.Transparent), center, radius), radius, center)
            }
        }
        content()
    }
}
