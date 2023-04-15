import me.heizi.koltinx.version.versions

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    android {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":khell-log"))
                implementation(project(":khell-api"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutine"]}")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${versions["coroutine"]}")
                implementation("org.slf4j:slf4j-log4j12:${versions["slf4j"]}")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${versions["coroutine"]}")
            }
        }
//        val androidTest by getting {
//            dependencies {
//                implementation ("androidx.test.ext:junit:1.1.3")
//            }
//        }

    }
}

android {
    namespace = "me.heizi.kotlinx.khell"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        baseline = file("build/lint-baseline.xml")
    }
}



group = "me.heizi.kotlinx"
version = versions["khell"]
dependencies {
    api(project(":khell-log"))
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
}
repositories {
    mavenCentral()
}


//tasks.withType<Test> {
//    useJUnitPlatform()
//}