@file:OptIn(InternalCoroutinesApi::class)
package me.heizi.kotlinx.shell

/**
 * 单次执行 执行完毕后立即作废 设计缺陷
 */

import kotlinx.coroutines.InternalCoroutinesApi
import me.heizi.kotlinx.logger.debug
import java.io.OutputStreamWriter
import me.heizi.kotlinx.logger.println as logp


/**
 * 参考了JetpackCompose的写法，在运行时写点什么。
 *
 */
interface RunScope {
    fun run(command:String)
//    fun write(string: String)
}

internal class WriterRunScope(
    private val writer: OutputStreamWriter, private val isEcho: Boolean = false
):RunScope {
    override fun run(command: String) {
        if (isEcho) {
            writer.write("echo \"$command\" \n")
            writer.flush()
        }
        writer.write(command)
        "shell".debug("command", command)
        writer.write("\n")
        writer.flush()
    }

//    override fun write(string: String) {
//        TODO("懒得写")
//    }

    companion object {
        internal fun OutputStreamWriter.getDefaultRunScope(isEcho: Boolean = false) = WriterRunScope(this,isEcho)
    }

}

/**
 * 用于匹配错误的Regex
 */
internal val exceptionRegex by lazy {
    "Cannot run program \".+\": error=(\\d+), (.+)"
        .toRegex()
}

fun println(vararg msg:Any?) = "shell".logp(*msg)