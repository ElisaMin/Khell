package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.IOException
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
    override val id: Int = idMaker++,
    private val charset: Charset = defaultCharset,
    private val onRun: suspend RunScope.() -> Unit,
): AbstractKShell(coroutineContext, prefix, env, isMixingMessage, isEcho, startWithCreate, id, charset, onRun) {

    private val idS = "shell#${id}"
    private fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    private fun debug(vararg any: Any?) = idS.dddd("running", *any)
    private fun error(vararg any: Any?) = idS.eeee("running", *any)

    private val block:suspend CoroutineScope.()->CommandResult = {
        run()
        debug("block returning")
        result!!
    }
    private val continuation = block.createCoroutineUnintercepted(this, this)
    private val collectors = arrayListOf<FlowCollector<ProcessingResults>>()
    private var result:CommandResult? = null

    private val replayCache: ArrayList<ProcessingResults> = arrayListOf()
    private val process by lazy {
        runCatching {
            ProcessBuilder(*prefix).run {
//                environment().putAll(Khell.env)
                env?.let { environment().putAll(it) }
                if (isMixingMessage) this.redirectErrorStream(true)
                start()
            }
        }.onFailure { e->
            when {
                e is IOException && e.message!=null ->{
                    println("catch IO exception \n $e")
                    val msg = e.message!!
                    when {
                        msg.matches(exceptionRegex) -> {
                            runBlocking {
                                exceptionRegex.find(msg)!!.groupValues.let {
                                    emit(ProcessingResults.Error(it[2]))
                                    emit(ProcessingResults.CODE(it[1].toInt()))
                                    emit(ProcessingResults.Closed)
                                }
                            }
                        }
                        "error=" in msg -> {
                            //["cannot run xxxx","114514,message"]
                            msg.split("error=")[1].split(",").let {

                                runBlocking {
                                    //["114514","message"]
                                    emit(ProcessingResults.Error(it[1]))
                                    emit(ProcessingResults.CODE(it[0].toInt()))
                                    emit(ProcessingResults.Closed)
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }.getOrNull()
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

    private val newIOContext get() = coroutineContext+IO+EmptyCoroutineContext

    private suspend inline fun run()  {
        debug("building runner")
        require(process!=null) {
            "process is not even running"
        }

        val process = this.process!!
        debug("runner bullied")

        val msgJob = launch(newIOContext) {
            process.inputStream.bufferedReader(charset).lineSequence().forEach {
                emit(ProcessingResults.Message(it))
                println("message", it)
            }
        }
        //如果混合消息则直接跳过这次的collect
        val errJob = if (!isMixingMessage) launch(newIOContext) {
            process.errorStream.bufferedReader(charset).lineSequence().forEach {
                emit(ProcessingResults.Error(it))
                error("failed", it)
            }
        } else null
        val writeJob = launch(newIOContext) {
            process.outputStream.writer(charset).getDefaultRunScope(isEcho,id).let {
                debug("writing")
                onRun(it)
            }
        }
        writeJob.invokeOnCompletion {
            process.outputStream.runCatching { close() }
        }
        launch (IO) {
            writeJob.join()
            errJob?.join()
            msgJob.join()
            emit(ProcessingResults.CODE(process.waitFor()))
            println("exiting")
            process.runCatching {
                inputStream.close()
                errorStream.close()
                destroy()
            }.onFailure {
                debug(it)
            }
            debug("all closed")
            emit(ProcessingResults.Closed)
            debug("emit closed")
        }.join()
        debug("run out")
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

        var idMaker = 0
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
            val id = idMaker++
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

