plugins {
    id("com.android.library")
    kotlin("multiplatform")
    `maven-publish`
}
android.namespace = "me.heizi.kotlinx.logger"
kotlin {
    jvm()
    android()
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