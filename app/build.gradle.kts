plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ffxivmtoiletroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ffxivmtoiletroll"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("MYAPP_RELEASE_STORE_FILE") ?: property("MYAPP_RELEASE_STORE_FILE") as String)
            storePassword = System.getenv("MYAPP_RELEASE_STORE_PASSWORD") ?: property("MYAPP_RELEASE_STORE_PASSWORD") as String
            keyAlias = System.getenv("MYAPP_RELEASE_KEY_ALIAS") ?: property("MYAPP_RELEASE_KEY_ALIAS") as String
            keyPassword = System.getenv("MYAPP_RELEASE_KEY_PASSWORD") ?: property("MYAPP_RELEASE_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.opencv:opencv:4.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
}