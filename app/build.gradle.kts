import com.android.utils.TraceUtils.simpleId

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "milan.bowzgore.mfp"
    compileSdk = 36

    defaultConfig {
        applicationId = "milan.bowzgore.mfp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"

            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
    splits {
        abi {
            /*isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64") // Choose only necessary ABIs*/
            isUniversalApk = true
        }
    }
    buildToolsVersion = "35.0.1"
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.legacy.support.v4)
    implementation(libs.recyclerview)
    implementation(libs.navigation.fragment)
    implementation(libs.jaudiotagger)
    // debugImplementation(libs.leakcanary.android)
}