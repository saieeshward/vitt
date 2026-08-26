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
        // With nothing recorded there is no currency to colour, so the single
        // slot is a faint placeholder rather than a currency's hue — it must not
        // imply a ledger that does not exist.
        val slots = if (currencyCount <= 0) {
            listOf(colors.inkFaint.copy(alpha = 0.45f))
        } else {
            (0 until currencyCount).map { colors.currency(it) }
        }
        drawPig(fill = fill, slotColors = slots)
    }
}

private fun DrawScope.drawPig(fill: Color, slotColors: List<Color>) {
    val w = size.width
    val h = size.height

    // Sized to leave room above and below: the character sits in its space
    // rather than filling it, so it never collides with the text beneath.
    val bodyW = minOf(w * 0.50f, h * 1.30f)
    val bodyH = bodyW * 0.58f
    val cx = w / 2f
    val cy = h * 0.50f
    val left = cx - bodyW / 2f
    val top = cy - bodyH / 2f

    val shade = fill.darken(0.12f)

    // Legs, drawn first and tall enough to show beneath the body.
    val legW = bodyW * 0.11f
    val legH = bodyH * 0.34f
    // Two pairs, back and front, rather than four evenly spaced posts.
    listOf(0.22f, 0.36f, 0.62f, 0.76f).forEach { fraction ->
        drawRoundRectCompat(
            color = shade,
            topLeft = Offset(left + bodyW * fraction - legW / 2f, top + bodyH * 0.80f),
            size = Size(legW, legH),
            radius = legW * 0.4f,
        )
    }

    // Tail: a short thin curl. Larger and it reads as a handle rather than a tail.
    drawCircleOutline(
        color = shade,
        center = Offset(left - bodyH * 0.01f, cy - bodyH * 0.18f),
        radius = bodyH * 0.085f,
        stroke = bodyH * 0.038f,
    )

    // Body.
    drawRoundRectCompat(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(bodyW, bodyH),
        radius = bodyH * 0.48f,
    )

    // Snout: same fill as the body and overlapping it, so it reads as part of
    // the head. Lighter and detached, it looked like a separate disc.
    val snoutW = bodyW * 0.20f
    val snoutH = bodyH * 0.30f
    val snoutX = left + bodyW - snoutW * 0.86f
    val snoutY = cy - snoutH * 0.05f
    drawRoundRectCompat(
        color = fill,
        topLeft = Offset(snoutX, snoutY),
        size = Size(snoutW, snoutH),
        radius = snoutH * 0.46f,
    )
    val nostril = snoutH * 0.10f
    listOf(-0.20f, 0.20f).forEach { dy ->
        drawCircle(
            color = shade.darken(0.18f),
            radius = nostril,
            center = Offset(snoutX + snoutW * 0.58f, snoutY + snoutH * (0.5f + dy)),
        )
    }

    // Ear: a small rounded flap tucked onto the head rather than a spike.
    val earW = bodyW * 0.11f
    val earH = bodyH * 0.20f
    val earX = left + bodyW * 0.66f
    val ear = Path().apply {
        moveTo(earX, top + bodyH * 0.20f)
        quadraticBezierTo(earX - earW * 0.35f, top - earH * 0.30f, earX + earW * 0.75f, top + bodyH * 0.04f)
        quadraticBezierTo(earX + earW * 0.55f, top + bodyH * 0.20f, earX, top + bodyH * 0.20f)
        close()
    }
    drawPath(ear, shade)

    // Eye.
    drawCircle(
        color = fill.darken(0.62f),
        radius = bodyH * 0.042f,
        center = Offset(left + bodyW * 0.74f, cy - bodyH * 0.14f),
    )

    // The coin slots: one per currency, along the back, each in its own colour.
    // This is the point of the character — coins go in through their own slot
    // and never move between them.
    if (slotColors.isEmpty()) return
    val slotW = bodyW * 0.062f
    val slotH = bodyH * 0.22f
    val span = bodyW * 0.30f
    val startX = left + bodyW * 0.24f
    val step = if (slotColors.size == 1) 0f else span / (slotColors.size - 1)

    slotColors.forEachIndexed { i, colour ->
        val x = if (slotColors.size == 1) startX + span / 2f else startX + step * i
        drawRoundRectCompat(
            color = colour,
            topLeft = Offset(x - slotW / 2f, top + bodyH * 0.10f),
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

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
