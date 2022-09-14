import me.heizi.koltinx.version.props
import me.heizi.koltinx.version.versions

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    `maven-publish`
}



kotlin {
    jvm()
    android {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        val commonMain by getting
        val jvmMain by getting {
            dependencies {
                api("org.slf4j:slf4j-api:${versions["slf4j"]}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.slf4j:slf4j-log4j12:${versions["slf4j"]}")
            }
        }
        val androidMain by getting {


        }
    }
}
android {
    namespace = "me.heizi.kotlinx.logger"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
    }
}


group = "me.heizi.kotlinx"
version = versions["khell"]



//apply(rootProject.file("publishing.gradle.kts"))


dependencies {

}


//dependencies {
//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
//}
//
//tasks.getByName<Test>("test") {
//    useJUnitPlatform()
//}
