pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        id("com.android.library").version(extra["agp.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)

    }
}


rootProject.name = "Khell"
//includeBuild("gradle-ext")
include("khell-log")
include("khell-api")
include("khell")
