import java.util.Properties

plugins {
    id("nemla.android.application")
}

val localProperties =
    Properties().apply {
        val content =
            providers
                .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
                .asText
                .orNull
        if (content != null) load(content.reader())
    }

fun localProperty(key: String): String = localProperties.getProperty(key).orEmpty().replace("\"", "\\\"")

android {
    namespace = "app.nemla"

    defaultConfig {
        applicationId = "app.nemla"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProperty("SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${localProperty("API_BASE_URL")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.koin.android)
    debugImplementation(libs.compose.ui.tooling)
}
