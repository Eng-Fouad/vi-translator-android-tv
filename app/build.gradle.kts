plugins {
    id("com.android.application")
}

android {
    namespace = "app.vitranslator.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.vitranslator.tv"
        // Android 8: overlays over other apps (TYPE_APPLICATION_OVERLAY); every Google TV has it
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
}
