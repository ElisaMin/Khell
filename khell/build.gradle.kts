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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "33.0.2"
}



group = "me.heizi.kotlinx"
version = versions["khell"]
dependencies {
    api(project(":khell-log"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
repositories {
    mavenCentral()
}


//tasks.withType<Test> {
//    useJUnitPlatform()
//}