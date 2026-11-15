plugins {
    id("nemla.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.koin.core)
        }
    }
}
