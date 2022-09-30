import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.shell.Shell
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
    repeat(3) {
        shell {
            run("echo heizi")
        }.await()
    }
    !"shell"
    repeat(3) {
        Shell {
            run("echo heizi")
        }.await()
    }
    !"Start"
}

class Branch {
    @Test
    fun loop3() = runBlocking {
        main()
    }
}
