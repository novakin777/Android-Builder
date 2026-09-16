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
        versionCode = 101
        versionName = "1.1-bouncer"
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
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
