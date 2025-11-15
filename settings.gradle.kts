rootProject.name = "AppUpdateChecker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
includeBuild("./build-logic")

pluginManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "MyLocalMaven"
            url = uri("F:/.m2/repository")
        }
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://www.jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven {
            name = "MyLocalMaven"
            url = uri("F:/.m2/repository")
        }
        maven("https://www.jitpack.io")
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}
include(":app")
include(":core-impl")
include(":compose-ui")
include(":view-ui")
