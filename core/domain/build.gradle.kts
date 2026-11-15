plugins {
    id("nemla.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
