plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.github.geoiq-tech-team"
version = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().removePrefix("v").replace("/", "-").ifEmpty { "0.0.0-SNAPSHOT" }

android {
    namespace = "com.geoiq.geoiq_android_lk_vision_bot_sdk"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // api(), not implementation(): LiveKit types appear in this SDK's public signatures.
    api(libs.livekit.android)
    api(libs.livekit.android.compose.components)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.geoiq-tech-team"
                artifactId = "geoiq-vision-android-sdk"
                version = project.version.toString()

                pom {
                    name.set("GeoIQ Vision")
                    description.set("A Vision Android SDK built in Kotlin.")
                    url.set("https://github.com/geoiq-tech-team/geoiq-vision-android-sdk")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.html")
                        }
                    }
                    developers {
                        developer {
                            id.set("geoiq-tech-team")
                            name.set("GeoIQ Tech Team")
                            email.set("tech-team@geoiq.io")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/geoiq-tech-team/geoiq-vision-android-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com:geoiq-tech-team/geoiq-vision-android-sdk.git")
                        url.set("https://github.com/geoiq-tech-team/geoiq-vision-android-sdk")
                    }
                }
            }
        }
    }
}