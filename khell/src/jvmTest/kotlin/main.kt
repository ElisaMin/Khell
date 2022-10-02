import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.shell.Shell
import me.heizi.kotlinx.shell.keepCLIPrefix
import me.heizi.kotlinx.shell.shell
import kotlin.test.Test


var currentTime = System.currentTimeMillis()
operator fun String.not() {
    val oldTime = currentTime
    currentTime = System.currentTimeMillis()
    println(this + " -> "+(currentTime - oldTime))
}


suspend fun main() {
    !"start"
    Shell(startWithCreate = true, prefix = keepCLIPrefix) {
        run("ping baidu.com")
    }.await()
    !"old shell ping"
    Shell(startWithCreate = true, prefix = keepCLIPrefix) {
        run("ping baidu.com")
    }.await()
    !"new shell ping"
    repeat(3) {

        shell(prefix = keepCLIPrefix) {
            run("echo heizi")
        }.await()


    }
    !"shell"
    repeat(3,) {
        Shell(prefix = keepCLIPrefix) {
            run("echo heizi")
        }.await()
    }
    !"end"
}

class Branch {
    @Test
    fun loop3() = runBlocking {
        main()
    }
}
