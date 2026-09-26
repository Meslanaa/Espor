plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val mesosVersionName: String by rootProject.extra
val mesosVersionCode: Int by rootProject.extra

android {
    namespace = "org.mesos.shell"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mesos.shell"
        minSdk = 26
        targetSdk = 36
        // The Shell APK version is the MesOS release version (see mesos.properties),
        // so Android's own downgrade protection also guards MesOS updates.
        versionCode = mesosVersionCode
        versionName = mesosVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
