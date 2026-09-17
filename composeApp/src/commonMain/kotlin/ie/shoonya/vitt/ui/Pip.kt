package ie.shoonya.vitt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ie.shoonya.vitt.model.Nudge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.ui.platform.prefersReducedMotion
import ie.shoonya.vitt.ui.theme.Vitt
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The companion, living wherever the user put her.
 *
 * An overlay rather than a row in the layout, which is what lets her be
 * anywhere and is also why she is layout-safe: nothing in a `Box` overlay can
 * remeasure the list underneath it, which was the original hazard
 * `docs/pip-motion.md` warned about. It also gives back the 60dp the fixed
 * strip used to reserve on the home screen.
 *
 * **She is placed, then she lives there.** The drop point is not a position but
 * a *home*: she patrols a band around it, so moving her moves her territory.
 * The band above the tab bar is now simply the default home rather than a
 * special case.
 *
 * This deliberately relaxes one design rule. The wander artboard's rule is
 * "never over your numbers or your buttons", which exists to stop *the app*
 * putting a pet in the user's way — and a pet the user dragged onto their
 * balance is not the app doing anything. What stays forbidden is the tab bar
 * and the Add button, because that is the daily path and nothing may block it.
 *
 * Horizontal patrol only. A pet walks on a surface, and letting her drift
 * vertically would carry her onto figures the user did not put her on.
 */
@Composable
fun CompanionLayer(
    daysRecorded: Int,
    currencyCount: Int,
    animal: CompanionAnimal = CompanionAnimal.DEFAULT,
    mood: Mood? = null,
    /**
     * Where she lives, as a fraction of the screen in each axis.
     *
     * Normalised rather than in pixels so it survives a different screen size
     * and means the same thing on a phone and a tablet.
     */
    home: Offset,
    /** Called once on drop, with the new normalised home. */
    onHomeChange: (Offset) -> Unit,
    /**
     * Bumped on every touch anywhere in the app.
     *
     * Keying the wander effect on this is the whole stop-on-touch mechanism: a
     * new value cancels the running walk, so she halts exactly where she stood
     * rather than finishing the step or snapping home.
     */
    interactionTick: Int = 0,
    /**
     * The thing she should go and stand by, or null to wander.
     *
     * She points, and that is all. See [ie.shoonya.vitt.model.Nudges] for why
     * this is the shape of the mechanic: position is an attention cue that
     * carries no verdict, and a face that did would break §5.6.
     */
    nudge: Nudge? = null,
    /** Where the nudge's target is on screen, in root coordinates. Null if it is not laid out. */
    nudgeTarget: Rect? = null,
    /** She was tapped while pointing at something. */
    onNudgeTap: (Nudge) -> Unit = {},
    /** She gave up waiting. The caller decides when to ask again. */
    onNudgeExpired: (Nudge) -> Unit = {},
    /** Height of the tab bar, which she may never be dropped onto. */
    forbiddenBottom: Dp = 92.dp,
    pixelSize: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    val still = prefersReducedMotion()
    val density = LocalDensity.current
    val step = with(density) { pixelSize.toPx() }
    val cell = floor(step).coerceAtLeast(1f)

    // Where this layer sits in the root, so a target's root bounds can be
    // turned into a place to walk to inside it.
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .onGloballyPositioned { layerOrigin = it.boundsInRoot().topLeft },
    ) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val petW = CompanionSprites.WIDTH * cell
        val petH = CompanionSprites.HEIGHT * cell
        val bottomPx = with(density) { forbiddenBottom.toPx() }
        val edge = with(density) { 8.dp.toPx() }

        // The placeable region: on screen, clear of the tab bar and the Add
        // button inside it, and not half off an edge.
        val minX = edge
        val maxX = (boxW - petW - edge).coerceAtLeast(minX)
        val minY = edge
        val maxY = (boxH - bottomPx - petH).coerceAtLeast(minY)

        // Where home sits in pixels. Home is where she rests between bouts,
        // not a cage: the whole screen is hers to cross, which is what makes
        // her a creature in the app rather than a decoration on the tab bar.
        val homeX = (minX + home.x * (maxX - minX)).coerceIn(minX, maxX)
        val homeY = (minY + home.y * (maxY - minY)).coerceIn(minY, maxY)

        // Her position, as an offset from home.
        val walkedX = remember { Animatable(0f) }
        val walkedY = remember { Animatable(0f) }
        var pose by remember { mutableStateOf(CompanionPose.STAND) }
        var facingLeft by remember { mutableStateOf(true) }
        var act by remember { mutableStateOf(IdleAct.NONE) }

        // Drag state. While she is held, the patrol is suspended and she simply
        // follows the finger.
        var dragging by remember { mutableStateOf(false) }
        var dragX by remember { mutableStateOf(0f) }
        var dragY by remember { mutableStateOf(0f) }

        val fullWidth = (maxX - minX).coerceAtLeast(1f)
        suspend fun walkTo(x: Float, y: Float) {
            val hereX = homeX + walkedX.value
            val hereY = homeY + walkedY.value
            val tx = x.coerceIn(minX, maxX)
            val ty = y.coerceIn(minY, maxY)
            facingLeft = tx < hereX
            pose = CompanionPose.WALK
            val travel = kotlin.math.sqrt((tx - hereX) * (tx - hereX) + (ty - hereY) * (ty - hereY))
            val millis = ((travel / fullWidth) * FULL_WIDTH_MILLIS).toInt().coerceAtLeast(400)
            kotlinx.coroutines.coroutineScope {
                launch { walkedX.animateTo(tx - homeX, tween(millis, easing = LinearEasing)) }
                launch { walkedY.animateTo(ty - homeY, tween(millis, easing = LinearEasing)) }
            }
        }

        // Small things, at irregular moments, while she is standing. The
        // breath and the blink run on fixed clocks and a fixed clock is what a
        // paused GIF has too: after ten seconds the eye has learnt the period
        // and she reads as a loop. These are drawn from a pool at random gaps,
        // so nothing about her rest repeats, and each one is a thing an animal
        // actually does when it is waiting for nothing in particular.
        LaunchedEffect(still, dragging) {
            act = IdleAct.NONE
            if (still || dragging) return@LaunchedEffect
            while (true) {
                delay(2_200L + Random.nextLong(5_500))
                if (pose != CompanionPose.STAND) continue
                when (Random.nextInt(10)) {
                    0, 1 -> { act = IdleAct.SETTLE; delay(2_800L + Random.nextLong(3_000)) }
                    2, 3 -> { act = IdleAct.EAR; delay(180); act = IdleAct.NONE; delay(140); act = IdleAct.EAR; delay(180) }
                    4 -> { facingLeft = !facingLeft; delay(60) }
                    5, 6 -> { act = IdleAct.LOOK_UP; delay(900L + Random.nextLong(900)) }
                    7 -> { act = IdleAct.SNIFF; repeat(3) { delay(160); act = IdleAct.NONE; delay(120); act = IdleAct.SNIFF }; delay(160) }
                    8 -> { act = IdleAct.STRETCH; delay(700) }
                    else -> { act = IdleAct.BLINK; delay(110); act = IdleAct.NONE; delay(90); act = IdleAct.BLINK; delay(110) }
                }
                act = IdleAct.NONE
            }
        }

        // Where to stand to be beside the target: centred under it, just
        // below its bottom edge, and clamped to the screen — so a tab at the
        // very bottom puts her on her row above it, and a card mid-list puts
        // her at its foot.
        val nudgeSpot: Offset? = nudgeTarget?.let { r ->
            Offset(
                (r.center.x - layerOrigin.x - petW / 2f).coerceIn(minX, maxX),
                (r.bottom - layerOrigin.y + edge).coerceIn(minY, maxY),
            )
        }
        val pointing = nudge != null && nudgeSpot != null

        LaunchedEffect(still, interactionTick, dragging, homeX, homeY, nudge, nudgeSpot) {
            if (still || dragging) {
                pose = CompanionPose.STAND
                return@LaunchedEffect
            }
            if (nudge != null && nudgeSpot != null) {
                // Beckon. Walk to the thing, turn to face the user, and hold
                // there with the odd glance back at it, for as long as the
                // design's patience allows. Then let it go: she gave up, not
                // the user, and the caller decides when she may try again.
                walkTo(nudgeSpot.x, nudgeSpot.y)
                var waited = 0L
                while (waited < BECKON_FOR_MILLIS) {
                    pose = CompanionPose.FRONT
                    delay(2_200); waited += 2_200
                    pose = CompanionPose.STAND
                    facingLeft = (nudgeTarget?.center?.x ?: 0f) - layerOrigin.x < homeX + walkedX.value + petW / 2f
                    delay(1_100); waited += 1_100
                }
                onNudgeExpired(nudge)
                return@LaunchedEffect
            }
            // A new home resets the walk: this effect is keyed on it, and
            // re-entering is what clears the distance from the old one. After
            // a nudge or a bout she is somewhere else, and the pause before
            // the next bout is taken where she stands rather than snapping.
            pose = CompanionPose.STAND
            delay(SETTLE_BEFORE_WANDER)
            while (true) {
                // A bout: two to four errands across the screen, anywhere the
                // layout allows. Each leg has to be long enough to read as
                // going somewhere, never a shuffle on the spot.
                repeat(2 + Random.nextInt(3)) {
                    val hereX = homeX + walkedX.value
                    val hereY = homeY + walkedY.value
                    var tx: Float
                    var ty: Float
                    var tries = 0
                    do {
                        tx = minX + Random.nextFloat() * (maxX - minX)
                        ty = minY + Random.nextFloat() * (maxY - minY)
                        tries++
                    } while (
                        tries < 8 &&
                        kotlin.math.abs(tx - hereX) + kotlin.math.abs(ty - hereY) < petW * 1.5f
                    )
                    walkTo(tx, ty)
                    // Mostly she just stops. The turn to face you is the rare
                    // one, because at this size the front view is a coin and
                    // holding it makes her a badge rather than an animal.
                    pose = if (Random.nextFloat() < 0.2f) CompanionPose.FRONT else CompanionPose.STAND
                    delay(1_400L + Random.nextLong(3_200))
                }
                // Home for the long rest, roughly every other bout. Coming
                // back is what makes the place she was given mean something.
                if (Random.nextBoolean()) walkTo(homeX, homeY)
                pose = CompanionPose.STAND
                delay(REST_BETWEEN_TRIPS + Random.nextLong(12_000))
            }
        }

        // Snapped to whole grid pixels: a fractional translation resamples the
        // sprite and grows seams down the body on exactly the frames she moves.
        val restingX = if (dragging) dragX else homeX + walkedX.value
        val restingY = if (dragging) dragY else homeY + walkedY.value
        val x = kotlin.math.round(restingX / cell) * cell
        val y = kotlin.math.round(restingY / cell) * cell

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = x
                    translationY = y
                    // A small lift while held, which is the whole affordance:
                    // it is how someone finds out she can be moved at all,
                    // without a tutorial telling them.
                    scaleX = if (dragging) 1.15f else 1f
                    scaleY = if (dragging) 1.15f else 1f
                }
                .width(pixelSize * CompanionSprites.WIDTH)
                .height(pixelSize * CompanionSprites.HEIGHT)
                // Only while pointing is she a control. The rest of the time
                // she is decoration, and a screen reader stopping on a pig
                // with nothing to say would be noise.
                .then(
                    if (pointing) {
                        Modifier.semantics {
                            role = Role.Button
                            contentDescription = nudge!!.label
                            onClick { onNudgeTap(nudge); true }
                        }
                    } else {
                        Modifier
                    },
                )
                .pointerInput(nudge, pointing) {
                    detectTapGestures(onTap = { if (pointing) onNudgeTap(nudge!!) })
                }
                .pointerInput(homeX, homeY, minX, maxX, minY, maxY) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragX = homeX + walkedX.value
                            dragY = homeY + walkedY.value
                            dragging = true
                        },
                        onDrag = { _, delta ->
                            dragX = (dragX + delta.x).coerceIn(minX, maxX)
                            dragY = (dragY + delta.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = {
                            dragging = false
                            // Back to normalised, so the stored home means the
                            // same thing on a screen of another size.
                            val nx = if (maxX > minX) (dragX - minX) / (maxX - minX) else 0f
                            val ny = if (maxY > minY) (dragY - minY) / (maxY - minY) else 1f
                            onHomeChange(Offset(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f)))
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            CompanionPet(
                daysRecorded = daysRecorded,
                currencyCount = currencyCount,
                animal = animal,
                mood = mood,
                pixelSize = pixelSize,
                facingLeft = facingLeft,
                walking = pose == CompanionPose.WALK && !dragging,
                pose = if (dragging) CompanionPose.FRONT else pose,
                still = still || dragging,
                act = act,
            )
        }
    }
}

/**
 * A thing she does while standing. Each resolves to whole-pixel offsets in
 * [idleOffsets], the way every other motion in the rig does.
 */
enum class IdleAct { NONE, SETTLE, EAR, LOOK_UP, SNIFF, STRETCH, BLINK }

/** Her default home: the band above the tab bar, at the left, as the design had it. */
val DEFAULT_COMPANION_HOME = Offset(0.06f, 1f)

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
 * How long she stands by a thing before giving up. Long enough to be seen on
 * a normal visit, short enough that she is never *stationed* there: a pet that
 * lives on the review queue is a badge, and §5.5 has no badges.
 */
private const val BECKON_FOR_MILLIS = 20_000L

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
    act: IdleAct = IdleAct.NONE,
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
            offsets = if (walking) walkOffsets(stride) else idleOffsets(breath, headBreath, ear, shift, act),
            eyesShut = !still && (blink > 0.95f || act == IdleAct.BLINK),
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
private fun idleOffsets(
    breath: Float,
    headBreath: Float,
    ear: Float,
    shift: Float,
    act: IdleAct = IdleAct.NONE,
): RigOffsets {
    val settled = act == IdleAct.SETTLE
    return RigOffsets(
        // weightshift: she leans onto one side for roughly half of a 9s cycle,
        // which is most of what stops a standing animal reading as a paused GIF.
        bodyX = if (shift > 0.52f && shift < 0.94f) 1 else 0,
        // Settling drops the body a pixel onto the legs and stills the breath's
        // lift, which is what makes it read as sitting rather than as a glitch.
        bodyY = when {
            settled -> 1
            breath > 0.5f -> -1
            else -> 0
        },
        // The same breath, a fifth of a second later. Two shapes pulsing together
        // read as a pulse; offset, they read as one animal breathing.
        headY = when (act) {
            IdleAct.LOOK_UP -> -2
            IdleAct.STRETCH -> -1
            IdleAct.SNIFF -> 1
            IdleAct.SETTLE -> if (headBreath > 0.5f) 0 else 1
            else -> if (headBreath > 0.5f) -1 else 0
        },
        headX = if (act == IdleAct.SNIFF || act == IdleAct.STRETCH) 1 else 0,
        earX = if (act == IdleAct.EAR || (ear > 0.80f && ear < 0.90f)) 1 else 0,
        // Settled, the legs tuck: they lose the pixel the body gained.
        legY = if (settled) -1 else 0,
    )
}

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
