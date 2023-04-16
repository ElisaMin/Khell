import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import me.heizi.kotlinx.logger.debug
import me.heizi.kotlinx.logger.println
import me.heizi.kotlinx.shell.*
import java.lang.StringBuilder
import java.util.LinkedList
import kotlin.test.Test


val timess = LinkedList<String>()
var currentTime = System.currentTimeMillis()
inline operator fun String.not() {
    val oldTime = currentTime
    currentTime = System.currentTimeMillis()
    (this + " -> "+(currentTime - oldTime)).let {
        println(it)
        timess.add(it)
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
suspend fun main() = coroutineScope {
    DebugProbes.install()
    println(System.getProperty("com.zaxxer.nuprocess.threads", "auto"))
    !"start"
//    val a = S
//    Shell(startWithCreate = true, prefix = keepCLIPrefix) {
//        run("ping baidu.com")
//    }.await()
//    !"old shell ping"
//    shell(coroutineStart = CoroutineStart.DEFAULT, prefix = keepCLIPrefix) {
//        run("ping baidu.com")
//    }.await()
//    !"new shell ping"
//    NuShell(startWithCreate = true, prefix = keepCLIPrefix) {
//        run("ping baidu.com")
//    }.await()
    val times = 100
//    !"nu shell ping"
//    repeat(times) {
//        shell(prefix = keepCLIPrefix) {
//            run("echo heizi")
//        }.await()
//    }
//    !"nu shell"
    repeat(times,) {
        DebugProbes.dumpCoroutines()
        NuShell(prefix = arrayOf("cmd","/c","echo heizi")) {
        }.join()
    }
    !"shell"
//    repeat(times,) {
//        Shell(prefix = keepCLIPrefix) {
//            run("echo heizi")
//        }.await()
//    }
//    !"end"
    timess.forEach(::println)
}

class Branch {
    @Test
    fun loop3() = runBlocking {
        main()
    }
    @Test
    fun test() = runBlocking {
        println("haha")
        debug("haha")
//        Shell( "echo heizi").collect {
//            println(it)
//        }
        NuShell(prefix = arrayOf("cmd","/c","echo heizi"))
            .apply { start() }
            .collect {
            println(it)
        }
    }
}
