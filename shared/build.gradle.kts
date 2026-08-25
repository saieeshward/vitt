plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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
