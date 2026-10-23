plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.custom.firelauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.custom.firelauncher"
        minSdk = 22
        targetSdk = 30 // Fire OS 8 is Android 11 (API 30)
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
