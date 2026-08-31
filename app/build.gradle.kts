plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "pk.vancott.tenders"
    compileSdk = 34

    defaultConfig {
        applicationId = "pk.vancott.tenders"
        // API 21 keeps this installable on the older Android phones that are
        // still common on site in Pakistan.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Where the app reads tenders from: the repo published by GitHub Actions.
        // Change this only if the repo is ever renamed or moved.
        
        buildConfigField(
            "String",
            "FEED_URL",
            "\"https://raw.githubusercontent.com/ShahzebJ8/vancott-tenders/main/data/tenders.json\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Lets modern Java/Kotlin date APIs run on old Android versions.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Background checks for new tenders + notifications.
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
