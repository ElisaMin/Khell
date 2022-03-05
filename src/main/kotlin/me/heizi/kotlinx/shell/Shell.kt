package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.logger.error
import me.heizi.kotlinx.logger.toString
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import me.heizi.kotlinx.shell.WriterRunScope.Companion.getDefaultRunScope
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.intercepted
import me.heizi.kotlinx.logger.debug as dddd
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
 * @param startWithCreate don't you can read the name ? idiot
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class Shell(
    coroutineContext: CoroutineContext= EmptyCoroutineContext,
    private val prefix: Array<String> = arrayOf("cmd", "/k"),
    private val env: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    private val onRun: suspend RunScope.() -> Unit,
): Flow<ProcessingResults>, AbstractCoroutine<CommandResult>(CoroutineScope(IO).newCoroutineContext(coroutineContext), false, false),Deferred<CommandResult> {

    private fun println(any: Any?) = "shell".pppp("running", any.toString())
    private fun debug(any: Any?) = "shell".dddd("running", any.toString())

    private val block:suspend CoroutineScope.()->CommandResult = {
        run()
        debug("block returning")
        result!!
    }
    private val continuation = block.createCoroutineUnintercepted(this, this)
    private val collectors = arrayListOf<FlowCollector<ProcessingResults>>()
    private var result:CommandResult? = null
//    override
    val replayCache: ArrayList<ProcessingResults> = arrayListOf()
    private val process by lazy {
        runCatching {
            ProcessBuilder(*prefix).run {
                environment().putAll(Khell.env)
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
            result = replayCache.toResult()
        }
    }


    private suspend fun run()  {
        debug("building runner")
        require(process!=null) {
            "process is not running even"
        }
        val process = this.process!!
        fun newIOContext() = coroutineContext+IO+EmptyCoroutineContext
        debug("runner bullied")

        val msgJob = launch(newIOContext()) {
            process.inputStream.bufferedReader(charset("GBK")).lineSequence().forEach {
                emit(ProcessingResults.Message(it))
                println("message", it)
            }
        }
        //如果混合消息则直接跳过这次的collect
        val errJob = if (!isMixingMessage) launch(newIOContext()) {
            process.errorStream.bufferedReader(charset("GBK")).lineSequence().forEach {
                emit(ProcessingResults.Error(it))
                "shell".error("failed", it)
            }
        } else null
        val writeJob = launch(newIOContext()) {
            process.outputStream.writer().getDefaultRunScope(isEcho).let {
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

    }

    override suspend fun await(): CommandResult {
        debug("waiting")
        while (result==null) {}
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
         * 假构造器
         *
         * @see Shell
         * @param runCommandOnPrefix options bout windows , true means using /c to execute connected commands by && ,
         * using /k launch a cmd and write command to stdin ,that's false.
         */
        operator fun invoke  (
            vararg commandLines:String,
            globalArg:Map<String,String>?=null,
            isMixingMessage: Boolean = false,
            runCommandOnPrefix: Boolean = false,
            startWithCreate: Boolean = true,
        ):Shell {
            require(commandLines.isNotEmpty()) {
                "unless one command"
            }
            val prefix =
                if (runCommandOnPrefix) arrayOf(
                    "cmd","/c",
                    if (commandLines.size == 1) commandLines[0]
                    else commandLines.joinToString(" && ")
                ) else arrayOf("cmd","/k", "@echo off", )
            //log
            println("new command",if (runCommandOnPrefix)
                prefix.joinToString(" && ")
             else
                prefix.joinToString(" ")+" "+commandLines.joinToString(" && ")
            )
            commandLines.forEach {
                println("commands",it)
            }
            return  Shell(prefix=prefix, env = globalArg, isMixingMessage=isMixingMessage, isEcho = false, startWithCreate = startWithCreate) {
                if (!runCommandOnPrefix) commandLines.forEach(this::run)
            }
        }

    }
}

