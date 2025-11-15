plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.azhon.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.azhon.app"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("config") {
            keyAlias = "appUpdate"
            keyPassword = "123456"
            storeFile = file("./../app.jks")
            storePassword = "123456"
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-impl"))
    implementation(project(":compose-ui"))
    implementation(project(":view-ui"))

    implementation(libs.androidx.constraintLayout)
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.google.material)

    val bom = libs.versions.bom.get()
    // Import the Compose BOM
    implementation(platform("androidx.compose:compose-bom:$bom"))
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    androidTestImplementation(platform("androidx.compose:compose-bom:$bom"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}
