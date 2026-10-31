plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.mavenPublish)
    id("publish")
}

dependencies {
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.okio)
    implementation(libs.slf4j.api)
    testImplementation(libs.slf4j.logback.jvm)
}
