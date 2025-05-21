
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

rootProject.name = "GEOIQ-ANDROID-LK-VISION-BOT-SDK"
include(":app")
include(":GEOIQ-ANDROID-LK-VISION-BOT-SDK")
