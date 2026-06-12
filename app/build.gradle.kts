plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.impulsegate"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.impulsegate"
        minSdk = 26
        // 34, not 35: Android 15 forces edge-to-edge on targetSdk 35, which would draw
        // the settings screen under the status bar. Nothing here needs 35 behavior.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // debug-signed so the release APK can be sideloaded directly
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
