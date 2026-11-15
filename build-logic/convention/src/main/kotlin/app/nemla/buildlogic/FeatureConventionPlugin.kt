package app.nemla.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("nemla.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(":core:domain"))
                    implementation(project(":core:common"))
                    implementation(project(":core:designsystem"))
                    implementation(libs.lib("androidx-lifecycle-viewmodel-compose"))
                    implementation(libs.lib("androidx-lifecycle-runtime-compose"))
                    implementation(libs.lib("androidx-navigation-compose"))
                    implementation(libs.lib("koin-core"))
                    implementation(libs.lib("koin-compose-viewmodel"))
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.lib("turbine"))
                }
            }
        }
    }
}
