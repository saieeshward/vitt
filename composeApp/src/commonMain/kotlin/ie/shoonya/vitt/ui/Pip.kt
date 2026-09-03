package ie.shoonya.vitt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.random.Random
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.ui.platform.prefersReducedMotion
import ie.shoonya.vitt.ui.theme.Vitt
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The companion's own strip: a fixed band above the tab bar that it wanders.
 *
 * Straight from the design's wander artboard, and the rules there are what keep
 * it charming rather than irritating:
 *
 * > A pet that only ever sits in a box is furniture. One that occasionally walks
 * > somewhere is alive.
 *
 * - **Her strip, her business.** A fixed 60dp band and nothing else: never over
 *   a number, never over a control, never obscuring a row being read. Because
 *   the strip is its own row between the content and the tab bar rather than an
 *   item inside the scrolling list, wandering cannot remeasure anything — the
 *   layout hazard `docs/pip-motion.md` warned about is gone by construction
 *   rather than by care.
 * - **Never blocks a tap.** There is nothing interactive behind the strip, and
 *   the strip itself takes no input.
 * - **Any touch stops her mid-step.** She freezes where she is and looks up.
 * - **Rare enough to notice.** Fourteen seconds for a round trip, at most one
 *   trip per idle minute, and only while the app is in front.
 * - **Still on first paint.** The screen has to answer "can I spend?" before
 *   anything moves.
 * - **Reduce Motion holds a still idle pose** rather than a frozen mid-step one.
 *
 * Note the wander is *idle* behaviour: it is triggered by nothing happening,
 * which is why it sits inside `PLAN.md` §5 without argument. It is not a
 * reaction to anything the user did.
 */
@Composable
fun CompanionStrip(
    daysRecorded: Int,
    currencyCount: Int,
    animal: CompanionAnimal = CompanionAnimal.DEFAULT,
    mood: Mood? = null,
    /**
     * Bumped on every touch anywhere in the app.
     *
     * Keying the wander effect on this is the whole stop-on-touch mechanism: a
     * new value cancels the running walk, so she halts exactly where she stood
     * rather than finishing the step or snapping home.
     */
    interactionTick: Int = 0,
    pixelSize: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    val still = prefersReducedMotion()
    val step = with(LocalDensity.current) { pixelSize.toPx() }

    BoxWithConstraints(
        // Inset, so the ends of her walk are inside the screen rather than
        // flush against it. Standing half-off the edge reads as clipping.
        modifier = modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .padding(horizontal = 14.dp),
        // Standing on the floor of her band rather than floating in it.
        contentAlignment = androidx.compose.ui.Alignment.BottomStart,
    ) {
        val stripPx = with(LocalDensity.current) { maxWidth.toPx() }
        val cell = floor(step).coerceAtLeast(1f)
        val far = (stripPx - CompanionSprites.WIDTH * cell).coerceAtLeast(0f)

        val pos = remember { Animatable(0f) }
        var pose by remember { mutableStateOf(CompanionPose.STAND) }
        var facingLeft by remember { mutableStateOf(true) }

        LaunchedEffect(still, interactionTick) {
            if (still) {
                pose = CompanionPose.STAND
                return@LaunchedEffect
            }
            // Still on first paint, and still again for a beat after any touch.
            delay(SETTLE_BEFORE_WANDER)
            while (true) {
                // A bout is a handful of moves rather than one there-and-back.
                // The design's budget is "one trip per idle minute at most", and
                // this spends that minute's worth of movement as two to four
                // short errands instead of a single lap: the same amount of
                // motion, far less of a track. A fixed full-width lap is what
                // made her read as a sprite on rails.
                repeat(2 + Random.nextInt(3)) {
                    val here = pos.value
                    // Somewhere new, and far enough to be worth walking to. A
                    // destination two pixels away looks like a twitch.
                    var target = Random.nextFloat()
                    if (kotlin.math.abs(target - here) < 0.22f) {
                        target = if (here < 0.5f) here + 0.3f else here - 0.3f
                    }
                    target = target.coerceIn(0f, 1f)

                    facingLeft = target < here
                    pose = CompanionPose.WALK
                    // Constant speed, so distance decides duration. A fixed
                    // duration regardless of distance makes a short step look
                    // like slow motion and a long one like a scurry.
                    val travel = kotlin.math.abs(target - here)
                    pos.animateTo(
                        target,
                        tween((travel * FULL_WIDTH_MILLIS).toInt().coerceAtLeast(400), easing = LinearEasing),
                    )

                    // At the end of a move she either turns to face you or just
                    // stands there. Both, unpredictably, is what stops the stop
                    // itself becoming a pattern.
                    pose = if (Random.nextFloat() < 0.45f) {
                        CompanionPose.FRONT
                    } else {
                        CompanionPose.STAND
                    }
                    delay(900L + Random.nextLong(2_600))
                }
                pose = CompanionPose.STAND
                // The long quiet. This is the part that keeps her an event
                // rather than wallpaper.
                delay(REST_BETWEEN_TRIPS + Random.nextLong(12_000))
            }
        }

        // Snapped to the pixel grid, which the design says in as many words. A
        // fractional translation resamples the whole layer: the sprite grew
        // vertical seams down the body on exactly the frames she was mid-step,
        // which reads as a rendering fault rather than as motion.
        val snapped = kotlin.math.round(pos.value * far / cell) * cell

        Box(
            // Horizontal only. The vertical bob belongs to the rig, where the
            // body and the legs disagree about it on purpose; lifting the whole
            // sprite as well made her bounce twice per step.
            modifier = Modifier.graphicsLayer { translationX = snapped },
        ) {
            CompanionPet(
                daysRecorded = daysRecorded,
                currencyCount = currencyCount,
                animal = animal,
                mood = mood,
                pixelSize = pixelSize,
                facingLeft = facingLeft,
                walking = pose == CompanionPose.WALK,
                pose = pose,
                still = still,
            )
        }
    }
}

/** The design's own band height: 60px above the tab bar, and nothing else. */
val STRIP_HEIGHT = 60.dp

/**
 * How long crossing the whole strip takes, which sets her walking speed.
 *
 * Derived from 14a's "22-second patrol": a lap is two traverses plus the pauses
 * at each end, so one traverse is about seven seconds. Expressed as a speed
 * rather than a duration because she no longer always walks the full width.
 */
private const val FULL_WIDTH_MILLIS = 7_000f
private const val REST_BETWEEN_TRIPS = 34_000L
private const val SETTLE_BEFORE_WANDER = 14_000L

/**
 * The animal, drawn from its pixel grid with every part on its own clock.
 *
 * Transcribed from the design's rig, and the timings are its numbers rather
 * than invented ones. The walk runs on a single 0.5s cycle with five different
 * curves on it; the idle runs five clocks at once:
 *
 * | Part | Idle | Walk (0.5s cycle) |
 * |---|---|---|
 * | body | breath 1.6s, weight shift 9s | up a pixel on the off-beats |
 * | head | the same breath, delayed 0.2s | up a pixel on the quarter-beats |
 * | ear  | twitch 5.3s | out a pixel mid-cycle |
 * | eye  | blink 4.1s | blink 4.7s |
 * | leg  | still | round the four-step cycle |
 *
 * The head's 0.2s lag behind the body is the detail that makes it read as one
 * animal breathing rather than two shapes pulsing, and it is not a thing anyone
 * guesses — it came out of the design file.
 *
 * **Every motion is a whole pixel, and stepped.** The rig is authored
 * `steps(1, end)` throughout, so nothing interpolates: a pixel grid moved by a
 * fraction shimmers along every edge, and a rotation of a four-pixel ear at this
 * scale is less than one pixel, so the design's 7° ear and 9° tail rotations are
 * expressed here as the one-pixel translations they actually resolve to.
 */
@Composable
internal fun CompanionPet(
    daysRecorded: Int,
    currencyCount: Int,
    animal: CompanionAnimal,
    mood: Mood?,
    pixelSize: Dp,
    facingLeft: Boolean = false,
    walking: Boolean = false,
    pose: CompanionPose = CompanionPose.STAND,
    still: Boolean = false,
) {
    val colors = Vitt.colors
    val warmth = daysRecorded.coerceIn(0, 30) / 30f
    val transition = rememberInfiniteTransition(label = "pet")

    // The design's clocks, each independent. Coprime enough that the
    // combination does not visibly resolve.
    val breath by transition.phase(if (still) 0 else 1_600, "breath")
    val headBreath by transition.phase(if (still) 0 else 1_600, "headBreath", delayMillis = 200)
    val blink by transition.phase(if (still) 0 else if (walking) 4_700 else 4_100, "blink")
    val ear by transition.phase(if (still) 0 else 5_300, "ear")
    val shift by transition.phase(if (still) 0 else 9_000, "weightShift")
    val stride by transition.phase(if (!walking || still) 0 else 500, "stride")

    val step = with(LocalDensity.current) { pixelSize.toPx() }

    Canvas(
        modifier = Modifier
            .width(pixelSize * CompanionSprites.WIDTH)
            .height(pixelSize * CompanionSprites.HEIGHT),
    ) {
        drawPet(
            animal = animal,
            pose = pose,
            pixel = step,
            facingLeft = facingLeft,
            offsets = if (walking) walkOffsets(stride) else idleOffsets(breath, headBreath, ear, shift),
            eyesShut = !still && blink > 0.95f,
            currencyCount = currencyCount,
            currencyColour = { colors.currency(it) },
            // Pale toward the ground at a cold streak, full colour at a warm
            // one. Never below the animal's own hue, only nearer the paper —
            // and capped well short of the 0.45 an earlier pass used, which
            // washed a warm pink into something barely on the page.
            quiet = (1f - warmth) * 0.26f,
            ground = colors.ground,
            rest = (mood ?: Mood.NEUTRAL).rest,
        )
    }
}

/**
 * Where each rig part sits this frame, in whole sprite pixels.
 *
 * Offsets are **local**: [drawPet] adds each part's ancestors, so `head` here is
 * the head's motion relative to the body, not its position on screen.
 */
private data class RigOffsets(
    val bodyX: Int = 0,
    val bodyY: Int = 0,
    val headX: Int = 0,
    val headY: Int = 0,
    val earX: Int = 0,
    val legX: Int = 0,
    val legY: Int = 0,
)

/** The 0.5s walk cycle: `wlegA`, `wbody`, `whead` and `wear` from the design. */
private fun walkOffsets(p: Float): RigOffsets {
    val quarter = (p * 4f).toInt().coerceIn(0, 3)
    return RigOffsets(
        // wbody: up on the off-beats.
        bodyY = if (quarter == 1 || quarter == 3) -1 else 0,
        // whead: up on the quarter-beats, which puts it out of phase with the
        // body — the head bobs against the shoulders rather than with them.
        headY = if (quarter == 1 || quarter == 3) -1 else 0,
        earX = if (quarter == 1 || quarter == 2) 1 else 0,
        // wlegA: (-1,0) → (0,-1) → (1,0) → (0,0).
        legX = when (quarter) { 0 -> -1; 2 -> 1; else -> 0 },
        legY = if (quarter == 1) -1 else 0,
    )
}

/** The standing rig: `idlebreath`, `weightshift` and `eartwitchpx`. */
private fun idleOffsets(breath: Float, headBreath: Float, ear: Float, shift: Float) = RigOffsets(
    // weightshift: she leans onto one side for roughly half of a 9s cycle,
    // which is most of what stops a standing animal reading as a paused GIF.
    bodyX = if (shift > 0.52f && shift < 0.94f) 1 else 0,
    bodyY = if (breath > 0.5f) -1 else 0,
    // The same breath, a fifth of a second later. Two shapes pulsing together
    // read as a pulse; offset, they read as one animal breathing.
    headY = if (headBreath > 0.5f) -1 else 0,
    earX = if (ear > 0.80f && ear < 0.90f) 1 else 0,
)

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.phase(
    millis: Int,
    label: String,
    delayMillis: Int = 0,
) = if (millis <= 0) {
    remember { mutableStateOf(0f) }
} else {
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(millis, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = label,
    )
}

/** The dark plum the design uses for features, rather than black. */
private val Feature = Color(0xFF46283B)

private fun DrawScope.drawPet(
    animal: CompanionAnimal,
    pose: CompanionPose,
    pixel: Float,
    facingLeft: Boolean,
    offsets: RigOffsets,
    eyesShut: Boolean,
    currencyCount: Int,
    currencyColour: (Int) -> Color,
    quiet: Float,
    ground: Color,
    rest: Int,
) {
    val scale = floor(pixel).coerceAtLeast(1f)
    val spriteH = CompanionSprites.HEIGHT * scale
    val originY = floor(size.height - spriteH) + rest * scale

    CompanionSprites.sprite(animal, pose).forEach { px ->
        // A currency marker is only drawn when the user actually has that
        // currency. The design authors two or three of them per animal, so an
        // unowned one has to disappear rather than sit there in a colour that
        // means nothing.
        if (px.currency >= 0 && px.currency >= currencyCount) return@forEach
        // Eyes shut is a substitution, not a squash: a grid cannot scale an eye
        // to 30% without inventing pixels, so the eye rects are simply skipped
        // and the lid is the head colour already behind them.
        if (eyesShut && px.part == 'y') return@forEach

        val colour = when {
            px.currency >= 0 -> currencyColour(px.currency)
            // Features never fade. The streak ramp is meant to make her paler
            // when the streak is cold, which the design does want ("Day 0 —
            // pale, asleep"), but running it over the eyes as well turned a
            // near-black pupil into a grey smudge and cost her a face. Fur
             // fades; eyes and mouth do not.
            px.part == 'y' || px.part == 'm' -> Color(px.argb)
            else -> Color(px.argb).mix(ground, quiet)
        }

        val (dx, dy) = offsets.forPart(px.part)
        // Mirrored about the sprite box, width included, or a rect flips to the
        // wrong side of its own left edge.
        val gx = if (facingLeft) px.x + dx else CompanionSprites.WIDTH - px.x - px.w - dx
        drawRect(
            color = colour,
            topLeft = Offset(gx * scale, originY + (px.y + dy) * scale),
            size = Size(px.w * scale, px.h * scale),
        )
    }
}

/**
 * A part's offset, with the inheritance that actually holds up on screen.
 *
 * **Horizontally, everything inherits the body.** The idle weight shift has to
 * move the whole animal or she tears in half when she leans.
 *
 * **Vertically, `head` and `leg` are siblings of `body`, not children.** This is
 * the correction to a bug that was plainly visible in the simulator and not at
 * all visible in the code: the walk's body and head curves are the same shape,
 * so nesting them added up to a two-pixel head lift against a one-pixel body,
 * and her head came off. Sibling vertical offsets also give the idle rig what it
 * needs, since there the head runs the same breath 0.2s later — one pixel of
 * compression at the neck, which is what breathing looks like on a grid.
 *
 * `mouth`, `ear` and `eye` really are children of `head`, and that part matters:
 * the head lifts on the quarter-beats while the mouth has no walk curve of its
 * own, so without inheritance the snout would stay behind every other frame.
 */
private fun RigOffsets.forPart(part: Char): Pair<Int, Int> = when (part) {
    'b' -> bodyX to bodyY
    // Legs ride the body vertically, and add their own step on top.
    //
    // They used to be planted, on the reasoning that legs carrying the body's
    // lift is a hop rather than a step. That was wrong on screen: the legs sit
    // strictly *below* the body with no overlapping rows, so every time the
    // body breathed upward it tore a one-pixel gap at the waist and she came
    // apart. The head can afford an independent lift because head and body
    // rects overlap heavily — a relative shift there just moves the overlap.
    // The waist has no such slack.
    'l' -> bodyX + legX to bodyY + legY
    'h' -> bodyX + headX to headY
    'y', 'm' -> bodyX + headX to headY
    'e' -> bodyX + headX + earX to headY
    else -> 0 to 0
}

/** Blends toward [other] by [amount], for shading derived from one hue. */
private fun Color.mix(other: Color, amount: Float) = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha,
)

/** Toward white, for a highlight that keeps the hue. */
private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

/**
 * The resting mood, from how many budgets are on track.
 *
 * Three states and no sad one. §5.2 suppresses a score below 40% coverage rather
 * than showing a low one, and this inherits that: the worst available state is
 * [CALM], which is quieter rather than unhappy.
 */
enum class Mood(val rest: Int) {
    /** Most budgets on track. */
    BRIGHT(rest = -1),

    /** Nothing to say yet, or a mixed month. The default. */
    NEUTRAL(rest = 0),

    /** Little on track. Still and quiet, never sad. */
    CALM(rest = 0),
    ;

    companion object {
        /**
         * Mood from the count of healthy envelopes.
         *
         * Null below two budgets, because a mood derived from one envelope is a
         * mood derived from one number, and §5.2's coverage gate exists to stop
         * the app making a claim it cannot support.
         */
        fun of(onTrack: Int, total: Int): Mood? {
            if (total < 2) return null
            val share = onTrack.toFloat() / total.toFloat()
            return when {
                share >= 0.66f -> BRIGHT
                share >= 0.34f -> NEUTRAL
                else -> CALM
            }
        }
    }
}
