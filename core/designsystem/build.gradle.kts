plugins {
    id("nemla.compose")
}

compose.resources {
    packageOfResClass = "app.nemla.core.designsystem.resources"
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
    }
}
