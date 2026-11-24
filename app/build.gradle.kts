plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kamera.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kamera.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    val c = "1.3.4"
    implementation("androidx.camera:camera-core:$c")
    implementation("androidx.camera:camera-camera2:$c")
    implementation("androidx.camera:camera-lifecycle:$c")
    implementation("androidx.camera:camera-view:$c")
    implementation("androidx.camera:camera-video:$c")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("com.google.mlkit:image-labeling:17.0.9")
}
