package me.heizi.kotlinx.shell

import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderResult
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.intercepted
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

//suspend fun shell() = coroutineScope {
//    channelFlow<ProcessingResults> {
//
//    }
//    Unit
//}

@OptIn(InternalCoroutinesApi::class)
class ReOpenNuShell(
    private val prefix: Array<String> = defaultPrefix,
    coroutineContext: CoroutineContext = Dispatchers.IO,
    private val coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
    val id: Int = AbstractKShell.getNewId(),
    val charset: Charset = defaultCharset,
    private val env: Map<String, String>? = null,
    val onRun: suspend RunScope.() -> Unit
): AbstractCoroutine<CommandResult>(coroutineContext, false, false),
    Flow<ProcessingResults>
{
    private var result:CommandResult? = null
    override val coroutineContext = super.coroutineContext+ CoroutineName("shell-worker#$id")
    val process by lazy {
        runCatching {
            debug("runner","building")
            NuProcessBuilder(MuHandler(),*prefix).run {
                env?.let { environment().putAll(it) }
                start().also { println("runner","built") }
            }
        }   .onFailure(::whenError)
            .onSuccess {
                debug("runner","built")
            }.getOrNull()!!
    }

    override fun onStart() = start(coroutineStart,Unit) {
        process
        debug("runner","await")
        process.runCatching {
            val code = waitFor(0, TimeUnit.NANOSECONDS)
            debug("runner","code",code)
            emit(ProcessingResults.CODE(code))
            debug("runner","destroy")
            destroy(false)
        }
        emit(ProcessingResults.Closed)
        debug("runner","closed")
        replayCache.toResult(id)
    }
    private val replayCache: ArrayList<ProcessingResults> = arrayListOf()
    private val collectors = arrayListOf<FlowCollector<ProcessingResults>>()
    private suspend fun emit(processingResults: ProcessingResults) {
        replayCache.add(processingResults)
        for (collector in collectors) {
            collector.emit(processingResults)
        }
        if (processingResults is ProcessingResults.Closed) {
            result = replayCache.toResult(id)
        }
    }
    override suspend fun collect(collector: FlowCollector<ProcessingResults>) {
        debug("run: ${start()}")

        replayCache.forEach {
            collector.emit(it)
        }
        collectors.add(collector)
        while (result==null) delay(10)
    }

    private fun whenError(e:Throwable?) {
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
        } ?: endWithError(e)
    }
    private fun endWithError(reason:String,code:Int = -1,) = runBlocking {
        emit(ProcessingResults.Error(reason))
        emit(ProcessingResults.CODE(code))
        cancel(reason)
        if (isActive) throw CancellationException("already cancel")
    }
    private fun endWithError(e:Throwable?) = runBlocking {
        emit(ProcessingResults.Error(e?.message.toString()))
        emit(ProcessingResults.CODE(-1))
        cancel("unknowing exception", e)
        if (isActive) throw CancellationException("already cancel")
    }
    inner class MuHandler: NuAbstractCharsetHandler(charset) {
        val sb = StringBuilder()

        var nuProcess: NuProcess? = null

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
        override fun onExit(exitCode: Int) {
            runBlocking {
                emit(ProcessingResults.CODE(exitCode))
            }
        }
        override fun onStdinCharsReady(buffer: CharBuffer): Boolean = runBlocking {
            var command: String = sb.toString()
            sb.clear()
//            command = if (isEcho) "echo \"$command\"\n$command\n" else "$command\n"
            command += "\n"
            this@ReOpenNuShell.debug("stdin", command.split("\n"))
            buffer.put(command)
            buffer.flip()
            false
        }
        private fun emit(buffer: CharBuffer, isError: Boolean = false) = runBlocking {
            emit(buildString {
                while (buffer.hasRemaining())
                    append(buffer.get())
            }.lines().filter { it.isNotBlank() }.joinToString("\n").also {
                if (isError) error("stderr", it) else println("stdout", it)
            }.let(if (isError) ProcessingResults::Error else ProcessingResults::Message))
        }

        override fun onStderrChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult?)
                = emit(buffer,true)
        override fun onStdoutChars(buffer: CharBuffer, closed: Boolean, coderResult: CoderResult?)
                = emit(buffer,false)
    }
    private val idS = "shell#${id}"
    private fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    private fun debug(vararg any: Any?) = idS.dddd("running", *any)
    private fun error(vararg any: Any?) = idS.eeee("running", *any)


    companion object {
        private val exceptionRegex by lazy {
            "Cannot run program \".+\": error=(\\d+), (.+)"
                .toRegex()
        }
        private val defaultPrefix: Array<String> = arrayOf("cmd", "/c")
        private val keepCLIPrefix: Array<String> = arrayOf("cmd", "/k", "echo off")
        private val defaultCharset: Charset = charset("GBK")
    }
}