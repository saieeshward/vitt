package ie.shoonya.vitt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Pip — a piggy bank with one coin slot per currency.
 *
 * The slots carry the product's one hard rule without a word of explanation:
 * coins go in through their own slot and never pour between them. Add a currency
 * and Pip gets another slot, tinted that currency's colour.
 *
 * Drawn rather than shipped as artwork. A vector Pip costs no asset pipeline, no
 * download size, and recolours per currency for free — and a solo developer
 * cannot maintain an illustration set that grows with the feature list.
 *
 * **Pip reacts only to whether you recorded, never to how much you spent.**
 * Reacting to spending would make the character an instrument of judgement,
 * which is the failure mode the tone rules exist to prevent.
 */
@Composable
fun Pip(
    /** How many days were recorded in the window — deepens Pip's colour. */
    daysRecorded: Int,
    /** One slot per currency, in assignment order. */
    currencyCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Vitt.colors
    val warmth = (daysRecorded.coerceIn(0, 30)) / 30f

    Canvas(modifier = modifier) {
        val body = colors.pip
        // Pale and asleep at zero, deepening with the streak. Never a different
        // hue — the same character, more awake.
        val fill = Color(
            red = body.red + (1f - body.red) * (1f - warmth) * 0.55f,
            green = body.green + (1f - body.green) * (1f - warmth) * 0.55f,
            blue = body.blue + (1f - body.blue) * (1f - warmth) * 0.55f,
            alpha = 1f,
        )
        drawPig(fill = fill, slotColors = (0 until currencyCount).map { colors.currency(it) })
    }
}

private fun DrawScope.drawPig(fill: Color, slotColors: List<Color>) {
    val w = size.width
    val h = size.height
    val bodyW = w * 0.72f
    val bodyH = h * 0.52f
    val left = (w - bodyW) / 2f
    val top = h * 0.30f

    // Legs first, so the body sits over them.
    val legW = bodyW * 0.12f
    val legH = h * 0.14f
    listOf(0.18f, 0.40f, 0.60f, 0.82f).forEach { fraction ->
        drawRoundRectCompat(
            color = fill,
            topLeft = Offset(left + bodyW * fraction - legW / 2f, top + bodyH * 0.82f),
            size = Size(legW, legH),
            radius = legW / 2f,
        )
    }

    // Body.
    drawRoundRectCompat(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(bodyW, bodyH),
        radius = bodyH / 2f,
    )

    // Snout.
    val snoutR = bodyH * 0.30f
    drawCircle(color = fill, radius = snoutR, center = Offset(left + bodyW - snoutR * 0.35f, top + bodyH * 0.58f))
    val nostril = snoutR * 0.16f
    listOf(-0.35f, 0.35f).forEach { dy ->
        drawCircle(
            color = fill.darken(0.22f),
            radius = nostril,
            center = Offset(left + bodyW - snoutR * 0.15f, top + bodyH * 0.58f + snoutR * dy),
        )
    }

    // Ear.
    val ear = Path().apply {
        moveTo(left + bodyW * 0.62f, top + bodyH * 0.06f)
        lineTo(left + bodyW * 0.78f, top - bodyH * 0.18f)
        lineTo(left + bodyW * 0.82f, top + bodyH * 0.14f)
        close()
    }
    drawPath(ear, fill)

    // Eye.
    drawCircle(
        color = fill.darken(0.55f),
        radius = bodyH * 0.055f,
        center = Offset(left + bodyW * 0.74f, top + bodyH * 0.32f),
    )

    // Tail.
    drawCircleOutline(
        color = fill,
        center = Offset(left - bodyH * 0.06f, top + bodyH * 0.42f),
        radius = bodyH * 0.11f,
        stroke = bodyH * 0.055f,
    )

    // The coin slots: one per currency, along the back, each in its own colour.
    // Evenly spaced regardless of count, so a fourth currency does not crowd.
    if (slotColors.isEmpty()) return
    val slotW = bodyW * 0.045f
    val slotH = bodyH * 0.13f
    val span = bodyW * 0.42f
    val startX = left + bodyW * 0.20f
    val step = if (slotColors.size == 1) 0f else span / (slotColors.size - 1)

    slotColors.forEachIndexed { i, colour ->
        val x = if (slotColors.size == 1) startX + span / 2f else startX + step * i
        drawRoundRectCompat(
            color = colour,
            topLeft = Offset(x - slotW / 2f, top + bodyH * 0.02f),
            size = Size(slotW, slotH),
            radius = slotW / 2f,
        )
    }
}

private fun DrawScope.drawRoundRectCompat(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
) = drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = size,
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
)

private fun DrawScope.drawCircleOutline(color: Color, center: Offset, radius: Float, stroke: Float) =
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
    )

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
