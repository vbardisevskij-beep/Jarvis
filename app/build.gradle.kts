plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.vitalya.jarvis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vitalya.jarvis"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "4.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}




dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
