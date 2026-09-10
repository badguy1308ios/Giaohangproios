plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.example.giaohangpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.giaohangpro"
        minSdk = 24
        targetSdk = 35
        versionCode = ciBuildNumber ?: 1
        versionName = if (ciBuildNumber != null) "1.0.$ciBuildNumber" else "1.0-dev"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
