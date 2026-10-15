plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.library)
    alias (libs.plugins.compose.compiler)
    id("publish")
}

android {
    namespace = "com.kiylx.tools.compose_ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
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
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.material)

    val bom = libs.versions.bom.get()

    // Import the Compose BOM
    implementation(platform("androidx.compose:compose-bom:${bom}"))
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")

    compileOnly(project(":core"))

}
