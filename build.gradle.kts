import com.android.build.gradle.internal.scope.ProjectInfo.Companion.getBaseName
import me.heizi.koltinx.version.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply("gradle/genLocal.gradle.kts")

plugins {
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
    id("com.android.library") apply false
}

group = "me.heizi.kotlinx"
version = versions["khell"]

subprojects {
    apply( plugin = "maven-publish")
    apply( plugin =  "org.jetbrains.kotlin." +
            if (name!="khell-nu-process") "multiplatform" else  "jvm")
    val local = when {
        rootProject.file("local.properties").exists() -> rootProject.props["local"]["maven_repo_dir"] as String?
        System.getenv("LOCAL_MAVEN_REPO_DIR") != null -> System.getenv("LOCAL_MAVEN_REPO_DIR")
        else -> null
    }
    local?.let { configure<PublishingExtension> {
        repositories {
            maven {
                url = uri(it)
            }
        }
        publications {
            create("toLocal", MavenPublication::class.java){
//                println("!!")
                components.forEach(::println)
                from(components["kotlin"])
            }
        }
    } }

}


allprojects {
    repositories {
        google()
        mavenCentral()
    }
}