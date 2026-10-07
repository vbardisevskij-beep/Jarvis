plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.vitalya.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vitalya.jarvis"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
