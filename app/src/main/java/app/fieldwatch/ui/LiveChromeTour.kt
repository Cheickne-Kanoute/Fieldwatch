package app.fieldwatch.ui

import app.fieldwatch.domain.LanguageText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.min
import kotlin.math.roundToInt

data class LiveTourTargets(
    val tune: Rect? = null,
)

@Composable
fun LiveChromeTour(
    tune: Rect?,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
) {
    if (tune == null) return
    val density = LocalDensity.current
    val ring = PhosphorActive.nightIf(LocalNightMode.current)
    val hole = with(density) { tune.inflate(4.dp.toPx()) }
    val radius = min(hole.width, hole.height) / 2f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val edge = with(density) { 16.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val cardW = with(density) { 300.dp.toPx() }.coerceAtMost(screenW - edge * 2f)
        val cardLeft = (hole.right - cardW).coerceIn(edge, (screenW - cardW - edge).coerceAtLeast(edge))
        val cardTop = hole.bottom + gap

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = hole.topLeft,
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Clear,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = ring,
                topLeft = hole.topLeft,
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(hole.left.roundToInt(), hole.top.roundToInt()) }
                .size(with(density) { hole.width.toDp() }, with(density) { hole.height.toDp() })
                .semantics { contentDescription = LanguageText.text("ui.open.display") }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onOpen,
                ),
        )
        Surface(
            modifier = Modifier
                .offset { IntOffset(cardLeft.roundToInt(), cardTop.roundToInt()) }
                .width(with(density) { cardW.toDp() }),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, ring),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    LanguageText.text("ui.how.live.looks"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ring,
                )
                Text(
                    LanguageText.text("ui.radar.list.timeline.hybrid"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    LanguageText.text("ui.pause.filters.signatures.reports"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldwatchActionButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(LanguageText.text("ui.open.label")) }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text(LanguageText.text("ui.skip.label")) }
                }
            }
        }
    }
}
