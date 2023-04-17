
package me.heizi.kotlinx.shell

import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * ## NuShell - NuProcess的封装类
 * Create and run a [NuProcess] by command line , that's Shell no matter is Windows call it or not .
 * This class implemented [Deferred] asynchronous coroutine and [SharedFlow] ,
 * That means you can use await to wait for [CommandResult]  or collect [ProcessingResults].
 *
 * I recommend the fake constructor if you just want to **run a simple command**
 * @see KuShell.invoke
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

class NuShell(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    forest: Array<String> = emptyArray(),
    environment: Map<String, String>? = null,
    workdir: Path? = null,
    active: Boolean = true,
    charset: Charset = defaultCharset,
    isRedirect: Boolean = false,
    stdIn: (suspend WriteScope.() -> Unit)?=null
): AbstractKShell(coroutineContext, forest, environment, workdir?.toFile(), active, charset, isRedirect, commandWriter = stdIn)
{
    private val process by lazy {
        debug("runner","building")
        handled { NuProcessBuilder().run {
            setProcessListener(nuHandler)
            command().addAll(forest)
            environment?.let { environment().putAll(it) }
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
    private fun stdin(nuProcess: NuProcess) = launch {
        debug("stdin","building")
        commandWriter?.invoke(object : WriteScope { override fun write(string: String) {
            debug("writing",string)
            nuProcess.writeStdin(ByteBuffer.wrap(
                string.toByteArray(charset)
            ))
        } })
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

    override suspend fun create() {

    }

    override suspend fun CoroutineScope.running(): CommandResult {
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

    init {
        if (active) {
            start()

        }
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

            val prefix = if (isKeepCLIAndWrite) keepCLIPrefix else
                prefix + commandLines.run {
                    if (size == 1) first()
                    else commandLines.joinToString(" && ")
                }
            return NuShell(
                forest = prefix,
                environment = globalArg,
                isRedirect = isMixingMessage,
                charset = charset,
                workdir = workdir,
                active = startWithCreate,
            ) {
                if (!isKeepCLIAndWrite) commandLines.forEach(this::echoRun)
            }
        }
    }
}
