package me.heizi.kotlinx.shell

import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.logger.debug
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.intercepted
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

@OptIn(InternalCoroutinesApi::class)
class ReOpenNuShell(override val coroutineContext: CoroutineContext, ):
    AbstractCoroutine<CommandResult>(coroutineContext, false, false) {
    override fun onStart() {
        super.onStart()
    }
    inner class MuHandler

}

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
    private val charset: Charset = defaultCharset,
    private val onRun: suspend RunScope.() -> Unit,
): AbstractKShell(coroutineContext, prefix, env, isMixingMessage, isEcho, startWithCreate, id, charset, onRun) {

    private var result:CommandResult? = null

    private val process by lazy {
        runCatching {
            debug("runner","building")
            NuProcessBuilder(nuHandler,*prefix).run {
                env?.let { environment().putAll(it) }
                start().also { println("runner","built") }
            }
        }.onFailure {e ->
            fun endWithError(reason:String,code:Int = -1) = runBlocking {
                emit(ProcessingResults.Error(reason))
                emit(ProcessingResults.CODE(code))
                close()
            }
            when {
                e is IOException && e.message!=null ->{
                    println("catch IO exception \n $e")
                    val msg = e.message!!
                    when {
                        msg.matches(exceptionRegex) -> exceptionRegex.find(msg)!!.groupValues.let {
                            endWithError(it[2],it[1].toInt())
                        }
                        //["cannot run xxxx","114514,message"]
                        "error=" in msg -> msg.split("error=")[1].split(",").let {
                            //["114514","message"]
                            endWithError(it[1],it[0].toInt())
                        }
                        else -> null
                    }
                } else -> null
            } ?: endWithError(e.message?:e.toString(),)
        }.getOrNull()
    }
    private val nuHandler by lazy {
        create()
    }
    private val block:suspend CoroutineScope.()->CommandResult = {
        debug("building runner")
        require(process!=null) {
            "process is not even running"
        }
        debug("runner","await")
        var isRunningStill = true
        launch {
            var i=0
            while (isRunningStill) {
                kotlin.io.println("running${i++}")
                delay(1000)
            }
        }
        process!!.waitFor(0,TimeUnit.NANOSECONDS)
        isRunningStill = false
        close()
        this@NuShell.debug("block returning")
        result!!
    }
    private val continuation = block.createCoroutineUnintercepted(this, this)

    private val collectors = arrayListOf<FlowCollector<ProcessingResults>>()

    private val replayCache: ArrayList<ProcessingResults> = arrayListOf()


    private suspend fun close() {
        process?.run {
            if (isRunning) {
                waitFor(0,TimeUnit.NANOSECONDS)
                destroy(false)
            }
        }
        debug("all closed")
        emit(ProcessingResults.Closed)
        debug("emit closed")
        cancel()
    }

    override fun onStart() {
        try {
            continuation.intercepted().resumeCancellableWith(Result.success(Unit))
            debug("resumed")
        }catch (e:Exception) {
            error("$e")
        }
    }

    init {
        if (startWithCreate) start()
    }

    private suspend fun emit(processingResults: ProcessingResults) {
        replayCache.add(processingResults)
        for (collector in collectors) {
            collector.emit(processingResults)
        }
        if (processingResults is ProcessingResults.Closed) {
            result = replayCache.toResult(id)
        }
    }

    private val newIOContext get() = coroutineContext.newCoroutineContext(IO)

    private fun create()  = object : NuAbstractCharsetHandler(charset) {

        val sb = StringBuilder()

        var nuProcess:NuProcess? = null

        init {
            launch {
                while (nuProcess==null) Unit
                val runner = object : RunScope {
                    override fun run(command: String) {
                        sb.append(command)
                        nuProcess!!.wantWrite()
                    }
                }
                onRun(runner)
                runner.run("\n\nexit")
            }
        }

        override fun onStart(nuProcess: NuProcess) {
            this.nuProcess = nuProcess
        }
        override fun onExit(exitCode: Int) { runBlocking {
            emit(ProcessingResults.CODE(exitCode))
            close()
        } }
        override fun onStdinCharsReady(buffer: CharBuffer): Boolean = runBlocking {
            var command:String = sb.toString()
            sb.clear()
            command = if (isEcho) "echo \"$command\"\n$command\n" else "$command\n"
            command += "\n"
            this@NuShell.debug("stdin",command.split("\n"))
            buffer.put(command)
            buffer.flip()
            false
        }
        fun emit(buffer: CharBuffer,isError: Boolean = false) = runBlocking {
            emit(buildString {
                while (buffer.hasRemaining())
                    append(buffer.get())
            }.lines().filter{it.isNotBlank()}.joinToString("\n").also {
                if (isError) error("stderr",it) else println("stdout",it)
            }.let(if (!isMixingMessage && isError) ProcessingResults::Error else ProcessingResults::Message))
        }

        override fun onStderrChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult?)
                = emit(buffer,true)
        override fun onStdoutChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult?)
                = emit(buffer,false)
    }


    override suspend fun collect(collector: FlowCollector<ProcessingResults>) {
        debug("run: ${start()}")

        replayCache.forEach {
            collector.emit(it)
        }
        collectors.add(collector)
        while (result==null) delay(10)
    }

    override suspend fun await(): CommandResult {
        debug("waiting")
        while (result==null) delay(10)
        debug("awaited")
        return result!!
    }


    @ExperimentalCoroutinesApi
    override fun getCompleted(): CommandResult {
        return result!!
    }
    private val idS = "shell#${id}"
    private fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    private fun debug(vararg any: Any?) = idS.dddd("running", *any)
    private fun error(vararg any: Any?) = idS.eeee("running", *any)

    /**
     * FIXME:永远不可能修复了估计,来个大佬吧
     */

    @Deprecated("DONT USE IT", ReplaceWith("Nothings"),DeprecationLevel.ERROR)
    override val onAwait: SelectClause1<CommandResult> get() = TODO("Not yet implemented")


    companion object {

        private val defaultPrefix:Array<String>
                = arrayOf("cmd","/c")
        private val keepCLIPrefix: Array<String>
                = arrayOf("cmd","/k","echo off")
        private val defaultCharset: Charset
                = charset("GBK")
        /**
         * 用于匹配错误的Regex
         */
        private val exceptionRegex by lazy {
            "Cannot run program \".+\": error=(\\d+), (.+)"
                .toRegex()
        }

        /**
         * 假构造器
         *
         * @see NuShell
         * @param isKeepCLIAndWrite ture means launch the Non-Close-CLI first and write command lines after. it'll exit
         * while read the exit command on your [commandLines]. run on Prefix that joined commands by && sign if false
         */
//        @Deprecated("will remove on next version cuz multiplatform supporting")
        @Suppress("NAME_SHADOWING")
        operator fun invoke  (
            vararg commandLines:String,
            globalArg:Map<String,String>?=null,
            isMixingMessage: Boolean = false,
            isKeepCLIAndWrite: Boolean = false,// keep cmd maybe (
            startWithCreate: Boolean = true,
            charset: Charset = defaultCharset,
            prefix: Array<String> = defaultPrefix
        ):NuShell {
            val id = getNewId()
            fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
            require(commandLines.isNotEmpty()) {
                "unless one command"
            }
            val prefix = if (isKeepCLIAndWrite) keepCLIPrefix else
                prefix + commandLines.run {
                    if (size == 1) first()
                    else commandLines.joinToString(" && ")
                }
            // Log
            println("new command",
                (if (isKeepCLIAndWrite) prefix.joinToString(" && ") else prefix.joinToString(" ")+" "+commandLines.joinToString(" && "))
            )
            return  NuShell(prefix=prefix, env = globalArg, isMixingMessage=isMixingMessage, isEcho = false, startWithCreate = startWithCreate, id = id, charset = charset) {
                if (!isKeepCLIAndWrite) commandLines.forEach(this::run)
            }
        }
    }
}
