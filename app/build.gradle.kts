plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wanjia.auto"
    // 覆盖 HyperOS 2 (Android 15 / API 35) 与 HyperOS 3 (Android 16 / API 36)
    // 如需完全采用 Android 16 新行为，可改为 36（需 AGP 8.9+）
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wanjia.auto"
        minSdk = 30          // Android 11+，takeScreenshot 无障碍截图 API 30 起可用
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // ML Kit 中文 OCR：模型打包进 APK，不依赖 Google Play 服务，适合国内 HyperOS 设备
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
