import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.shell.CommandResult
import me.heizi.kotlinx.shell.Shell
import kotlin.test.Test

class CommonTest {
//    @Test
    fun echoHelloWorld () = runBlocking {
        if (System.getProperty("os.name").startsWith("Windows"))
        Shell("echo hello world").await().let {
            if (it is CommandResult.Success) {
                assert(true)
                it.message.let {
                    println(it)
                    assert(it.lines().last().trim() == "hello world")
                }
            } else {
                println(it)
                assert(false)
            }

        }
    }
}