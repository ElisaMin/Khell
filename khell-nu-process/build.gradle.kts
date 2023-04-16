import me.heizi.koltinx.version.versions

plugins {
    kotlin("jvm")
}

group = "me.heizi.kotlinx"
version = versions["khell"]
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${rootProject.versions["coroutine"]}")
    implementation("com.zaxxer:nuprocess:2.0.5")
    implementation(project(":khell-api"))
    api(project(":khell-log"))
    implementation("org.slf4j:slf4j-log4j12:${rootProject.versions["slf4j"]}")
    testImplementation(kotlin("test"))
    testImplementation(project(":khell"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${rootProject.versions["coroutine"]}")
}
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(19)
    target.compilations.all {
        kotlinOptions {
            freeCompilerArgs += "-Xcontext-receivers"
        }
    }
}