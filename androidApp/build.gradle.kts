import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Release signing reads the project-local key made by scripts/make-release-key.sh
// (keystore/ is gitignored). Without it, only release packaging fails, so debug
// builds, tests and lint still work on a fresh checkout.
val keystoreDir = rootProject.layout.projectDirectory.dir("keystore")
val keystorePropertiesFile = keystoreDir.file("keystore.properties").asFile
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
    }
val hasReleaseKey = keystorePropertiesFile.isFile

android {
    namespace = "app.newz"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "app.newz"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = keystoreDir.file(keystoreProperties.getProperty("storeFile")).asFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.ktor.client.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.android.driver)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Zero-Google guard (SPEC S2): the shipped app must not pull in Google service SDKs.
val forbiddenDependencyPrefixes =
    listOf(
        "com.google.firebase",
        "com.google.android.gms",
        "com.google.android.play",
        "com.google.ads",
        "com.google.android.libraries.places",
        // Prefix: also catches the downloadable-fonts artifact (fonts are bundled, SPEC S2).
        "androidx.compose.ui:ui-text-google",
    )

val verifyNoGoogleServices by tasks.registering {
    group = "verification"
    description = "Fails if releaseRuntimeClasspath contains a Google service SDK."
    val rootComponent =
        configurations
            .named("releaseRuntimeClasspath")
            .flatMap { it.incoming.resolutionResult.rootComponent }
    val forbidden = forbiddenDependencyPrefixes
    doLast {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(rootComponent.get()))
        val offenders = sortedSetOf<String>()
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            val id = component.id
            if (id is ModuleComponentIdentifier) {
                val coordinate = "${id.group}:${id.module}"
                if (forbidden.any { coordinate == it || coordinate.startsWith(it) }) {
                    offenders += "$coordinate:${id.version}"
                }
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.add(it.selected) }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Forbidden Google dependencies on releaseRuntimeClasspath:\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("verifyNoGoogleServices: releaseRuntimeClasspath is clean (${seen.size} components)")
    }
}

tasks.named("check") { dependsOn(verifyNoGoogleServices) }

if (!hasReleaseKey) {
    tasks
        .matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }
        .configureEach {
            doFirst {
                throw GradleException(
                    "Release signing key not found (keystore/keystore.properties). " +
                        "Run `bash scripts/make-release-key.sh` first.",
                )
            }
        }
}
