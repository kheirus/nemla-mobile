package app.nemla.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("nemla.spotless")

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = nemlaNamespace
                    compileSdk = libs.versionOf("android-compileSdk").toInt()
                    minSdk = libs.versionOf("android-minSdk").toInt()
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.versionOf("jvmTarget")))
                    }
                    withHostTest {}
                }

                iosArm64()
                iosSimulatorArm64()

                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.lib("kotlin-test"))
                    implementation(libs.lib("kotlinx-coroutines-test"))
                }
            }
        }
    }
}
