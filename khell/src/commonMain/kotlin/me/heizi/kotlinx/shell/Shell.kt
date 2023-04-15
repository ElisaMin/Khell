package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.intercepted
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp


/**
 * ## Shell - Process的封装类
 * Create and run a [Process] by command line , that's Shell no matter is Windows call it or not .
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
class Shell(
    coroutineContext: CoroutineContext= EmptyCoroutineContext,
    private val prefix: Array<String> = defaultPrefix,
    private val env: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    id: Int = getNewId(),
    private val charset: Charset = defaultCharset,
    private val workdir: File? = null,
    private val onRun: suspend RunScope.() -> Unit,
): AbstractKShell(coroutineContext, prefix, env, isMixingMessage, isEcho, startWithCreate, id, charset,workdir, onRun) {

    private val idS = "shell#${id}"
    private fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    private fun debug(vararg any: Any?) = idS.dddd("running", *any)
    private fun error(vararg any: Any?) = idS.eeee("running", *any)

    private fun create() = ProcessBuilder(*prefix).apply {
        if (isMixingMessage) this.redirectErrorStream(true)
        workdir?.let(this::directory)
        env?.takeIf { it.isNotEmpty() }?.let {
            val e = environment()
            e.putAll(it)
        }
    }.start()

    private suspend fun collectErrJob() = coroutineScope {
        launch(newIOContext) {
            debug("collecting err")
            stdErrRead(process!!.errorStream)
        }
    }
    private suspend fun collectOutJob() = coroutineScope {
        launch(newIOContext) {
            debug("collecting out")
            stdOutRead(process!!.inputStream)
        }
    }
    private suspend fun writeJob() = coroutineScope {
        launch(newIOContext) {
            process!!.outputStream.writer(charset).getDefaultRunScope(isEcho, id).let {
                debug("writing")
                onRun(it)
            }
        }
    }
    private suspend inline fun runJobInside() = coroutineScope {
        debug("building runner")
        require(process!=null) {
            "process is not even running"
        }
        debug("runner bullied")
        collectOutJob()
        //如果混合消息则直接跳过这次的collect
        if (!isMixingMessage) collectErrJob()
        writeJob().invokeOnCompletion {error->
            error?.let {
                debug("write job failed",it)
                process!!.outputStream.close()
            }
        }
        val code = withContext(IO) {
            process!!.waitFor()
        }
        debug("process exit with code $code")
//        this.coroutineContext.job.join()
        debug("all job joined")
        runCatching { process!!.destroy() }
        debug("process destroyed")
        emit(ProcessingResults.CODE(code))
        close()
        debug("emit closed")
        debug("run out")
    }

    private suspend fun lastMsg(msg:String?,code:Int = -1){
        msg?.let {
            emit(ProcessingResults.Error(it))
        }
        emit(ProcessingResults.CODE(code))
        close()
    }
    private suspend fun close()  {
        emit(ProcessingResults.Closed)
    }

    private suspend inline fun read(stream:InputStream,crossinline onLine:suspend (String)->Unit) {
//        debug("reading")
        stream.bufferedReader(charset).useLines { it.forEach { line ->
            onLine(line)
        } }
    }
    private suspend fun stdErrRead(stream:InputStream) = read(stream) {
        error("failed", it)
        emit(ProcessingResults.Error(it))
    }
    private suspend fun stdOutRead(stream:InputStream) = read(stream) {
        debug("message", it)
        emit(ProcessingResults.Message(it))
    }
    private suspend fun handlingProcessError(error: Throwable) = when {
        error is IOException && error.message!=null -> {
            println("catch IO exception \n $error")
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
                    lastMsg(msg)
                }
            }
            close()
        }
        else -> {
            lastMsg(error.toString())
        }
    }
    private val process by lazy {
        runCatching {
            create()
        }.onFailure { runBlocking {
            handlingProcessError(it)
        } }
            .getOrNull()
    }



    private val block:suspend CoroutineScope.()->CommandResult = {
        runJobInside()
        debug("block returning")
        result!!
    }
    private val continuation = block.createCoroutineUnintercepted(this, this)
    private val collectors = arrayListOf<FlowCollector<ProcessingResults>>()
    private var result:CommandResult? = null

    private val replayCache: ArrayList<ProcessingResults> = arrayListOf()

    override fun onStart() {
        try {
            continuation.intercepted().resumeCancellableWith(Result.success(Unit))
            debug("resumed")
        }catch (e:Exception) {
            runBlocking {
                handlingProcessError(e)
            }
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

    /**
     * FIXME:永远不可能修复了估计,来个大佬吧
     */

    @Deprecated("DONT USE IT", ReplaceWith("Nothings"))
    override val onAwait: SelectClause1<CommandResult> get() = TODO("Not yet implemented")


    companion object {

        /**
         * 用于匹配错误的Regex
         */
        internal val exceptionRegex by lazy {
            "Cannot run program \".+\": error=(\\d+), (.+)"
                .toRegex()
        }

        /**
         * 假构造器
         *
         * @see Shell
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
        ):Shell {
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
            return  Shell(prefix=prefix, env = globalArg, isMixingMessage=isMixingMessage, isEcho = false, startWithCreate = startWithCreate, id = id, charset = charset) {
                if (!isKeepCLIAndWrite) commandLines.forEach(this::run)
            }
        }
    }
}
