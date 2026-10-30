package cn.yzu.timetable.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** 轻量 Markdown 渲染：加粗、斜体、行内代码、标题、列表与换段，覆盖助手回复的常见语法。 */
@Composable fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, codeColor: Color = MaterialTheme.colorScheme.primary) {
    Text(toAnnotated(text, codeColor), modifier = modifier, style = style)
}

private val inlinePattern = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)""")

private fun toAnnotated(text: String, codeColor: Color): AnnotatedString = buildAnnotatedString {
    text.split('\n').forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        val line = raw.trimEnd()
        val heading = Regex("""^(#{1,4})\s*(.*)$""").find(line)
        val bullet = Regex("""\s*[-*+]\s+(.*)$""").find(line)
        when {
            heading != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(heading.groupValues[2], this@buildAnnotatedString, codeColor) }
            }
            bullet != null -> {
                append("•  ")
                appendInline(bullet.groupValues[1], this@buildAnnotatedString, codeColor)
            }
            else -> appendInline(line, this@buildAnnotatedString, codeColor)
        }
    }
}

private fun appendInline(line: String, scope: androidx.compose.ui.text.AnnotatedString.Builder, codeColor: Color) {
    var last = 0
    inlinePattern.findAll(line).forEach { m ->
        if (m.range.first > last) scope.append(line.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> scope.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { scope.append(token.removeSurrounding("**")) }
            token.startsWith("`") -> scope.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) { scope.append(token.removeSurrounding("`")) }
            else -> scope.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { scope.append(token.removeSurrounding("*")) }
        }
        last = m.range.last + 1
    }
    if (last < line.length) scope.append(line.substring(last))
}
