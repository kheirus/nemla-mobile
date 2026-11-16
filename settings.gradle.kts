rootProject.name = "nemla-mobile"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":core:common")
include(":core:domain")
include(":core:network")
include(":core:security")
include(":core:data")
include(":core:designsystem")
include(":core:audio")
include(":ai:api")
include(":ai:local")
include(":ai:remote-nemla")
include(":feature:auth")
include(":feature:dashboard")
include(":feature:voice")
include(":feature:onboarding")
include(":feature:settings")
