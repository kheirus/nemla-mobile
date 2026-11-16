import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("nemla.feature")
}

kotlin {
    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = "app.nemla.feature.statementimport"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.documents)
        }
    }
}

compose.resources {
    packageOfResClass = "app.nemla.feature.statementimport.resources"
}
