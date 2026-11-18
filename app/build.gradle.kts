plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "fi.ropi.kitka"
    compileSdk = 35

    defaultConfig {
        applicationId = "fi.ropi.kitka"
        minSdk = 29
        targetSdk = 34
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    // Sama avain joka buildissa -> uusi versio asentuu vanhan päälle ja data säilyy.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Store version. The keystore is encrypted; its password lives only in the GitHub secret RELEASE_PASSWORD.
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("RELEASE_PASSWORD") ?: ""
            keyAlias = "finalapp"
            keyPassword = System.getenv("RELEASE_PASSWORD") ?: ""
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
