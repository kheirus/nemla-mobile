plugins {
    id("nemla.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ai.api)
            implementation(libs.koin.core)
        }
    }
}
