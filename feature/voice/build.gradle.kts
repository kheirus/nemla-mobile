plugins {
    id("nemla.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.audio)
        }
    }
}

compose.resources {
    packageOfResClass = "app.nemla.feature.voice.resources"
}
