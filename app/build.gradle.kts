plugins {
    id("com.android.application")
}

android {
    namespace = "app.vitranslator.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.vitranslator.tv"
        // Android 5.0: the first Android TV (the older ways of drawing over other apps are in Compat)
        minSdk = 21
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
