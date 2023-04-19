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
        getByName("jvmTest").dependencies {
            implementation(libs.slf4j.log4j12)
            implementation(libs.kotlinx.coroutines.core.jvm)
        }
        getByName("androidMain").dependencies {
            implementation(libs.kotlinx.coroutines.core.android)
        }
        getByName("androidInstrumentedTest").dependencies {
//            implementation(project(mapOf("path" to ":khell")))
            implementation(libs.androidx.test.junit)
        }
    }
}