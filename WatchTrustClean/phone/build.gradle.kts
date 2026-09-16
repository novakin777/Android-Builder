plugins {
    id("com.android.application")
}

android {
    namespace = "dev.watchtrust"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.watchtrust"
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = "1.0-clean"
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
    compileOnly(project(":system-stubs"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
