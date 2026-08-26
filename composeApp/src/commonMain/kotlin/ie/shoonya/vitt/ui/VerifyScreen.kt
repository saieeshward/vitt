package ie.shoonya.vitt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.shoonya.vitt.VittServices
import ie.shoonya.vitt.auth.AuthResult
import ie.shoonya.vitt.sheets.LiveVerification
import kotlinx.coroutines.launch

/**
 * Runs the live checks against a real Google account and shows what happened.
 *
 * Not a product screen — it exists so the assumptions underneath the storage
 * design can be confirmed against Google rather than against a test double.
 */
@Composable
fun VerifyScreen(services: VittServices, autoRun: Boolean = false) {
    val auth = services.auth
    val scope = rememberCoroutineScope()
    val steps = remember { mutableStateListOf<LiveVerification.Step>() }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(if (auth.isSignedIn) "Signed in." else "Not signed in.") }
    var signedIn by remember { mutableStateOf(auth.isSignedIn) }

    // Lets the checks be driven from a launch environment variable, so a run
    // against real Google can be executed and captured without a human tapping
    // a button on a physical device.
    LaunchedEffect(autoRun) {
        if (!autoRun || !auth.isSignedIn) return@LaunchedEffect
        running = true
        message = "Running checks…"
        val verification = services.liveVerification()
        runCatching {
            verification.run { step ->
                steps.add(step)
                println("VITT-CHECK ${if (step.passed) "PASS" else "FAIL"} | ${step.name} | ${step.detail}")
            }
        }.onFailure {
            println("VITT-CHECK ABORTED | ${it.message}")
            message = "Aborted: ${it.message}"
        }.onSuccess {
            val failed = it.count { s -> !s.passed }
            println("VITT-CHECK DONE | $failed of ${it.size} failed")
            message = if (failed == 0) "All ${it.size} checks passed." else "$failed of ${it.size} checks failed."
        }
        running = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VITT — live verification", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    message = "Opening Google…"
                    scope.launch {
                        message = when (val r = auth.signIn()) {
                            is AuthResult.Code -> { signedIn = true; "Signed in." }
                            is AuthResult.Cancelled -> "Sign-in cancelled."
                            is AuthResult.Failed -> "Sign-in failed: ${r.reason}"
                        }
                        running = false
                    }
                },
            ) { Text(if (signedIn) "Sign in again" else "Connect Google Drive") }

            OutlinedButton(
                enabled = !running && signedIn,
                onClick = {
                    scope.launch {
                        auth.signOut(); signedIn = false; steps.clear(); message = "Disconnected."
                    }
                },
            ) { Text("Disconnect") }
        }

        Button(
            enabled = !running && signedIn,
            onClick = {
                running = true
                steps.clear()
                message = "Running checks…"
                scope.launch {
                    val token = auth.accessToken()
                    if (token == null) {
                        message = "No valid token — sign in again."
                        running = false
                        return@launch
                    }
                    val verification = services.liveVerification()
                    runCatching { verification.run { steps.add(it) } }
                        .onFailure { message = "Aborted: ${it.message}" }
                        .onSuccess {
                            val failed = it.count { s -> !s.passed }
                            message = if (failed == 0) {
                                "All ${it.size} checks passed."
                            } else {
                                "$failed of ${it.size} checks failed."
                            }
                        }
                    running = false
                }
            },
        ) { Text("Run live checks") }

        if (running) CircularProgressIndicator()

        steps.forEach { step ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${if (step.passed) "PASS" else "FAIL"}  ${step.name}",
                    color = if (step.passed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    step.detail,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
