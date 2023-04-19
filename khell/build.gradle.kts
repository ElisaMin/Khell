plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    android()
    sourceSets {
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
        findByName("jvmTest")?.dependencies {
            implementation(libs.slf4j.log4j12)
            implementation(libs.kotlinx.coroutines.core.jvm)
        }
        findByName("androidMain")?.run {
            dependencies {
                implementation(libs.kotlinx.coroutines.core.android)

            }
        }
    }
}