package ie.shoonya.vitt.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Softens where a scrolling list meets the top and bottom of the screen.
 *
 * Without it a row is simply guillotined: a date header cut in half by the
 * status bar, an account's monogram sliced through by the tab bar. It reads as
 * an unfinished screen rather than as content continuing past the edge, and it
 * is the single loudest thing separating this from an app that feels finished.
 *
 * A scrim in the ground colour rather than a real alpha mask. The alternative —
 * an offscreen compositing layer with `BlendMode.DstIn` — genuinely dissolves
 * the pixels, but it costs a full-screen buffer on every frame of every scroll,
 * and over an opaque background the two are indistinguishable. Paying for a
 * difference nobody can see is the wrong trade on a list that has to stay at
 * sixty frames while someone flicks through a year of entries.
 *
 * Kept short on purpose. A long fade turns into a vignette and starts hiding
 * the content it is meant to be introducing; this is just enough to take the
 * blade off the edge.
 */
fun Modifier.edgeFade(
    ground: Color,
    top: Dp = 20.dp,
    bottom: Dp = 28.dp,
): Modifier = drawWithContent {
    drawContent()

    val topPx = top.toPx()
    if (topPx > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ground, ground.copy(alpha = 0f)),
                startY = 0f,
                endY = topPx,
            ),
            size = Size(size.width, topPx),
        )
    }

    val bottomPx = bottom.toPx()
    if (bottomPx > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ground.copy(alpha = 0f), ground),
                startY = size.height - bottomPx,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - bottomPx),
            size = Size(size.width, bottomPx),
        )
    }
}
