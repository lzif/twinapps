plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.lzif.twinapps"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.lzif.twinapps"
        minSdk = 26
        targetSdk = 35
        // CI passes -PvCode/-PvName (GitHub run number); local builds use defaults.
        versionCode = (findProperty("vCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("vName") as String?) ?: "0.1.0"
    }

    // Fixed debug keystore (app/debug.keystore) so every CI build shares one
    // signature and debug updates install cleanly. Debug keys are not secret.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
