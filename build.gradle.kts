import me.heizi.koltinx.version.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply("gradle/genLocal.gradle.kts")

plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") apply false
}

group = "me.heizi.kotlinx"
version = versions["khell"]

subprojects {

    apply( plugin = "maven-publish")
    apply( plugin = "org.jetbrains.kotlin.multiplatform")

    configure<PublishingExtension> {

        val local = rootProject.props["local"]
        repositories {
            maven {
                url = uri(local["maven_repo_dir"] as String)
            }
        }
        publications {
            create("toLocal", MavenPublication::class.java){
//                println("!!")
                components.forEach(::println)
                from(components["kotlin"])
            }
        }
    }


}


allprojects {
    repositories {
        google()
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
}
