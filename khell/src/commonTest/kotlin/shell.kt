import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.logger.debug
import me.heizi.kotlinx.shell.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CommonTest {
    @Test
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
    @Test fun `ping baidu` (): Unit = runBlocking {
        Shell("ping baidu.com").await()
    }
}
class ReTest {
    @Test fun `ping baidu by -k(eep)`() = runBlocking {
        ReShell(keepCLIPrefix) {
            this printlnRun  "ping baidu.com"
            debug("exiting")
            exit()
        }.collect(::println)
    }
    @Test fun `ping baidu by -c`() = runBlocking {
        ReShell(defaultPrefix+"ping baidu.com")
            .collect(::println)
    }
    @Test fun `echo hello world result`() = runBlocking {
        ReShell(defaultPrefix+"echo hello world")
            .await().let { it as? CommandResult.Success }!!.message.let {
                repeat(it.length) {i->
                    assertEquals(it[i].code,"hello world"[i].code)
                }
//                println(it.lines().joinToString(prefix = "|", postfix = "|"))
                assertEquals("hello world",it)
            }
    }
}