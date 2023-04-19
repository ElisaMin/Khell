@file:Suppress("unused","NOTHING_TO_INLINE")
package me.heizi.kotlinx.shell

import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.logger.debug
import me.heizi.kotlinx.logger.println
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

private typealias Signals = ProducerScope<Signal>

/**
 * ## NuShell - NuProcess的封装类
 * Create and run a [NuProcess] by command line , that's Shell no matter is Windows call it or not .
 * This class implemented [Deferred] asynchronous coroutine and [SharedFlow] ,
 * That means you can use await to wait for [CommandResult]  or collect [ProcessingResults].
 *
 * I recommend the fake constructor if you just want to **run a simple command**
 * @see NuShell.invoke
 *
 * @property forest how to start a process.
 * @property environment event args
 * @property isRedirect stderr redirect to stdout when true
 * @property stdin you can delay or something to using [WriteScope] run some fancy line.
 * @param coroutineContext I don't know what's it So I'm just added it.
 * @param charset make sure you won't see some fancy line you have never seen before
 * @param active for launch as start
 */
@Deprecated("not stable release")
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


//
//@JvmInline
//internal value class ReOpenNuShell(
//    val p: NuProcess
//) {
//    inline fun id() = p.pid
//    fun label() = "KShell#${p.pid}"
//    inline fun debug(vararg any: Any?) = label().dddd(*any)
//    inline fun error(vararg any: Any?) = label().eeee(*any)
//    inline fun infos(vararg any: Any?) = label().pppp(*any)
//}

        class ReOpenNuShellHandler(
            scope: Signals, charset : Charset = defaultCharset,
            val started:ReOpenNuShellHandler.() -> Unit = {}
        ):NuAbstractCharsetHandler(charset),Signals by scope {
            lateinit var process: NuProcess
            override fun onPreStart(nuProcess: NuProcess) {
                debug("lifecycle","onPreStart")
                this.process = nuProcess
            }
            override fun onStart(nuProcess: NuProcess) {
                debug("lifecycle","onStart")
                started()
            }
            override fun onExit(statusCode: Int) {
                debug("lifecycle","onExit",statusCode)
                lastMsg(null,statusCode)
                debug("canceling")
                cancel()
            }
            override fun onStdoutChars(buffer: CharBuffer?, closed: Boolean, coderResult: CoderResult?) {
                errorMsg(buffer.toString())
            }
            override fun onStderrChars(buffer: CharBuffer?, closed: Boolean, coderResult: CoderResult?) {
                output(buffer.toString())
            }
            val label by lazy {
                "KShell#${process.pid}"
            }
            private inline fun output(msg:String) = trySend(Signal.Output(msg))
            private inline fun errorMsg(msg:String) = trySend(Signal.Error(msg))
            inline fun debug(vararg any: Any?) = label.dddd(*any)
            inline fun error(vararg any: Any?) = label.eeee(*any)
            inline fun infos(vararg any: Any?) = label.pppp(*any)

        }



        private inline fun ReOpenNuShellHandler.writerByStart(
            charset: Charset = defaultCharset,
            context: CoroutineContext = Dispatchers.IO,
            noinline stdin: (suspend WriteScope.() -> Unit)?
        ) = launch(context,CoroutineStart.DEFAULT) {
            println("start","started",process.pid,process.isRunning)
            stdin?.invoke(object : WriteScope {
                override fun write(string: String) {
                    process.writeStdin(ByteBuffer.wrap(string.toByteArray(charset)))
                }
            })
        }
        suspend fun NuReShellBase(
            forest:Array<String> = emptyArray(),
            environment: Map<String, String>? = null,
            workdir: Path? = null,
            coroutineContext: CoroutineContext = Dispatchers.IO,
            charset: Charset = defaultCharset,
            stdin: (suspend WriteScope.() -> Unit)?=null
        ) = coroutineScope {
            NuProcessBuilder(*forest).runCatching {
                environment?.takeIf { it.isNotEmpty() }?.let {
                    environment().putAll(it)
                }
                workdir?.let(this::setCwd)
                callbackFlow {
                    runCatching {
                        ReOpenNuShellHandler(
                            this,charset,
                            started = { writerByStart(charset, coroutineContext, stdin) }
                        ).let {
                            setProcessListener(it)
                        }
                        awaitClose()
                    }.onFailure {
                        handlingProcessError(it) {e,c->
                            lastMsg(e, c?:-1)
                        }
                    }.getOrNull()
                }.onCompletion { e ->
                    if (e!=null) handlingProcessError(e) {m,c->
                        lastMsg(m, c?:-1)
                    }
                }.onStart {
                    start()
                }
            }
        }


        fun main() {
            println()
        }

        fun Signals.lastMsg(msg:String?=null, code: Int) {
            msg?.let(Signal::Error)?.let(this::trySend)
            trySend(Signal.Code(code))
            trySendBlocking(Signal.Closed)
        }
        /**
         * last message
         *
         * on error close before
         */
        suspend fun FlowCollector<Signal>.lastMsg (msg:String?,code:Int?){
            msg?.let { s ->
                emit(Signal.Error(s))
            }
            emit(Signal.Code((code?:-1)))
            emit(Signal.Closed)
        }

        /**
         * error handling
         *
         * catch Command Execute Error
         */
        suspend fun handlingProcessError(
            error: Throwable,lastMsg:suspend (String?,Int?)->Unit,
        ) = when {
            error is CancellationException -> Unit
            error is IOException && error.message!=null -> {
                kotlin.io.println("catch IO exception \n $error")
                val exceptionRegex = "Cannot run program \".+\": error=(\\d+), (.+)"
                    .toRegex()
                val msg = error.message!!
                when {
                    msg.matches(exceptionRegex) -> {
                        //["114514","message"]
                        exceptionRegex.find(msg)!!
                            .groupValues
                            .takeIf { it.size > 1 }
                            ?.let { lastMsg(it[2], it[1].toInt()) }

                    }
                    "error=" in msg -> {
                        //["cannot run xxxx","114514,message"]
                        msg.split("error=")[1]
                            .split(",")
                            .takeIf { it.size > 1 }
                            ?.let { lastMsg(it[2], it[1].toInt()) }
                    }
                    else -> {
                        lastMsg(msg,null)
                    }
                }
                lastMsg(msg,null)
            }
            else -> {
//                throw error
                lastMsg(error.toString(),null)
            }
        }
//fun FlowCollector<Signal>.lastMsg(msg:String?=null, code: Int?) = runBlocking {
//    msg?.let {
//        emit(Signal.Error(it))
//    }
//    emit(Signal.Code(code?:-1))
//    emit((Signal.Closed))
//}
    }
}

