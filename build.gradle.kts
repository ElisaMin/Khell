import com.android.build.gradle.internal.scope.ProjectInfo.Companion.getBaseName
import me.heizi.koltinx.version.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply("gradle/genLocal.gradle.kts")

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.libAndroid) apply false
}

allprojects {
    group = "me.heizi.kotlinx"
    version = rootProject.libs.versions.khell.get()
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    apply( plugin = "maven-publish")
    apply( plugin =  "org.jetbrains.kotlin." +
            if (name!="khell-nu-process") "multiplatform" else  "jvm")
    val local = when {
        rootProject.file("local.properties").exists() -> rootProject.props["local"]["maven_repo_dir"] as String?
        System.getenv("LOCAL_MAVEN_REPO_DIR") != null -> System.getenv("LOCAL_MAVEN_REPO_DIR")
        else -> null
    }
    kotlinExtension.apply {
        jvmToolchain(19)
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
