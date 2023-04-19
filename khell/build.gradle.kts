import me.heizi.koltinx.version.versions

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    android()
    sourceSets {
        val libs = rootProject.libs
        commonMain {
            dependencies {
                api(project(":khell-log"))
                implementation(project(":khell-api"))
                api(libs.kotlinx.coroutines.core.common)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.slf4j.log4j12)
                implementation(libs.kotlinx.coroutines.core.jvm)
            }
        }
        findByName("androidMain")?.run {
            dependencies {
                implementation(libs.kotlinx.coroutines.core.android)

            }
        }
//        findByName("androidTest")?.run {
//            dependencies {
//                implementation(libs.androidx.test.ext.junit)
//
//            }
//        }
    }
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += "-Xcontext-receivers"
            }
        }
    }
}