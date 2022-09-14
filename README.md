# Kotlin Shell
a simple and powerful command line executor with kotlin dsl, coroutine and multiplatform supported.
## Help this project
I'm not read the hold document of Coroutines and just wrote it, 
so I think there's some misunderstandings on kotlin coroutine APIs, is that I overthink ? take a talk on Issues #2 maybe ? to...help this lib keep working?
## add to your project
### with Gradle & Jitpack
[![](https://jitpack.io/v/ElisaMin/Khell.svg)](https://jitpack.io/#ElisaMin/Khell)
### maven
```kotlin
maven { url = uri("https://jitpack.io")}
```
### dependencies
```kotlin
// required: coroutine
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

// gradle.properties if you're
val khellVersion = extra["khell.version"] as String

// Khell
implementation("com.github.ElisaMin:khell:$khellVersion")
// or Khell Android
implementation("com.github.ElisaMin:khell-android:$khellVersion")
// or Khell JVM
implementation("com.github.ElisaMin:khell-jvm:$khellVersion")

// option: but JVM log impl must be
implementation("org.slf4j:slf4j-log4j12:+")

// option: the LOG lib
//implementation("com.github.ElisaMin:khell-log:$khellVersion")
```

### with Gradle & GithubRepo
.....
## use as JVM for Windows
```kotlin
Shell(Dispatchers.IO) {
    run("echo hello world")
}.run {
    collect { //result as flow
        
    }
    await() //result as Deferred
}
```
