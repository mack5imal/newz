plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "app.newz.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        withHostTest { }
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Xcode may be installed without an iOS simulator runtime; the iOS test binary is still
// compiled and linked, but running it needs a simulator, so skip only the run step then.
val hasIosSimulatorRuntime =
    providers
        .exec {
            commandLine("xcrun", "simctl", "list", "runtimes")
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.contains("iOS ") }

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    onlyIf("an iOS simulator runtime is installed") { hasIosSimulatorRuntime.getOrElse(false) }
}
