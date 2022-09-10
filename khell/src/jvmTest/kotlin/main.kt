import kotlinx.coroutines.runBlocking
import me.heizi.kotlinx.logger.println
import me.heizi.kotlinx.shell.CommandResult.Companion.waitForResult
import me.heizi.kotlinx.shell.shell


suspend fun main() = runBlocking {
    println("called")

    println(shell(*arrayOf("echo heizi")).waitForResult())
    println("callledddd")
    Unit
}