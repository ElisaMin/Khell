# Kotlin Shell
a simple and powerful command line executor with kotlin dsl, coroutine and multiplatform supported.
## Help this project
I'm not read the hold document of Coroutines and just wrote it, 
so I think there's some misunderstandings on kotlin coroutine APIs, is that I overthink ? take a talk on Issues #2 maybe ? to...help this lib keep working?

# QuickStart

## read the docs and code 
it's a veeeeery simple lib with JUST 1 CORE FILE, and it's 200 lines code only. so [just read it](khell/src/commonMain/kotlin/me/heizi/kotlinx/shell/Shell.kt)   
```kotlin
Shell("echo hello world").await() {
    if (it is CommandResult.Success) it.let(::println)
}
Shell("ping xvidoes.com")
    .collect(::println)

val req = Shell {
    if ( foo ) 
        run("bar")
    if ( foo2 ){
        write("bar2")
        run()
    }
}
delay(3000)
req
    .await()
    .let(::doSth)
```
## there has a log lib
```kotlin
import me.heizi.koltinx.loggger.error
import me.heizi.koltinx.loggger.println


class `class name as tag` {
    
    fun log(msg:String) 
        = this.println("msg-log",msg)
    
    fun err(msg:String)
        = this.error("msg-log",msg)
}

```

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