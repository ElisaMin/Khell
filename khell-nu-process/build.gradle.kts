plugins {
    kotlin("jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.nuprocess)
    implementation(project(":khell-api"))
    api(project(":khell-log"))
    testImplementation(kotlin("test"))
    testImplementation(project(":khell"))
    testImplementation(libs.kotlinx.coroutines.debug)
}

kotlin {
    jvmToolchain(17)
    target.compilations.all {
        kotlinOptions {
            freeCompilerArgs += "-Xcontext-receivers"
        }
    }
}