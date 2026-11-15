plugins {
    `kotlin-dsl`
}

group = "app.nemla.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(plugin(libs.plugins.kotlin.serialization))
    implementation(plugin(libs.plugins.compose.compiler))
    implementation(plugin(libs.plugins.compose.multiplatform))
    implementation(plugin(libs.plugins.android.application))
    implementation(plugin(libs.plugins.android.kmp.library))
    implementation(plugin(libs.plugins.spotless))
}

fun plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

gradlePlugin {
    plugins {
        register("nemlaKmpLibrary") {
            id = "nemla.kmp.library"
            implementationClass = "app.nemla.buildlogic.KmpLibraryConventionPlugin"
        }
        register("nemlaCompose") {
            id = "nemla.compose"
            implementationClass = "app.nemla.buildlogic.ComposeConventionPlugin"
        }
        register("nemlaFeature") {
            id = "nemla.feature"
            implementationClass = "app.nemla.buildlogic.FeatureConventionPlugin"
        }
        register("nemlaAndroidApplication") {
            id = "nemla.android.application"
            implementationClass = "app.nemla.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("nemlaSpotless") {
            id = "nemla.spotless"
            implementationClass = "app.nemla.buildlogic.SpotlessConventionPlugin"
        }
    }
}
