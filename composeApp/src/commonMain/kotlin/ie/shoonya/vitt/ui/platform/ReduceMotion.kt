package ie.shoonya.vitt.ui.platform

import androidx.compose.runtime.Composable

/**
 * Whether the user has asked the system to reduce motion.
 *
 * Honouring this is not optional. Vestibular disorders make continuous motion
 * genuinely unpleasant rather than merely distracting, and both stores treat the
 * setting as an accessibility contract rather than a hint. A companion that keeps
 * bobbing after the user has switched motion off is the kind of thing a reviewer
 * can reasonably hold a release on.
 *
 * Composable rather than a plain function because Android reads it from the
 * current `Context`, which only exists inside composition.
 */
@Composable
expect fun prefersReducedMotion(): Boolean
