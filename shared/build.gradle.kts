plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()
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
        jvmTest.dependencies { implementation(libs.sqldelight.driver.jvm) }
        iosMain.dependencies { implementation(libs.sqldelight.driver.native) }
    }
}

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
