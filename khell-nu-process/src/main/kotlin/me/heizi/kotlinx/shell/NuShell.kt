package me.heizi.kotlinx.shell

import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharedFlow
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * ## NuShell - NuProcess的封装类
 * Create and run a [NuProcess] by command line , that's Shell no matter is Windows call it or not .
 * This class implemented [Deferred] asynchronous coroutine and [SharedFlow] ,
 * That means you can use await to wait for [CommandResult]  or collect [ProcessingResults].
 *
 * I recommend the fake constructor if you just want to **run a simple command**
 * @see Shell.invoke
 *
 * @property prefix how to start a process.
 * @property env event args
 * @property isMixingMessage stderr redirect to stdout when true
 * @property isEcho echo command line before run the command line.
 * @property onRun you can delay or something to using [RunScope.run] run some fancy line.
 * @param coroutineContext I don't know what's it So I'm just added it.
 * @param charset make sure you won't see some fancy line you have never seen before
 * @param startWithCreate don't you can read the name ? idiot
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)

class NuShell constructor(
    coroutineContext: CoroutineContext= IO,
    private val prefix: Array<String> = defaultPrefix,
    private val env: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    id: Int = getNewId(),
    workdir: Path? = null,
    private val charset: Charset = defaultCharset,
    private val onRun: suspend RunScope.() -> Unit,
): AbstractKShell(coroutineContext, prefix, env, isMixingMessage, isEcho, startWithCreate, id, charset,workdir?.toFile(), onRun)
{
    private val process by lazy {
        debug("runner","building")
        handled { NuProcessBuilder(nuHandler,*prefix).run {
            env?.let { environment().putAll(it) }
            workdir?.let { setCwd(it) }
            start().also { println("runner","built") }
        } }
    }
    private val nuHandler by lazy {
        object : NuAbstractCharsetHandler(charset) {
            override fun onPreStart(nuProcess: NuProcess) {
                stdin(nuProcess)
            }
            override fun onExit(exitCode: Int) {
                lastMsg(code = exitCode, msg = null)
            }
            override fun onStderrChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult) {
                stderr(buffer)
            }
            override fun onStdoutChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult) {
                stdout(buffer)
            }

        }
    }
    private fun stdin(nuProcess: NuProcess)  {
        object : RunScope {
            override fun run(command: String) {
                val invokingCommand = command.takeIf { it.last() == '\n' } ?: "$command\n"
                debug("writing",invokingCommand)
                nuProcess.writeStdin(ByteBuffer.wrap(
                    invokingCommand.toByteArray(charset)
                    )
                )
            }
        }
    }
    private fun stdout(chars: CharBuffer) {
        chars.lineSequence().forEach {
            onLineOut(it)
        }
    }
    private fun stderr(chars: CharBuffer) {
        chars.lineSequence().forEach {
            onLineErr(it)
        }
    }

    override suspend fun running(): CommandResult {
        debug("building runner")
        require(process!=null) {
            "process is not even running"
        }
        debug("runner","await")
//        var isRunningStill = true
//        launch {
//            var i=0
//            while (isRunningStill) {
//                kotlin.io.println("running${i++}")
//                delay(1000)
//            }
//        }
        process!!.waitFor(0,TimeUnit.NANOSECONDS)
//        isRunningStill = false
        this@NuShell.debug("block returning")
        return result!!
    }
    companion object {

        private val defaultPrefix:Array<String>
                = arrayOf("cmd","/c")
        private val keepCLIPrefix: Array<String>
                = arrayOf("cmd","/k","echo off")
        private val defaultCharset: Charset
                = charset("GBK")
        @Suppress("NAME_SHADOWING")
        operator fun invoke  (
            vararg commandLines:String,
            globalArg:Map<String,String>?=null,
            isMixingMessage: Boolean = false,
            isKeepCLIAndWrite: Boolean = false,// keep cmd maybe (
            startWithCreate: Boolean = true,
            charset: Charset = defaultCharset,
            workdir: Path? = null,
            prefix: Array<String> = defaultPrefix
        ):NuShell {

//            val id = getNewId()
//            fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
//            require(commandLines.isNotEmpty()) {
//                "unless one command"
//            }
            val prefix = if (isKeepCLIAndWrite) keepCLIPrefix else
                prefix + commandLines.run {
                    if (size == 1) first()
                    else commandLines.joinToString(" && ")
                }
            // Log
//            println("new command",
//                (if (isKeepCLIAndWrite) prefix.joinToString(" && ") else prefix.joinToString(" ")+" "+commandLines.joinToString(" && "))
//            )
            return  NuShell(prefix=prefix, env = globalArg, isMixingMessage=isMixingMessage, isEcho = false, startWithCreate = startWithCreate, charset = charset, workdir = workdir) {
                if (!isKeepCLIAndWrite) commandLines.forEach(this::run)
            }
        }
    }
}
