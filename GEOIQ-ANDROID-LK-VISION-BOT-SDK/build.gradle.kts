plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "com.github.geoiq-tech-team" // Must match GitHub user/org name
version = "1.0.5"

android {
    namespace = "com.geoiq.geoiq_android_lk_vision_bot_sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 23

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
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    api ("io.livekit:livekit-android:2.16.0")
    api ("io.livekit:livekit-android-compose-components:1.3.0")

}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.geoiq-tech-team"
                artifactId = "geoiq-vision-android-sdk"
                version = "1.0.5"

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