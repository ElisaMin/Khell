import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.libAndroid) apply false
}
val normalSettings = allprojects {
    group = "me.heizi.kotlinx"
    version = rootProject.libs.versions.khell.get()
    repositories {
        mavenCentral {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        google()
    }
}

val settings = subprojects { afterEvaluate {
    if (isMultiplatform)
        if(configKotlinMultiplatform())
            androidDefaultConfig()
} }

fun Project.configKotlinMultiplatform():Boolean {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    var isAndroid = false
    configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)
        targets.all {
            compilations.all {
                kotlinOptions {
                    freeCompilerArgs += "-Xcontext-receivers"
                }
            }
            if (this is KotlinAndroidTarget) {
                println("is Android project!")
                isAndroid = true
                compilations.all {
                    kotlinOptions.jvmTarget = "11"
                }
                sourceSets.findByName("androidInstrumentedTest")?.dependencies {
                    implementation(libs.androidx.runner)
                    implementation(libs.androidx.test.junit)
                    implementation(kotlin("test"))
                }
                publishLibraryVariants("release")
            }
        }
    }
    return isAndroid
}
fun Project.androidDefaultConfig() {
    apply(plugin = "com.android.library")
    configure<LibraryExtension> {
        compileSdk = 33
        @Suppress("DEPRECATION")
        defaultConfig {
            targetSdk = 33
            minSdk = 21
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        buildToolsVersion = "33.0.2"
        // disable lint class version check
        lint {
            abortOnError = false
            baseline = file("lint-baseline.xml")
        }
        if (!name.endsWith("-log"))
            namespace = "me.heizi.kotlinx.${name.replace("-",".")}"
    }
}
val publishing = subprojects {
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
        publications {
            repositories {
                maven {
                    name = "test"
                    url = uri("file://${rootProject.projectDir}/build/maven-repo/")
                }
                localRepo?.let {
                    maven {
                        url = uri(it)
                    }
                }
                if(!System.getenv("GITHUB_ACTOR").isNullOrEmpty() && !System.getenv("GITHUB_TOKEN").isNullOrEmpty()) maven {
                    name = "github"
                    url = uri("https://maven.pkg.github.com/ElisaMin/Khell")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
            val pom = { it: MavenPublication ->
                with(it) {
                    pom {
                        name = "Khell"
                        description = "packaging the JVM 's Process class to Kotlin Style to supports coroutines"
                        url = "https://github.com/ElisaMin/Khell"
                        developers {
                            developer {
                                id = "ElisaMin"
                                name = "ElisaMin"
                                email = "Heizi@lge.fun"
                            }
                        }
                        scm {
                            connection = "scm:git:git://github.com/ElisaMin/Khell.git"
                            developerConnection = "scm:git:ssh://github.com/ElisaMin/Khell.git"
                            url = "https://github.com/ElisaMin/Khell"
                        }
                    }
                }
            }
            filterIsInstance<MavenPublication>().forEach(pom)
        }
    }
}
val Project.isMultiplatform get() = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")

val Project.localRepo get() = file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
    Properties().apply {
        load(it)
    }["local"] as String?
}