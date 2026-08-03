import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

enum class SdkVariant {
    RELEASE, SNAPSHOT, DEBUG
}

// Production API keys live in local.properties (gitignored) — this repository is public.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localSecret(key: String): String = localProperties.getProperty(key).orEmpty().trim()

android {
    namespace = "com.geoiq.lk_vision_demo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.geoiq.lk_vision_demo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Voice and chat run on separate deployments with separate keys. Socket URLs are not
        // secret and live in GeoEnv; only the production keys come from local.properties:
        //   geoiq.voice.prod.apiKey=...
        //   geoiq.chat.prod.apiKey=...
        buildConfigField(
            "String",
            "GEO_VOICE_PROD_API_KEY",
            "\"${localSecret("geoiq.voice.prod.apiKey")}\""
        )
        buildConfigField(
            "String",
            "GEO_CHAT_PROD_API_KEY",
            "\"${localSecret("geoiq.chat.prod.apiKey")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    val sdkModule = "com.github.geoiq-tech-team:geoiq-vision-android-sdk"
    val sdkVariant = SdkVariant.DEBUG
    when (sdkVariant) {
        SdkVariant.RELEASE -> {
            implementation("$sdkModule:${libs.versions.geoiqVisionSdkRelease.get()}")
        }

        SdkVariant.SNAPSHOT -> {
            implementation("$sdkModule:${libs.versions.geoiqVisionSdkSnapshot.get()}")
        }

        SdkVariant.DEBUG -> {
            implementation(project(":sdk"))
        }
    }
}