@file:OptIn(InternalCoroutinesApi::class)
package me.heizi.kotlinx.shell

/**
 * 单次执行 执行完毕后立即作废 设计缺陷
 */

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.heizi.kotlinx.logger.*
import me.heizi.kotlinx.shell.WriterRunScope.Companion.getDefaultRunScope
import java.io.IOException
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


@Deprecated(replaceWith = ReplaceWith("Shell"), message = "设计缺陷")
suspend fun shell(
    vararg commandLines:String,
    prefix:Array<String> = arrayOf("cmd","/k", "@echo off", ),
    globalArg:Map<String,String>?=null,
    isMixingMessage: Boolean = false,
    isWindows_keep: Boolean = true,
) = coroutineScope {
    shell(commandLines = commandLines, prefix, globalArg, isMixingMessage, isWindows_keep, Default)
}

/**
 *
 *
 * @param commandLines 所要丢给shell的指令
 * @param prefix 决定了以哪种形式打开这个解释器
 * @return
 */
@Deprecated(replaceWith = ReplaceWith("Shell"), message = "设计缺陷")
@Suppress( "BlockingMethodInNonBlockingContext", "SuspendFunctionOnCoroutineScope")
suspend fun CoroutineScope.shell(
    vararg commandLines:String,
    prefix:Array<String> = arrayOf("cmd","/k", "@echo off", ),
    globalArg:Map<String,String>?=null,
    isMixingMessage: Boolean = false,
    isWindows_keep: Boolean = true,
    dispatcher: CoroutineDispatcher = Default
): Flow<ProcessingResults> {
    val onCreateCommand = arrayOf(prefix.joinToString(" "),*commandLines)
    println("new command",onCreateCommand.joinToString(" && "))
    commandLines.forEach {
        println("commands",it)
    }
    return shell(prefix=prefix, env = globalArg, isMixingMessage=isMixingMessage, isEcho = false, dispatcher=dispatcher) {
        if (isWindows_keep) {
            commandLines.forEach(this::run)
            runBlocking {
                delay(300)
            }
            run("@exit")
        }
    }
}

/**
 *
 *
 * @param commandLines 所要丢给shell的指令
 * @param prefix 决定了以哪种形式打开这个解释器
 * @return
 */
@Deprecated("有新设计方案")
@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun shell(
    prefix: Array<String> = arrayOf("cmd", "/k"),
    env: Map<String, String>? = null,
    isMixingMessage: Boolean = false,
    isEcho: Boolean = false,
    dispatcher: CoroutineDispatcher = Default,
    block: suspend RunScope.() -> Unit,
): Flow<ProcessingResults> = coroutineScope scope@{
    fun println(any: Any?) = println("running", any.toString())
    fun debug(any: Any?) = "shell".debug("running", any.toString())
    debug("building runner")
    val flow = MutableSharedFlow<ProcessingResults>()
    val process = try {
        ProcessBuilder(*prefix).run {
            environment().putAll(Khell.env)
            env?.let { environment().putAll(it) }
            if (isMixingMessage) this.redirectErrorStream(true)

            start()
        }
    } catch (e: IOException) {
        println("catch IO exception \n $e")
        e.message?.let { msg ->
            when {
                msg.matches(exceptionRegex) -> {
                    runBlocking {
                        exceptionRegex.find(msg)!!.groupValues.let {
                            flow.emit(ProcessingResults.Error(it[2]))
                            flow.emit(ProcessingResults.CODE(it[1].toInt()))
                            flow.emit(ProcessingResults.Closed)
                        }
                    }
                    return@scope flow
                }
                "error=" in msg -> {
                    //["cannot run xxxx","114514,message"]
                    msg.split("error=")[1].split(",").let {
                        runBlocking {
                            //["114514","message"]
                            flow.emit(ProcessingResults.Error(it[1]))
                            flow.emit(ProcessingResults.CODE(it[0].toInt()))
                            flow.emit(ProcessingResults.Closed)
                        }
                    }
                    return@scope flow
                }
                else -> Unit
            }
        }
        throw IOException("未知错误", e)
    }
    debug("runner bullied")
    launch(dispatcher) {

        launch(dispatcher) {
            process.outputStream.writer().getDefaultRunScope(isEcho).let {
                debug("writing")
                block(it)
            }
        }.invokeOnCompletion {
            process.outputStream.runCatching { close() }
        }

        launch(dispatcher) {
            process.inputStream.bufferedReader(charset("GBK")).lineSequence().forEach {
                flow.emit(ProcessingResults.Message(it))
                println("message", it)
            }
        }
        //如果混合消息则直接跳过这次的collect
        if (!isMixingMessage) launch(dispatcher) {
            process.errorStream.bufferedReader(charset("GBK")).lineSequence().forEach {
                flow.emit(ProcessingResults.Error(it))
                "shell".error("failed", it)
            }
        }

    }.invokeOnCompletion {
        GlobalScope.launch {
            flow.emit(ProcessingResults.CODE(process.waitFor()))
            println("exiting")
            process.runCatching {
                inputStream.close()
                errorStream.close()
                destroy()
            }.onFailure {
                debug(it)
            }
            debug("all closed")
            flow.emit(ProcessingResults.Closed)
            debug("emit closed")
        }
    }
    flow
}

//private val socpe

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