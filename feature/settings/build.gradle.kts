plugins {
    id("nemla.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.notifications)
        }
    }
}

compose.resources {
    packageOfResClass = "app.nemla.feature.settings.resources"
}
