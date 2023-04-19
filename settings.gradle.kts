pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}


rootProject.name = "Khell"
//includeBuild("gradle-ext")
include("khell-log")
include("khell-api")
include("khell")
include("khell-nu-process")
