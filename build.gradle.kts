import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

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
subprojects { afterEvaluate {
    if (isMultiplatform)
        if(configKotlinMultiplatform())
            androidDefaultConfig()
} }
fun Project.configKotlinMultiplatform():Boolean {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    var isAndroid = false
    configure<KotlinMultiplatformExtension> {
        jvmToolchain(19)
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
            }
        }
        if (isAndroid) androidDefaultConfig()
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
interface Getter<T> {
    operator fun get(key:String):T
}
val Project.props get() = object : Getter<java.util.Properties> {
    override fun get(key: String): java.util.Properties = java.util.Properties().apply {
        file("$key.properties").inputStream().use(::load)
    }
}

operator fun <T> Property<T>.setValue (thisRef:Any?, prop: kotlin.reflect.KProperty<*>, value :T) {
    set(value)
}
subprojects { when {
    rootProject.file("local.properties").exists() -> rootProject.props["local"]["maven_repo_dir"] as String?
    System.getenv("LOCAL_MAVEN_REPO_DIR") != null -> System.getenv("LOCAL_MAVEN_REPO_DIR")
    else -> null
}?.let {afterEvaluate {
    apply(plugin = "org.jetbrains.kotlin.${ "multiplatform".takeIf { isMultiplatform } ?: "jvm" }")
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
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
    } } }
}
val Project.isMultiplatform get() = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")