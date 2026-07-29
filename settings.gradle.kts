
import java.net.URI

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Now URI will be recognized
        maven { url = URI("https://jitpack.io") }
    }
}

rootProject.name = "geoiq-vision-android-sdk"
include(":app")
include(":sdk")
include(":benchmark")
