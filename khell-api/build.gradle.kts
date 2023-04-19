import me.heizi.koltinx.version.versions

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
                api(libs.kotlinx.coroutines.core.common)
            }
        }
    }
}