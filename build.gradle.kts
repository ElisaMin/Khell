import com.android.build.gradle.LibraryExtension
import me.heizi.koltinx.version.props
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

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
    val isAndroidProject = name!="khell-nu-process"
    apply( plugin = "maven-publish")
    apply( plugin =  "org.jetbrains.kotlin." +
            if (isAndroidProject) "multiplatform" else  "jvm")
    if (isAndroidProject) {
        apply(plugin = "com.android.library")
        extensions.getByType<LibraryExtension>().apply {
            compileSdk = 33
            @Suppress("DEPRECATION")
            defaultConfig {
                targetSdk = 33
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            buildToolsVersion = "33.0.2"
            // disable lint class version check
            lint {
                checkReleaseBuilds = false
                abortOnError = false
                baseline = file("build/lint-baseline.xml")
            }
            if (!name.endsWith("-log"))
                namespace = "me.heizi.kotlinx.khell"

        }
    }
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
            create<MavenPublication>("toLocal") {
                from(components["kotlin"])
            }
        }
    } }

}
