package app.nemla.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("nemla.spotless")

            val javaVersion = JavaVersion.toVersion(libs.versionOf("jvmTarget"))

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.versionOf("android-compileSdk").toInt()
                defaultConfig {
                    minSdk = libs.versionOf("android-minSdk").toInt()
                    targetSdk = libs.versionOf("android-targetSdk").toInt()
                }
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
                buildFeatures {
                    compose = true
                    buildConfig = true
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.versionOf("jvmTarget")))
                }
            }
        }
    }
}
