plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Screenshot tests (test classpath only): `./gradlew recordRoborazziOssDebug` writes the PNGs Bill reviews.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ws.crandell.fafo"
    compileSdk = 36

    defaultConfig {
        applicationId = "ws.crandell.fafo"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // oss: the F-Droid / open-source build, no Google libraries at all. play: adds Google sign-in for
    // Gmail photo email. Same applicationId, so one installs over the other (same debug key).
    flavorDimensions += "dist"
    productFlavors {
        create("oss") {
            dimension = "dist"
            isDefault = true
            versionNameSuffix = "-oss"
        }
        create("play") {
            dimension = "dist"
            versionNameSuffix = "-play"
        }
    }

    // One shared, PUBLIC debug key (keystore/README.md) so CI and local debug builds install over each
    // other. There is deliberately no release signing config in this repo.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Plain-text report (build/reports/lint-results-<variant>.txt); CI prints it when lint fails.
        textReport = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

// Unit tests run on the debug variant only: Compose UI tests need ui-test-manifest, which is debug-only.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        (it as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

// The full JVM suite runs on oss. play runs only what differs per flavor (mail, manifest, endpoint and
// egress guards), so CI time does not double.
val playOnlyTests = listOf(
    "ws.crandell.fafo.mail.*", "ws.crandell.fafo.ManifestTest", "ws.crandell.fafo.NoRemoteEndpointsTest",
    "ws.crandell.fafo.EgressTest", "ws.crandell.fafo.RepoSecretsTest", "ws.crandell.fafo.FlavorTest",
)
tasks.withType<Test>().configureEach {
    // CI cannot download the test report from every environment: print the full cause of a failure in the log.
    testLogging {
        // standard_error too: kotlinx-coroutines-test prints leaked coroutine exceptions there.
        events("failed", "standard_error")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
    if (name == "testPlayDebugUnitTest") {
        filter {
            playOnlyTests.forEach { includeTestsMatching(it) }
            isFailOnNoMatchingTests = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // play flavor only: Google sign-in for Gmail send. Exact module allowlist in checkNoRemoteDeps.
    "playImplementation"(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}

// Spec section 6: no network or analytics libraries on the runtime classpath. A JVM test cannot see the
// Gradle classpath, so this is a verification task wired into `check` and run by CI.
// oss: the full deny list, including every Google Play services and Firebase module.
// play: the same deny list, except an exact allowlist of the Play services modules that
// play-services-auth (Gmail sign-in) pulls in. Any other gms module, any Firebase, ads or analytics fails.
val deniedGroups = listOf(
    "com.squareup.okhttp", "com.squareup.okhttp3", "com.squareup.retrofit2", "io.ktor", "org.chromium.net",
    "com.google.firebase", "com.google.android.gms", "com.crashlytics", "io.fabric", "io.sentry",
    "com.google.android.ads", "com.google.ads", "com.facebook.android", "com.facebook.audience",
    "com.applovin", "com.unity3d.ads", "com.ironsource", "com.mopub", "com.chartboost", "com.vungle",
    "com.inmobi", "com.adcolony", "com.flurry", "com.amplitude", "com.mixpanel", "com.segment",
    "io.branch", "com.appsflyer", "com.adjust", "com.bugsnag", "com.datadoghq", "com.newrelic", "com.instabug",
)
val playAllowedGms = setOf(
    "com.google.android.gms:play-services-auth",
    "com.google.android.gms:play-services-auth-api-phone",
    "com.google.android.gms:play-services-auth-base",
    "com.google.android.gms:play-services-base",
    "com.google.android.gms:play-services-basement",
    "com.google.android.gms:play-services-fido",
    "com.google.android.gms:play-services-tasks",
)

val checkNoRemoteDeps by tasks.registering {
    group = "verification"
    description = "Fails if a network or analytics library is on a runtime classpath (spec section 6), per flavor."
    doLast {
        val bad = mutableListOf<String>()
        val gmsSeen = sortedSetOf<String>()
        listOf("ossDebug", "ossRelease", "playDebug", "playRelease").forEach { variant ->
            val name = "${variant}RuntimeClasspath"
            val isPlay = variant.startsWith("play")
            project.configurations.getByName(name).incoming.resolutionResult.allComponents.forEach { c ->
                val id = c.moduleVersion ?: return@forEach
                val module = "${id.group}:${id.name}"
                if (isPlay && module in playAllowedGms) {
                    gmsSeen += "$module:${id.version}"
                    return@forEach
                }
                if (deniedGroups.any { id.group == it || id.group.startsWith("$it.") }) {
                    bad += "$name: $module:${id.version}"
                }
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException("Denied network/analytics dependencies found:\n" + bad.joinToString("\n"))
        }
        println("checkNoRemoteDeps: oss has no Google/network/analytics libraries; play gms modules (allowlisted):")
        gmsSeen.forEach { println("  $it") }
    }
}

tasks.named("check") { dependsOn(checkNoRemoteDeps) }
