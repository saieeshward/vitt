package ie.shoonya.vitt.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ie.shoonya.vitt.ui.platform.prefersReducedMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.ui.theme.Vitt

/**
 * Pip — a piggy bank seen face-on, with one coin slot per currency.
 *
 * Face-on, not in profile: the design's character is a round front-facing blob
 * with two ears, two eyes, blush and a centred snout. A side view reads as a
 * farm animal rather than a companion, which is what an earlier attempt here got
 * wrong.
 *
 * The slots along the top carry the product's one hard rule without a word of
 * explanation: coins go in through their own slot and never pour between them.
 * Add a currency and Pip gets another slot, tinted that currency's colour.
 *
 * Drawn rather than shipped as artwork — a vector Pip costs no asset pipeline
 * and recolours per currency for free.
 *
 * **Pip reacts only to whether you recorded, never to how much you spent.**
 * Reacting to spending would make the character an instrument of judgement.
 */
@Composable
fun Pip(
    /**
     * Days recorded in the window. Deepens Pip's colour; nothing else.
     *
     * Pass zero when the habit layer is off. Pip stays on the screen either way,
     * because the coin slots are how the app explains that currencies never
     * convert — but with the layer off nothing about Pip may respond to what the
     * user did.
     */
    daysRecorded: Int,
    /** One slot per currency, in assignment order. */
    currencyCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Vitt.colors
    val warmth = daysRecorded.coerceIn(0, 30) / 30f
    // Motion is the whole of what this setting governs here. Pip's colour still
    // deepens with the streak, because that is state rather than movement.
    val still = prefersReducedMotion()

    // A slow blink is the only motion. It reads as alive without reacting to
    // anything the user did, which is the line this character must not cross.
    val transition = rememberInfiniteTransition(label = "pip")
    val eyeScale by if (still) {
        // Eyes open, not shut: a blink frozen mid-cycle would look like a
        // character asleep, which §5 forbids reading as neglect.
        remember { mutableStateOf(1f) }
    } else transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5_500
                1f at 0
                1f at 5_060
                0.1f at 5_280
                1f at 5_500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    // A slow bob. The design animates Pip at 3.2s, and without it the home
    // screen reads as a still illustration rather than a companion.
    val bob by if (still) {
        remember { mutableStateOf(0f) }
    } else transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    Canvas(modifier = modifier.offset(y = (-5).dp * bob)) {
        // Pale and asleep at zero, deepening with the streak — the same hue
        // throughout, only more awake.
        val body = colors.pip
        val fill = Color(
            red = body.red + (1f - body.red) * (1f - warmth) * 0.5f,
            green = body.green + (1f - body.green) * (1f - warmth) * 0.5f,
            blue = body.blue + (1f - body.blue) * (1f - warmth) * 0.5f,
            alpha = 1f,
        )
        val slots = if (currencyCount <= 0) {
            listOf(colors.inkFaint.copy(alpha = 0.4f))
        } else {
            (0 until currencyCount).map { colors.currency(it) }
        }
        drawPip(fill, slots, eyeScale)
    }
}

/** The dark plum the design uses for features, rather than black. */
private val Feature = Color(0xFF46283B)

private fun DrawScope.drawPip(fill: Color, slotColors: List<Color>, eyeScale: Float) {
    // The body is slightly wider than tall, sized to leave margin on all sides.
    val bodyW = minOf(size.width * 0.52f, size.height * 0.92f)
    val bodyH = bodyW * 0.88f
    val cx = size.width / 2f
    val cy = size.height * 0.52f
    val left = cx - bodyW / 2f
    val top = cy - bodyH / 2f

    val snoutFill = fill.mix(Feature, 0.10f)
    val blush = fill.mix(Feature, 0.16f)

    // Ears: rounded, splayed outward. Drawn before the body so their bases tuck
    // underneath rather than sitting on top as flaps.
    val earW = bodyW * 0.25f
    val earH = bodyH * 0.30f
    listOf(-1f, 1f).forEach { side ->
        val earCx = cx + side * bodyW * 0.31f
        val earCy = top + earH * 0.16f
        rotate(degrees = side * 20f, pivot = Offset(earCx, earCy)) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(earCx - earW / 2f, earCy - earH / 2f),
                size = Size(earW, earH),
                cornerRadius = CornerRadius(earW * 0.5f, earH * 0.42f),
            )
        }
    }

    // Legs, peeking below the body.
    val legW = bodyW * 0.19f
    val legH = bodyH * 0.14f
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = fill.mix(Feature, 0.06f),
            topLeft = Offset(cx + side * bodyW * 0.24f - legW / 2f, top + bodyH * 0.92f),
            size = Size(legW, legH),
            cornerRadius = CornerRadius(legW * 0.4f, legH * 0.8f),
        )
    }

    // Tail: a broken ring to the side, matching the design's quarter-arc.
    val tailR = bodyH * 0.10f
    drawCircle(
        color = fill,
        radius = tailR,
        center = Offset(left + bodyW + tailR * 0.5f, top + bodyH * 0.36f),
        style = Stroke(width = bodyH * 0.035f),
    )

    // Body.
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.46f, bodyH * 0.50f),
    )

    // Cheeks, widening the face at eye level.
    val cheekW = bodyW * 0.28f
    val cheekH = bodyH * 0.32f
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = fill,
            topLeft = Offset(cx + side * bodyW * 0.44f - cheekW / 2f, top + bodyH * 0.40f),
            size = Size(cheekW, cheekH),
        )
    }

    // Eyes, blinking on the same slow cycle.
    val eyeW = bodyW * 0.11f
    val eyeH = bodyH * 0.15f * eyeScale.coerceAtLeast(0.06f)
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = Feature,
            topLeft = Offset(
                cx + side * bodyW * 0.22f - eyeW / 2f,
                top + bodyH * 0.34f - eyeH / 2f,
            ),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2f, eyeH / 2f),
        )
    }

    // Blush.
    val blushW = bodyW * 0.16f
    val blushH = bodyH * 0.09f
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = blush,
            topLeft = Offset(cx + side * bodyW * 0.36f - blushW / 2f, top + bodyH * 0.48f),
            size = Size(blushW, blushH),
        )
    }

    // Snout, centred, with two nostrils.
    val snoutW = bodyW * 0.37f
    val snoutH = bodyH * 0.27f
    val snoutX = cx - snoutW / 2f
    val snoutY = top + bodyH * 0.52f
    drawOval(
        color = snoutFill,
        topLeft = Offset(snoutX, snoutY),
        size = Size(snoutW, snoutH),
    )
    val nostrilW = snoutW * 0.14f
    val nostrilH = snoutH * 0.36f
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = Feature.copy(alpha = 0.5f),
            topLeft = Offset(
                cx + side * snoutW * 0.20f - nostrilW / 2f,
                snoutY + snoutH * 0.5f - nostrilH / 2f,
            ),
            size = Size(nostrilW, nostrilH),
        )
    }

    // The coin slots: a small row along the top, one per currency, each ringed
    // so it reads as an opening rather than a painted dot.
    if (slotColors.isEmpty()) return
    val slotW = bodyW * 0.055f
    val slotH = slotW * 1.7f
    val gap = slotW * 0.85f
    val totalW = slotColors.size * slotW + (slotColors.size - 1) * gap
    var x = cx - totalW / 2f
    val slotY = top + bodyH * 0.05f

    slotColors.forEach { colour ->
        drawRoundRect(
            color = Feature.copy(alpha = 0.16f),
            topLeft = Offset(x - slotW * 0.22f, slotY - slotW * 0.22f),
            size = Size(slotW * 1.44f, slotH + slotW * 0.44f),
            cornerRadius = CornerRadius(slotW, slotW),
        )
        drawRoundRect(
            color = colour,
            topLeft = Offset(x, slotY),
            size = Size(slotW, slotH),
            cornerRadius = CornerRadius(slotW / 2f, slotW / 2f),
        )
        x += slotW + gap
    }
}

/** Blends toward [other] by [amount], for shading derived from one hue. */
private fun Color.mix(other: Color, amount: Float) = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha,
)
