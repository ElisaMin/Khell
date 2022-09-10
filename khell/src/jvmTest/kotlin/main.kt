import me.heizi.kotlinx.shell.Shell


suspend fun main() {
    println("called")


    println(Shell("echo heizi").await())
    println("callledddd")
}