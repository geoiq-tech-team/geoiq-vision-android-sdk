plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

enum class SdkVariant {
    RELEASE, SNAPSHOT, DEBUG
}

android {
    namespace = "com.geoiq.geoiq_android_lk_vision_bot_sdk"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.geoiq.geoiq_android_lk_vision_bot_sdk"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "BASE_URL",
                "\"wss://lk-stg2.diq.geoiq.ai/\""
            )

            buildConfigField(
                "String",
                "API_KEY",
                "\"eyshaG9sbGVzX2Fwa1DopV9rCV12FwaV9rZXk6cassmmjas\""
            )

            buildConfigField(
                "String",
                "TOKEN_URL",
                "\"https://lk-va-token.diq.geoiq.ai/stg/v1/token\""
            )
        }

        debug {
            buildConfigField(
                "String",
                "BASE_URL",
                "\"wss://lk-stg2.diq.geoiq.ai/\""
            )

            buildConfigField(
                "String",
                "API_KEY",
                "\"eyshaG9sbGVzX2Fwa1DopV9rCV12FwaV9rZXk6cassmmjas\""
            )

            buildConfigField(
                "String",
                "TOKEN_URL",
                "\"https://lk-va-token.diq.geoiq.ai/stg/v1/token\""
            )
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
    val sdkVariant = SdkVariant.SNAPSHOT
    when (sdkVariant) {
        SdkVariant.RELEASE -> {
            implementation("$sdkModule:${libs.versions.geoiqVisionSdkRelease.get()}")
        }

        SdkVariant.SNAPSHOT -> {
            implementation("$sdkModule:${libs.versions.geoiqVisionSdkSnapshot.get()}")
        }

        SdkVariant.DEBUG -> {
            implementation(project(":GEOIQ-ANDROID-LK-VISION-BOT-SDK"))
        }
    }
}