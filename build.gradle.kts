import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import me.heizi.koltinx.version.versions


plugins {
    kotlin("multiplatform") apply false
}

group = "me.heizi.kotlinx"
version = versions["khell"]

allprojects {
    repositories {
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
