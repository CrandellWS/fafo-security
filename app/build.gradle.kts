plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ws.crandell.fafo"
    compileSdk = 36

    defaultConfig {
        applicationId = "ws.crandell.fafo"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
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
        // Plain-text report (build/reports/lint-results-debug.txt); CI prints it when lint fails.
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
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Spec section 6: no network or analytics libraries on the runtime classpath. A JVM test cannot see the
// Gradle classpath, so this is a verification task wired into `check` and run by CI.
val deniedGroups = listOf(
    "com.squareup.okhttp", "com.squareup.okhttp3", "com.squareup.retrofit2", "io.ktor", "org.chromium.net",
    "com.google.firebase", "com.google.android.gms", "com.crashlytics", "io.fabric", "io.sentry",
    "com.google.android.ads", "com.google.ads", "com.facebook.android", "com.facebook.audience",
    "com.applovin", "com.unity3d.ads", "com.ironsource", "com.mopub", "com.chartboost", "com.vungle",
    "com.inmobi", "com.adcolony", "com.flurry", "com.amplitude", "com.mixpanel", "com.segment",
    "io.branch", "com.appsflyer", "com.adjust", "com.bugsnag", "com.datadoghq", "com.newrelic", "com.instabug",
)

val checkNoRemoteDeps by tasks.registering {
    group = "verification"
    description = "Fails if a network or analytics library is on the runtime classpath (spec section 6)."
    doLast {
        val bad = mutableListOf<String>()
        listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").forEach { name ->
            project.configurations.getByName(name).incoming.resolutionResult.allComponents.forEach { c ->
                val id = c.moduleVersion
                if (id != null && deniedGroups.any { id.group == it || id.group.startsWith("$it.") }) {
                    bad += "$name: ${id.group}:${id.name}:${id.version}"
                }
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException("Denied network/analytics dependencies found:\n" + bad.joinToString("\n"))
        }
        println("checkNoRemoteDeps: no denied groups on debug/release runtime classpaths")
    }
}

tasks.named("check") { dependsOn(checkNoRemoteDeps) }
