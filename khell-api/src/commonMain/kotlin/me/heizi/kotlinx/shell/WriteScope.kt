@file:Suppress("NOTHING_TO_INLINE")
package me.heizi.kotlinx.shell

import java.io.Writer


/**
 * 参考了JetpackCompose的写法，在运行时写点什么。
 *
 */

interface WriteScope {
    fun write(string: String)
}

inline infix fun WriteScope.run(command:String) = write(command+'\n')
inline infix fun WriteScope.echoRun (command:String) = "echo $command\n$command\n"
inline infix fun WriteScope.echo (command:String) = "echo $command\n"
inline infix fun WriteScope.printlnRun (command:String) {
    println(command)
    write("$command\n")
}
inline fun WriteScope.exit() = write("exit\n")
inline fun Writer.asCommandWriter() = object : WriteScope {
    override fun write(string: String) {
        this@asCommandWriter.write(string)
        flush()
    }
}