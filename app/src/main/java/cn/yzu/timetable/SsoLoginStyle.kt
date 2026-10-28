package cn.yzu.timetable

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import androidx.core.view.WindowCompat

/**
 * 统一认证等待页的纯视觉样式，与 App 内登录页保持一致（扬大绿品牌图标、柔和渐变底、圆角卡片与胶囊按钮）。
 * 只设置颜色、尺寸和背景，不涉及 WebView、Cookie、跳转与校验逻辑。
 */
internal class SsoLoginStyle(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val primary = if (dark) 0xFF5EA2FF.toInt() else 0xFF0A6CFF.toInt()
    private val textColor = if (dark) 0xFFECEEF3.toInt() else 0xFF15171C.toInt()
    private val muted = if (dark) 0xFFA9AFBD.toInt() else 0xFF5B6272.toInt()
    private val canvasTop = if (dark) 0xFF101826.toInt() else 0xFFE6EEFC.toInt()
    private val canvasBottom = if (dark) 0xFF0B0D12.toInt() else 0xFFF3EFFA.toInt()
    private val cardFill = if (dark) 0xCC23262E.toInt() else 0xD9FFFFFF.toInt()
    private val cardStroke = if (dark) 0x33FFFFFF else 0xFFFFFFFF.toInt()

    fun applyWindow(window: Window) {
        @Suppress("DEPRECATION")
        window.statusBarColor = canvasTop
        @Suppress("DEPRECATION")
        window.navigationBarColor = canvasBottom
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    fun pageBackground(): Drawable {
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(canvasTop, canvasBottom))
        fun blob(color: Int, radius: Int, cx: Float, cy: Float) = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dp(radius).toFloat()
            colors = intArrayOf(color, 0x00FFFFFF); setGradientCenter(cx, cy)
        }
        return LayerDrawable(arrayOf(base,
            blob(if (dark) 0x551E3A6B else 0xAAB9D4FF.toInt(), 320, 0.1f, 0.05f),
            blob(if (dark) 0x553A2B66 else 0x99D9CCFF.toInt(), 300, 1f, 0.3f),
            blob(if (dark) 0x44173F3A else 0x88C4EBDD.toInt(), 300, 0f, 0.85f)))
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    fun logo(size: Int): Drawable {
        val px = dp(size)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0B5E3F.toInt(), 0xFF063A2A.toInt())).apply { cornerRadius = px * 0.28f }
        val fg = context.getDrawable(R.drawable.ic_launcher_foreground)!!
        return LayerDrawable(arrayOf(bg, fg)).apply {
            val grow = -(px * 0.15f).toInt()
            setLayerInset(1, grow, grow, grow, grow)
            setBounds(0, 0, px, px)
        }
    }

    fun statusText(view: TextView) { view.textSize = 14f; view.setTextColor(muted); view.setLineSpacing(0f, 1.15f) }

    fun spinner(view: ProgressBar) { view.indeterminateTintList = ColorStateList.valueOf(primary) }

    private fun capsule(button: Button) {
        button.isAllCaps = false; button.stateListAnimator = null; button.textSize = 15f
        button.minHeight = dp(48); button.minimumHeight = dp(48)
        button.setPadding(dp(18), 0, dp(18), 0)
    }

    fun primaryButton(button: Button) {
        capsule(button); button.background = rounded(primary, 26); button.setTextColor(0xFFFFFFFF.toInt())
        button.typeface = Typeface.DEFAULT_BOLD
    }

    fun secondaryButton(button: Button) {
        capsule(button); button.background = rounded(cardFill, 24, cardStroke); button.setTextColor(primary); button.textSize = 14f
    }

    fun textButton(button: Button) {
        capsule(button); button.background = null; button.setTextColor(muted); button.textSize = 14f
    }

    fun topBar(start: View, end: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(16), dp(4))
        addView(start, LinearLayout.LayoutParams(-2, -2))
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(end, LinearLayout.LayoutParams(-2, dp(44)))
    }

    fun statusCard(status: TextView, progress: ProgressBar) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(cardFill, 24, cardStroke)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = fullWidthParams()
        addView(ImageView(context).apply { setImageDrawable(logo(48)) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            addView(TextView(context).apply { text = "扬州大学统一认证"; textSize = 17f; setTextColor(textColor); typeface = Typeface.DEFAULT_BOLD })
            addView(status)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(progress, LinearLayout.LayoutParams(dp(28), dp(28)))
    }

    fun fullWidthParams() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(6), dp(16), dp(6)) }

    fun browserParams() = LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(6), 0, 0) }

    fun cover(view: TextView) {
        view.background = pageBackground(); view.setTextColor(muted); view.textSize = 14f; view.setLineSpacing(0f, 1.3f)
        view.setCompoundDrawables(null, logo(96), null, null); view.compoundDrawablePadding = dp(24)
    }
}
