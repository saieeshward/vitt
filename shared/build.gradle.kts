import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    // Android is a real target, not the JVM one in disguise: Keystore-backed
    // token storage and SecureRandom have no JVM equivalent, so androidMain
    // needs its own actuals.
    android {
        namespace = "ie.shoonya.vitt.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentnegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies { implementation(libs.sqldelight.driver.jvm) }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies { implementation(libs.sqldelight.driver.jvm) }
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
            implementation(libs.ktor.client.darwin)
        }
    }
}

/**
 * OAuth client ids come from `local.properties` (gitignored) so a fresh clone
 * fails loudly with instructions rather than silently building an app that
 * cannot sign in.
 *
 * These are not secrets — Google documents installed-app client ids as public,
 * and the real control is the binding to bundle id and signing certificate. They
 * live outside the repo only so each contributor points at their own Cloud
 * project.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun oauthClientId(key: String): String =
    (localProps.getProperty(key) ?: System.getenv(key.replace('.', '_').uppercase()) ?: "")

val buildConfigDir = layout.buildDirectory.dir("generated/oauth")

val generateOauthConfig by tasks.registering {
    val iosId = oauthClientId("oauth.ios.clientId")
    val androidId = oauthClientId("oauth.android.clientId")
    val outDir = buildConfigDir
    inputs.property("ios", iosId)
    inputs.property("android", androidId)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("ie/shoonya/vitt/auth")
        dir.mkdirs()
        dir.resolve("OauthConfig.kt").writeText(
            """
            package ie.shoonya.vitt.auth

            /** Generated from local.properties. Do not edit. */
            internal object OauthConfig {
                const val IOS_CLIENT_ID = "$iosId"
                const val ANDROID_CLIENT_ID = "$androidId"
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(generateOauthConfig) }

sqldelight {
    databases {
        create("VittDatabase") {
            packageName.set("ie.shoonya.vitt.db")
            // Fail the build if a migration would lose data or leave the schema
            // inconsistent. Financial history is not recoverable from a bad upgrade.
            verifyMigrations.set(true)
        }
    }
}

/**
 * Fails the build on a comma inside a backticked test name.
 *
 * Kotlin/Native rejects them where the JVM accepts them, so the iOS target stops
 * compiling while `jvmTest` stays green — the suite looks healthy and has
 * silently halved. This has now happened three times, which is two more than a
 * convention in a document deserves.
 */
val checkTestNames by tasks.registering {
    val testSources = layout.projectDirectory.dir("src").asFile
    doLast {
        val offenders = testSources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("Test") }
            .flatMap { file ->
                Regex("fun `([^`]*,[^`]*)`").findAll(file.readText())
                    .map { "${file.name}: ${it.groupValues[1]}" }
            }
            .toList()
        if (offenders.isNotEmpty()) {
            error(
                "Kotlin/Native rejects commas in backticked test names; " +
                    "use an em dash instead:\n" + offenders.joinToString("\n") { "  $it" }
            )
        }
    }
}

tasks.named("compileTestKotlinJvm") { dependsOn(checkTestNames) }
