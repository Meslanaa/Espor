import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val mesosVersionName: String by rootProject.extra
val mesosVersionCode: Int by rootProject.extra
val mesosVersionLabel: String by rootProject.extra
val mesosChannel: String by rootProject.extra
val mesosBuildNumber: String by rootProject.extra

android {
    namespace = "org.mesos.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "MESOS_VERSION_NAME", "\"$mesosVersionName\"")
        buildConfigField("int", "MESOS_VERSION_CODE", mesosVersionCode.toString())
        buildConfigField("String", "MESOS_VERSION_LABEL", "\"$mesosVersionLabel\"")
        buildConfigField("String", "MESOS_BUILD_CHANNEL", "\"$mesosChannel\"")
        buildConfigField("String", "MESOS_BUILD_NUMBER", "\"$mesosBuildNumber\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.activity.compose)
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
