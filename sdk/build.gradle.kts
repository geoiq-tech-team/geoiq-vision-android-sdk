plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.github.geoiq-tech-team"

// JitPack builds with `-Pversion=<the coordinate it resolved>`. Honour that: the version in the
// published POM must equal the coordinate consumers request, or Gradle rejects the module with
// "inconsistent module metadata found ... bad version". Overwriting it unconditionally also meant
// JitPack's shallow clone (no tags) fell through to 0.0.0-SNAPSHOT for every branch build.
// The git-describe fallback is for publishing locally, where no version is passed in.
val injectedVersion = (findProperty("version") as? String)
    ?.takeIf { it.isNotBlank() && it != Project.DEFAULT_VERSION }

version = injectedVersion ?: providers.exec {
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
            // Must stay false: R8 on a *library* shrinks its own published API. It dropped
            // GeoVisionTypeAliasesKt as unreferenced (nothing inside the SDK uses the aliases —
            // they exist for consumers) and stripped META-INF/*.kotlin_module, the only place
            // top-level typealiases are recorded. Consumers then fail to compile with
            // "Unresolved reference 'LocalVideoTrack'". No keep rule can restore the
            // .kotlin_module resource. The consuming app minifies instead, guided by
            // consumerProguardFiles above.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
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
    implementation(libs.androidx.profileinstaller)

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