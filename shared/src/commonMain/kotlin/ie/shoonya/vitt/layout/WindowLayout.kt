package ie.shoonya.vitt.layout

/**
 * Which layout a window gets, decided from the window and never the device.
 *
 * An iPad in Split View at a third is a phone-width window, a Stage Manager
 * window can be any size, and a phone on its side is a short one: deciding by
 * device gets all three wrong. The breakpoints are Material's (600 and 840 dp of
 * width, 480 of height), so Android tablets and foldables come out of the same
 * rule. `docs/ipad-plan.clan`, decision D2.
 */
data class WindowLayout(val widthDp: Int, val heightDp: Int) {

    enum class Width { COMPACT, MEDIUM, EXPANDED }

    val width: Width
        get() = when {
            widthDp < MEDIUM_WIDTH -> Width.COMPACT
            widthDp < EXPANDED_WIDTH -> Width.MEDIUM
            else -> Width.EXPANDED
        }

    val shortHeight: Boolean get() = heightDp < SHORT_HEIGHT

    /**
     * A rail on the leading edge instead of the bottom bar: on anything wider
     * than a phone, and on a phone on its side, where height is what there is
     * least of. D3.
     */
    val useRail: Boolean get() = width != Width.COMPACT || shortHeight

    /**
     * Task sheets as a centred dialog rather than a full-width sheet. Only
     * when there is room both ways: a phone on its side keeps the sheet, which
     * at least has the whole width to work with. D4.
     */
    val dialogSheets: Boolean get() = width != Width.COMPACT && !shortHeight

    /** The widest a single column of reading gets before it is centred. */
    val maxContentWidthDp: Int? get() = if (width == Width.COMPACT) null else READABLE_WIDTH

    companion object {
        const val MEDIUM_WIDTH = 600
        const val EXPANDED_WIDTH = 840
        const val SHORT_HEIGHT = 480
        const val READABLE_WIDTH = 680

        /** A phone held upright, for previews and tests that do not care. */
        val PHONE = WindowLayout(widthDp = 390, heightDp = 844)
    }
}
