plugins {
    id("nemla.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "app.nemla.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.network)
            implementation(projects.core.security)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.ai.api)
            implementation(projects.ai.local)
            implementation(projects.ai.remoteNemla)
            implementation(projects.feature.auth)
            implementation(projects.feature.home)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

compose.resources {
    packageOfResClass = "app.nemla.shared.resources"
}
