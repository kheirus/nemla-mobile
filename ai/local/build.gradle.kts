plugins {
    id("nemla.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ai.api)
            implementation(libs.koin.core)
        }
    }
}
