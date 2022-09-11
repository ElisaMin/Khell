# Kotlin Shell
a kotlin-ful shell executor
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
//coroutine required!
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:+")
// jvm log impl
implementation("org.slf4j:slf4j-log4j12:+")
//Khell
implementation("com.github.ElisaMin:khell:main")
//LOG lib
//implementation("com.github.ElisaMin:khell-log:main")
//or api("com.github.ElisaMin:khell-log:main")
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
