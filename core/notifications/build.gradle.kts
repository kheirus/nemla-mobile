import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("nemla.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.common)
            implementation(libs.compose.runtime)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}
