
package me.heizi.kotlinx.shell

/**
 * 单次执行 执行完毕后立即作废 设计缺陷
 */

import me.heizi.kotlinx.logger.debug
import java.io.OutputStreamWriter


/**
 * 参考了JetpackCompose的写法，在运行时写点什么。
 *
 */
interface RunScope {
    infix fun run(command:String)
//    fun write(string: String)
}

internal class WriterRunScope(
    private val writer: OutputStreamWriter, private val isEcho: Boolean = false,private val id :Int
):RunScope {
    override fun run(command: String) {
        if (isEcho) {
            writer.write("echo \"$command\" \n")
            writer.flush()
        }
        writer.write(command)
        "shell#$id".debug("command", command)
        writer.write("\n")
        writer.flush()
    }

//    override fun write(string: String) {
//        TODO("懒得写")
//    }

    companion object {
        internal fun OutputStreamWriter.getDefaultRunScope(isEcho: Boolean = false,id:Int) = WriterRunScope(this,isEcho,id)
    }

}

/**
 * 用于匹配错误的Regex
 */
internal val exceptionRegex by lazy {
    "Cannot run program \".+\": error=(\\d+), (.+)"
        .toRegex()
}
