package app.nemla.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")
            val ktlintVersion = libs.versionOf("ktlint")
            val isRoot = this == rootProject
            extensions.configure<SpotlessExtension> {
                kotlin {
                    if (isRoot) {
                        target("build-logic/convention/src/**/*.kt")
                    } else {
                        target("src/**/*.kt")
                    }
                    targetExclude("**/build/**")
                    ktlint(ktlintVersion)
                }
                kotlinGradle {
                    if (isRoot) {
                        target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/convention/*.gradle.kts")
                    } else {
                        target("*.gradle.kts")
                    }
                    ktlint(ktlintVersion)
                }
            }
        }
    }
}
