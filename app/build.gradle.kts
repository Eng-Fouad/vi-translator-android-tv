plugins {
    id("com.android.application")
}

// The version: -Pversion=1.2.3 (the release workflow passes the tag's), else 1.0.0. The version
// code grows with it (1.2.3 -> 10203), so a newer release always installs over an older one.
val appVersion = (findProperty("version") as String?)?.takeIf { it != "unspecified" } ?: "1.0.0"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

// Release signing, from the environment (the release workflow sets these from the repository's
// secrets): the keystore's path, its password, the key's alias and the key's password.
val releaseKeystore = System.getenv("ANDROID_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.vitranslator.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.vitranslator.tv"
        // Android 5.0: the first Android TV (the older ways of drawing over other apps are in Compat)
        minSdk = 21
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
