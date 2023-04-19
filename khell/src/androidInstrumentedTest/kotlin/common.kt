import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.logger.error
import me.heizi.kotlinx.shell.CommandResult
import me.heizi.kotlinx.shell.ExperimentalApiReShell
import me.heizi.kotlinx.shell.ReShell
import me.heizi.kotlinx.shell.await
import org.junit.Test
import kotlin.test.assertEquals


class CommonAndroidTest {
    @ExperimentalApiReShell
    @Test
    fun echoHelloWorld() { runBlocking {
        ReShell(forest = arrayOf("echo","hello world"))
            .map { it.also { "tester".error(it) } }
            .await().let { it as CommandResult.Success }.message.
        let { assertEquals( "hello world",it) }
    } }
}