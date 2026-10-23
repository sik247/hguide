import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hguide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hguide.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // Fakes (LLM, diarization, voice clone, Pleos gateways) run the whole demo without a vehicle.
        buildConfigField("boolean", "USE_FAKES", "true")
        // Fake Pleos gateways (vehicle/location/navi/speech) driven by the in-app demo console. Debug default: on.
        // Release default: off (real Pleos SDK). Override: -PfakeGateways=true|false
        buildConfigField("boolean", "USE_FAKE_GATEWAYS", (findProperty("fakeGateways") ?: "true").toString())
        // Pleos CONTROL-class permissions are only usable inside the approved scope; the app never needs them.
        buildConfigField("boolean", "ENABLE_CONTROL_PERMISSIONS", "false")
    }

    // Demo-recording voices (debug only). Key + voice ids live in the git-ignored local.properties:
    // typecast.apiKey=… / typecast.voice.dad=… / typecast.voice.mom=… / typecast.voice.guide=…
    val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    fun localQuoted(key: String) = "\"" + (local.getProperty(key) ?: "") + "\""

    buildTypes {
        debug {
            buildConfigField("String", "TYPECAST_API_KEY", localQuoted("typecast.apiKey"))
            buildConfigField("String", "TYPECAST_VOICE_DAD", localQuoted("typecast.voice.dad"))
            buildConfigField("String", "TYPECAST_VOICE_MOM", localQuoted("typecast.voice.mom"))
            buildConfigField("String", "TYPECAST_VOICE_GUIDE", localQuoted("typecast.voice.guide"))
        }
        release {
            isMinifyEnabled = false
            // TODO(release): sign with the team keystore. Debug signing keeps the contest APK installable meanwhile.
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "USE_FAKES", "true") // TODO: flip to false once real vendors are wired
            buildConfigField("boolean", "USE_FAKE_GATEWAYS", (findProperty("fakeGateways") ?: "false").toString())
            listOf("TYPECAST_API_KEY", "TYPECAST_VOICE_DAD", "TYPECAST_VOICE_MOM", "TYPECAST_VOICE_GUIDE").forEach { buildConfigField("String", it, "\"\"") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Demo audio + ground-truth labels live in /sample and are packaged as assets/sample/*.
val syncSampleAssets by tasks.registering(Sync::class) {
    from(rootProject.file("sample")) { include("*.wav", "*.json") }
    into(layout.buildDirectory.dir("generated/sample-assets/sample"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/sample-assets"))
tasks.configureEach {
    if (name.matches(Regex("merge.*Assets")) || name.contains("Lint", ignoreCase = true)) dependsOn(syncSampleAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.fused)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.org.json)
}
