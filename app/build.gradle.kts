import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.app.zoyalink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.app.zoyalink"
        minSdk = 26
        targetSdk = 35

        // =====================
        // 🔢 VERSION
        // =====================
        versionCode = 3
        versionName = "1.1.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // =========================
    // 🔐 SIGNING CONFIG (RELEASE)
    // =========================
    signingConfigs {
        create("release") {
            storeFile = file("../zoyalink-release.jks")
            storePassword = "07052020"
            keyAlias = "zoyalink"
            keyPassword = "07052020"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // debug default
        }
    }

    // =========================
    // 📦 AUTO RENAME APK (FINAL FIX)
    // =========================
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as ApkVariantOutputImpl
            outputImpl.outputFileName =
                "zoyalink-${versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = false
    }
}

dependencies {

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Material
    implementation("com.google.android.material:material:1.12.0")

    // WebView
    implementation("androidx.webkit:webkit:1.10.0")

    // Pull-to-refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")

    // Custom Tabs
    implementation("androidx.browser:browser:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}