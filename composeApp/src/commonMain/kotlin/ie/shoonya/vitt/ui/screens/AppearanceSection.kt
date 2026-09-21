package ie.shoonya.vitt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ie.shoonya.vitt.money.Currency
import ie.shoonya.vitt.ui.CompanionAnimal
import ie.shoonya.vitt.ui.CompanionPet
import ie.shoonya.vitt.ui.theme.AccentChoice
import ie.shoonya.vitt.theme.Appearance
import ie.shoonya.vitt.ui.theme.ThemeChoice
import ie.shoonya.vitt.ui.theme.Vitt
import ie.shoonya.vitt.ui.theme.colours

/**
 * Choosing the animal, the palette and the accent.
 *
 * Every option is shown as itself rather than named: an animal is a picture and
 * a palette is three colours, and a list of words would make the user apply each
 * one to find out what it is. That is the one place in this app where a preview
 * earns more space than a label.
 */
@Composable
fun AppearanceSection(
    companion: CompanionAnimal?,
    onCompanionChange: (CompanionAnimal?) -> Unit,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    accent: AccentChoice,
    onAccentChange: (AccentChoice) -> Unit,
    /** Drives the coin slots in the previews, so they match the real one. */
    currencyCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Vitt.space.base)) {
        Text("Companion", style = Vitt.type.caption, color = Vitt.colors.inkMuted)

        // Six animals plus None is seven tiles, which does not fit one row at a
        // readable size, so it wraps.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            maxItemsInEachRow = 4,
        ) {
            CompanionAnimal.entries.forEach { animal ->
                CompanionTile(
                    animal = animal,
                    selected = animal == companion,
                    currencyCount = currencyCount,
                    onClick = { onCompanionChange(animal) },
                    modifier = Modifier.weight(1f),
                )
            }
            // A real choice sitting alongside the animals rather than a switch
            // somewhere else: wanting no pet is a preference about the pet, and
            // it belongs where the pets are.
            NoPetTile(
                selected = companion == null,
                onClick = { onCompanionChange(null) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            if (companion == null) {
                "No pet. Your ledgers and streak are unchanged."
            } else if (companion.ruledOut != null) {
                // Says the limitation rather than hiding the animal. 14a keeps
                // both rejected ones as skins, so they are choosable, but a
                // hamster with two cheeks cannot show a third currency and the
                // user should hear that from the app and not discover it.
                "${companion.label}: ${companion.ruledOut}."
            } else {
                // Says the one thing a user cannot see from the pictures: the
                // slots are the same on every animal, because they mean
                // something.
                "${companion.label} carries your currencies as ${companion.marker}."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        // Above the palettes, because it decides which palettes are shown.
        Text("Appearance", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight)) {
            Appearance.entries.forEach { option ->
                FilterChip(
                    selected = option == appearance,
                    onClick = { onAppearanceChange(option) },
                    label = { Text(option.label, style = Vitt.type.label) },
                )
            }
        }
        Text(
            when (appearance) {
                Appearance.SYSTEM -> "Follows your phone's Light and Dark setting."
                Appearance.LIGHT -> "Always light, whatever the phone is set to."
                Appearance.DARK -> "Always dark, whatever the phone is set to."
            },
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )

        Text("Theme", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        // Only the four on the side the app is currently showing. Offering all
        // eight would mean tapping a light tile to leave Dark, which makes the
        // palette decide the appearance again, the exact tangle the two
        // settings exist to undo. Each side keeps its own pick.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.tight),
            maxItemsInEachRow = 4,
        ) {
            ThemeChoice.on(theme.dark).forEach { option ->
                ThemeTile(
                    theme = option,
                    accent = accent,
                    selected = option == theme,
                    onClick = { onThemeChange(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(theme.note, style = Vitt.type.label, color = Vitt.colors.inkMuted)

        Text("Accent", style = Vitt.type.caption, color = Vitt.colors.inkMuted)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Vitt.space.snug),
            verticalArrangement = Arrangement.spacedBy(Vitt.space.snug),
        ) {
            AccentChoice.entries.forEach { option ->
                AccentDot(
                    accent = option,
                    dark = theme.dark,
                    selected = option == accent,
                    onClick = { onAccentChange(option) },
                )
            }
        }
        Text(
            // The reason the list is short, stated once so it does not read as a
            // missing feature.
            "Green, amber, blue, pink and teal are your currency colours, so they stay out of here.",
            style = Vitt.type.label,
            color = Vitt.colors.inkMuted,
        )
    }
}

/** The opt-out, drawn as an empty pen so it reads as a choice and not a gap. */
@Composable
private fun NoPetTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Vitt.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Vitt.radius.tile))
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else colors.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Vitt.space.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A dash rather than a crossed-out animal. Crossing one out makes
            // the option read as a refusal of something the app wanted.
            Box(
                Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(Vitt.radius.pill))
                    .background(colors.inkFaint),
            )
        }
        Text(
            "None",
            style = Vitt.type.caption,
            color = if (selected) colors.accent else colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompanionTile(
    animal: CompanionAnimal,
    selected: Boolean,
    currencyCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Vitt.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Vitt.radius.tile))
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else colors.card)
            // The animal is a `Canvas`, so the tile's name comes from its caption
            // alone. That is enough; what was missing is that it is one of a set
            // and which one is on.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Vitt.space.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        // The real renderer, not a separate illustration, so a tile can never
        // show something the home screen would not.
        // Fixed streak, and still: a tile shows which animal it is, and five
        // animals breathing out of step would turn the picker into an aquarium.
        CompanionPet(
            daysRecorded = 30,
            currencyCount = currencyCount,
            animal = animal,
            mood = null,
            pixelSize = 2.dp,
            still = true,
        )
        Text(
            animal.label,
            style = Vitt.type.caption,
            color = if (selected) colors.accent else colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * A palette as three bands: ground, card and ink.
 *
 * Painted from the theme's own colours rather than the current one, which is the
 * only way a preview of an unselected theme can be honest.
 */
@Composable
private fun ThemeTile(
    theme: ThemeChoice,
    accent: AccentChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = Vitt.colors
    val preview = theme.colours(accent)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Vitt.radius.tile))
            // Selection marked exactly as it is on the companion tiles above,
            // so one glance reads both rows.
            .background(if (selected) current.accent.copy(alpha = 0.10f) else current.ground)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Vitt.space.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Vitt.space.hair),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Vitt.radius.pill))
                .background(preview.ground),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                // A card with a line of ink on it and an accent dot: the three
                // things every screen in the app is made of.
                val pad = size.width * 0.16f
                drawRoundRect(
                    color = preview.card,
                    topLeft = Offset(pad, size.height * 0.24f),
                    size = Size(size.width - pad * 2f, size.height * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
                drawRoundRect(
                    color = preview.ink,
                    topLeft = Offset(pad * 1.5f, size.height * 0.40f),
                    size = Size((size.width - pad * 3f) * 0.62f, size.height * 0.09f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
                drawCircle(
                    color = preview.accent,
                    radius = size.width * 0.055f,
                    center = Offset(size.width - pad * 1.7f, size.height * 0.60f),
                )
            }
        }
        Text(
            theme.label,
            style = Vitt.type.caption,
            color = if (selected) current.accent else current.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccentDot(
    accent: AccentChoice,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colour = accent.colour(dark)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) colour.copy(alpha = 0.18f) else Vitt.colors.card)
            // Two nested circles and no text anywhere: named here or not at all.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = accent.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 20.dp else 16.dp)
                .clip(CircleShape)
                .background(colour),
        )
    }
}
