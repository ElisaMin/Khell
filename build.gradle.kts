import me.heizi.gradle.Libs
plugins {
    kotlin("jvm")
}

group = "me.heizi.kotlinx"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {

    @Suppress("UNCHECKED_CAST")
    implementation(Libs.Coroutine)
    implementation(project(":logger"))
    implementation(kotlin("stdlib"))
}
