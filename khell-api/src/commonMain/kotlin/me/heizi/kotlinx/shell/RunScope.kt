package me.heizi.kotlinx.shell

/**
 * 参考了JetpackCompose的写法，在运行时写点什么。
 *
 */
interface RunScope {
    infix fun run(command:String)
//    fun write(string: String)
}