plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * All shared UI lives here, for both platforms.
 *
 * This is a *library*, not an application: from AGP 9 the Android application
 * plugin can no longer be applied alongside the Kotlin Multiplatform plugin, so
 * the app shells are separate thin modules (`:androidApp`, `iosApp/`) that do
 * nothing but host what this module produces.
 */
kotlin {
    android {
        namespace = "ie.shoonya.vitt.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: AppRoot takes a VittServices, so :shared
            // types are part of this module's public surface and the app shells
            // need them on their compile classpath.
            api(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            // For the create-document picker: `rememberLauncherForActivityResult`
            // lives in activity-compose, not in Compose Multiplatform.
            implementation(libs.androidx.activity.compose)
        }
    }
}
