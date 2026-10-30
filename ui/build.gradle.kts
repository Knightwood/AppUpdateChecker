plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.library)
    alias (libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("publish")
}

android {
    namespace = "com.github.knightwood.downloader.ui"
    compileSdkVersion("android-37.0")
    defaultConfig {
        minSdk = 24
        lint.targetSdk = 36
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
        // activity/compose 相关依赖以 JVM 11 编译, 1.8 无法内联其字节码
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

ksp {
    // 导出 Room 数据库 schema，供编写数据库迁移及迁移测试使用
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    compileOnly(project(":core"))
    compileOnly(project(":core-android"))
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.kotlin.serialization)

    val bom = libs.versions.bom.get()
    // Import the Compose BOM
    implementation(platform("androidx.compose:compose-bom:${bom}"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Navigation3
//    implementation("androidx.navigation3:navigation3-runtime:1.3.0-alpha02")
//    implementation("androidx.navigation3:navigation3-ui:1.3.0-alpha02")

    val lifecycle_version = "2.11.0"
    // Lifecycle & ViewModel
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    // ViewModel utilities for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version")
    // Lifecycle utilities for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycle_version")
    // Saved state module for ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycle_version")
    // ViewModel integration with Navigation3
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.12.0-alpha04")
    // optional - helpers for implementing LifecycleOwner in a Service
    implementation("androidx.lifecycle:lifecycle-service:$lifecycle_version")

    // Room
    val room_version = "2.8.5"
    implementation("androidx.room:room-runtime:$room_version")
    // If this project uses any Kotlin source, use Kotlin Symbol Processing (KSP)
    ksp("androidx.room:room-compiler:$room_version")
    // optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:${room_version}")
    // optional - Paging 3 Integration
    implementation("androidx.room:room-paging:${room_version}")

    // Paging
    val paging_version = "3.5.3"
    implementation("androidx.paging:paging-runtime:$paging_version")

    // DataStore
    // Preferences DataStore (SharedPreferences like APIs)
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.core.android)
}
