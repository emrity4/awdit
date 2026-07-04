plugins {
    id("com.android.application")
}

android {
    namespace = "com.odit.expensetracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.odit.expensetracker"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("buildNumber")?.toString()?.toIntOrNull() ?: 1)
        versionName = "1." + (project.findProperty("buildNumber")?.toString() ?: "0")
    }

    signingConfigs {
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {}
