package ie.shoonya.vitt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The tab glyphs, drawn rather than imported.
 *
 * The design uses Phosphor's line icons. Shipping an icon font would add a
 * download, a licence, and a dependency for five glyphs — so these are drawn to
 * match its weight instead: a uniform 1.6dp stroke, rounded caps, on a 24dp box.
 *
 * A tab bar of bare words reads unfinished no matter how the type is set, which
 * is most of what made the previous version look rough.
 */
enum class VittIcon { Wallet, List, People, Spark }

@Composable
fun VittGlyph(icon: VittIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val w = s * 0.067f          // ≈1.6dp on a 24dp box
        val stroke = Stroke(width = w, cap = StrokeCap.Round)
        when (icon) {
            VittIcon.Wallet -> drawWallet(tint, s, stroke)
            VittIcon.List -> drawList(tint, s, w)
            VittIcon.People -> drawPeople(tint, s, stroke)
            VittIcon.Spark -> drawSpark(tint, s, stroke)
        }
    }
}

private fun DrawScope.drawWallet(tint: Color, s: Float, stroke: Stroke) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.12f, s * 0.26f),
        size = Size(s * 0.76f, s * 0.50f),
        cornerRadius = CornerRadius(s * 0.10f, s * 0.10f),
        style = stroke,
    )
    // The clasp, which is what makes it read as a wallet rather than a card.
    drawCircle(color = tint, radius = s * 0.045f, center = Offset(s * 0.70f, s * 0.51f))
}

private fun DrawScope.drawList(tint: Color, s: Float, w: Float) {
    listOf(0.32f, 0.50f, 0.68f).forEachIndexed { i, y ->
        drawCircle(color = tint, radius = w * 0.62f, center = Offset(s * 0.20f, s * y))
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.34f, s * y - w / 2f),
            // Rows of unequal length read as a list rather than a grid.
            size = Size(s * (if (i == 2) 0.34f else 0.48f), w),
            cornerRadius = CornerRadius(w, w),
        )
    }
}

private fun DrawScope.drawPeople(tint: Color, s: Float, stroke: Stroke) {
    drawCircle(color = tint, radius = s * 0.13f, center = Offset(s * 0.40f, s * 0.34f), style = stroke)
    drawArc(
        color = tint,
        startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(s * 0.16f, s * 0.52f),
        size = Size(s * 0.48f, s * 0.40f),
        style = stroke,
    )
    // A second figure, cropped, so it reads as people rather than one person.
    drawArc(
        color = tint.copy(alpha = 0.55f),
        startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(s * 0.46f, s * 0.52f),
        size = Size(s * 0.46f, s * 0.40f),
        style = stroke,
    )
    drawCircle(
        color = tint.copy(alpha = 0.55f),
        radius = s * 0.10f, center = Offset(s * 0.70f, s * 0.36f), style = stroke,
    )
}

private fun DrawScope.drawSpark(tint: Color, s: Float, stroke: Stroke) {
    // A four-point spark: habit, not a trophy. A trophy would be a reward for
    // thrift, which is the thing this app deliberately does not do.
    val c = Offset(s * 0.5f, s * 0.5f)
    val long = s * 0.30f
    val short = s * 0.16f
    listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f).forEach { (dx, dy) ->
        drawLine(
            color = tint,
            start = c,
            end = Offset(c.x + dx * long, c.y + dy * long),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
    listOf(0.7f to -0.7f, 0.7f to 0.7f, -0.7f to 0.7f, -0.7f to -0.7f).forEach { (dx, dy) ->
        drawLine(
            color = tint.copy(alpha = 0.55f),
            start = c,
            end = Offset(c.x + dx * short, c.y + dy * short),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}
