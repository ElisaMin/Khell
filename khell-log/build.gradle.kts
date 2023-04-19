plugins {
    id("com.android.library")
    kotlin("multiplatform")
    `maven-publish`
}
kotlin {
    jvm()
    android {with(project.android) {
        namespace = "me.heizi.kotlinx.logger"
        compileSdk = 33
        @Suppress("DEPRECATION")
        defaultConfig {
            targetSdk = 33
            minSdk = 21
        }
    }}
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.core.common)
                implementation(libs.mockk)
                implementation(libs.slf4j.log4j12)
            }
        }
        
        getByName("jvmMain").dependencies {
            implementation(libs.slf4j.api)
        }
    }
}