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
    (project.extra.get("kotlinCoroutineDependency")!! as DependencyHandler.()->Unit)()
    implementation(project(":logger"))
    implementation(kotlin("stdlib"))
}
