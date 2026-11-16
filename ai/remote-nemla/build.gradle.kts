plugins {
    id("nemla.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ai.api)
            implementation(projects.core.network)
            implementation(projects.core.common)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.ai.local)
            implementation(libs.ktor.client.mock)
        }
    }
}
