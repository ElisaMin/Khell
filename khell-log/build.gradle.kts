import me.heizi.koltinx.version.versions

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
}



group = "me.heizi.kotlinx"
version = versions["kotlin"]


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

