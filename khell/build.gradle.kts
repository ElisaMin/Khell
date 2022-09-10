import me.heizi.koltinx.version.versions

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    sourceSets {
        commonMain {
            dependencies {
                api(project(":khell-log"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["coroutine"]}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${versions["coroutine"]}")
                implementation("org.slf4j:slf4j-log4j12:${versions["slf4j"]}")
            }
        }

    }
}




group = "me.heizi.kotlinx"
version = versions["kotlin"]


//
//group = "me.heizi.kotlinx"
//version = versions["kotlin"]
//
//repositories {
//    mavenCentral()
//}

//dependencies {
//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
//}

//tasks.getByName<Test>("test") {
//    useJUnitPlatform()
//}