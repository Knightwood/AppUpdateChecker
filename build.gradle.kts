// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.application) apply false
    alias(libs.plugins.library) apply false

    alias(libs.plugins.compose.compiler) apply false

    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

//tasks.register<Delete>("clean").configure {
//    delete(rootProject.buildDir)
//}

ext {
    this["version"] = "1.3.0"
}

