plugins {
    id("com.android.application")
}

android {
    namespace = "dev.watchtrust"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.watchtrust"
        minSdk = 30
        targetSdk = 35
        versionCode = 101
        versionName = "1.1-wear-diag"
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
