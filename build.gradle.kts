// Declared once here, applied in the modules. Without a single root declaration
// each subproject can load its own copy of the Kotlin Gradle Plugin into a
// separate classloader, which fails with confusing "cannot be cast to itself"
// errors when AGP's built-in Kotlin support meets the multiplatform plugin.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.sqldelight) apply false
}
