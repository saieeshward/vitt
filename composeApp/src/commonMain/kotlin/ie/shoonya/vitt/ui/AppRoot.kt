package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import ie.shoonya.vitt.ui.screens.SheetActions
import androidx.compose.ui.Modifier
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.Crypto
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ie.shoonya.vitt.model.Choice
import ie.shoonya.vitt.ui.theme.AccentChoice
import ie.shoonya.vitt.theme.Appearance
import androidx.compose.foundation.isSystemInDarkTheme
import ie.shoonya.vitt.ui.theme.ThemeChoice
import ie.shoonya.vitt.ui.theme.Vitt
import ie.shoonya.vitt.ui.theme.VittTheme

@Composable
fun AppRoot(
    services: VittServices,
    /** Runs the live Google checks instead of the app. Set from a launch variable. */
    verify: Boolean = false,
    autoRun: Boolean = false,
    /** The tab to open on, by name. Debug builds only, for screenshots. */
    startTab: String? = null,
) {
    // The palette is read here rather than inside the app, because VittTheme
    // wraps everything below it: a theme the user picks two levels down has to
    // re-enter composition from above to take effect at all.
    var appearance by remember { mutableStateOf(0) }

    // Read every recomposition rather than remembered: this is the one value
    // that can change while the app is open and untouched, when the phone
    // crosses into dark mode on a schedule or the user flips it in Control
    // Centre. Remembering it would leave the app in yesterday's mode.
    val systemDark = isSystemInDarkTheme()

    val dark = remember(appearance, systemDark) {
        Appearance.migrated(
            stored = services.ledger.choice(Choice.APPEARANCE),
            storedPaletteIsDark = ThemeChoice.isDarkCode(services.ledger.choice(Choice.THEME)),
        ).isDark(systemDark)
    }
    val theme = remember(appearance, dark) {
        // One key per side. The light palette survives a trip through Dark and
        // back, which a single key would destroy each way.
        val code = services.ledger.choice(if (dark) Choice.THEME_DARK else Choice.THEME)
        ThemeChoice.ofCode(code, dark = dark)
    }
    val accent = remember(appearance) {
        AccentChoice.ofCode(services.ledger.choice(Choice.ACCENT))
    }

    VittTheme(theme = theme, accent = accent) {
        ProvideWindowLayout {
            Surface(modifier = Modifier.fillMaxSize(), color = Vitt.colors.ground) {
                if (verify || autoRun) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        VerifyScreen(services, autoRun = autoRun)
                    }
                } else {
                    val syncStatus by services.sync.status.collectAsState()
                    val derivedTabs by services.derivedTabs.collectAsState()
                    // Text handed in from a share or a Shortcut, waiting to be
                    // confirmed. Collected here because VittApp is recreated on a
                    // theme change and the offer must survive that.
                    val pendingCapture by services.pendingCapture.collectAsState()
                    // The widget's button, as a counter: two taps are two requests.
                    val openAdd by services.openAdd.collectAsState()
                    val remoteRevision by services.sync.remoteChanges.collectAsState()
                    // A pull can change the theme too: it is a synced choice.
                    LaunchedEffect(remoteRevision) { appearance++ }
                    // The launch sync. Foreground returns come through the
                    // platform hosts, which are the only things that see them.
                    LaunchedEffect(Unit) { services.sync.onForeground() }
                    VittApp(
                        repository = services.ledger,
                        today = services.today(),
                        newId = { newTransactionId(services.today()) },
                        onAppearanceChange = { appearance++ },
                        pendingCapture = pendingCapture,
                        onCaptureConsumed = { services.captureConsumed() },
                        openAddTick = openAdd,
                        syncStatus = syncStatus,
                        derivedTabs = derivedTabs,
                        sheetActions = remember(services) {
                            SheetActions(
                                connect = { services.connectGoogle() },
                                disconnect = { services.disconnectGoogle() },
                                syncNow = { services.sync.syncNow() },
                                overwriteSheet = { services.overwriteDerivedTabs() },
                            )
                        },
                        remoteRevision = remoteRevision,
                        now = services::now,
                        startTab = startTab,
                    )
                }
            }
        }
    }
}

/**
 * A time-ordered identifier.
 *
 * Random ids scatter across an index; a time-prefixed one keeps recent rows
 * together, which is what almost every query wants.
 */
private fun newTransactionId(day: Int): String {
    val random = Crypto.randomBytes(8).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    return day.toString(16).padStart(6, '0') + "-" + random
}
