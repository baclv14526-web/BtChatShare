import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

// ── Đọc version từ version.properties ──────────────────────────────────────
val versionProps = Properties().apply {
    val propsFile = rootProject.file("version.properties")
    if (propsFile.exists()) load(propsFile.inputStream())
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "1.0.0")
val appVersionCode: Int    = versionProps.getProperty("VERSION_CODE", "1").trim().toInt()
// ───────────────────────────────────────────────────────────────────────────

android {
    namespace = "com.example.btchatshare"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.btchatshare"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // APK được đổi tên trong workflow CI (bước "Rename APK")
    // vì BaseVariantOutputImpl là internal AGP API không ổn định trên AGP 8.x

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.cardview:cardview:1.0.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
