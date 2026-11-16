plugins {
    id("nemla.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(libs.compose.runtime)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
