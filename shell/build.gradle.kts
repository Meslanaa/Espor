plugins {
    alias(libs.plugins.android.application)
}

val mesosVersionName: String by rootProject.extra
val mesosVersionCode: Int by rootProject.extra

// Release signing comes only from the environment (the release workflow decodes it
// from GitHub Actions secrets). Nothing secret is stored in the repository.
val releaseKeystore: String? = providers.environmentVariable("MESOS_KEYSTORE_FILE").orNull
val releaseKeystorePassword: String? = providers.environmentVariable("MESOS_KEYSTORE_PASSWORD").orNull

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
        resValue("string", "app_name", "MesOS")
    }

    signingConfigs {
        if (releaseKeystore != null && releaseKeystorePassword != null) {
            create("mesosRelease") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeystorePassword
                keyAlias = "mesos"
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            // Local development builds install next to the published MesOS instead of
            // clashing with it (different package, different signing key).
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "MesOS Dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("mesosRelease")
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":launcher"))
    implementation(project(":settings"))
    implementation(project(":updater"))
    implementation(project(":apps:camera"))
    implementation(project(":apps:photos"))
    implementation(project(":apps:files"))
    implementation(project(":apps:calculator"))
    implementation(project(":apps:notes"))
    implementation(project(":apps:weather"))
    implementation(project(":apps:calendar"))
    implementation(project(":apps:clock"))
    implementation(project(":apps:music"))
}
