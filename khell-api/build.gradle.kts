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
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutine"]}")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
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