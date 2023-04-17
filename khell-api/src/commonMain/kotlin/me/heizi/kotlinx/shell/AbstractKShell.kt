package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

@Deprecated("This class is deprecated and will be removed in the future", ReplaceWith("ReShell"), DeprecationLevel.ERROR)
@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
abstract class AbstractKShell(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    protected val forest: Array<String> = emptyArray(),
    protected val environment: Map<String, String>? = null,
    protected val workdir: File? = null,
    active: Boolean = true,
    protected val charset: Charset,
    protected val isRedirect: Boolean = false,
    protected val id: Int = getNewId(),
    protected val commandWriter: (suspend WriteScope.() -> Unit)? = null,
): KShell,
//    CoroutineScope by CoroutineScope(coroutineContext+CoroutineName("shell-worker#$id")),
    AbstractCoroutine<CommandResult>(coroutineContext+CoroutineName("shell-worker#$id"), false, false) {

    // deprecated
    init {
        throw NotImplementedError("This class is deprecated and will be removed in the future")
    }

    protected abstract suspend fun create()

    protected open suspend fun CoroutineScope.running():CommandResult {
        throw NotImplementedError("You should override this method")
    }
    protected open suspend fun after() {}

//    private val block:suspend CoroutineScope.()->CommandResult by lazy { {
//        debug("lesumdo")
//        create()
//
//        running().also { result = it }
//    } }



    override fun onStart() {

    }
    protected var result:CommandResult? = null


    //log helper
    protected val idS = "shell#${id}"
    protected fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    protected fun debug(vararg any: Any?) = idS.dddd("running", *any)
    protected fun error(vararg any: Any?) = idS.eeee("running", *any)

    //flow helper
    protected fun close() { emit(ProcessingResults.Closed)}
    protected fun lastMsg(msg:String?, code:Int = -1) {
        msg?.let { emit(ProcessingResults.Error(it)) }
        emit(ProcessingResults.CODE(code))
        close()
    }
    //error helper
    protected inline fun <T:Any?> handled(crossinline block: () -> T?): T?
        = runCatching(block)
            .onFailure(::handlingProcessError)
            .getOrNull()
    protected fun handlingProcessError(error: Throwable) = when {
        error is IOException && error.message!=null -> {
            println("catch IO exception \n $error")
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
                    lastMsg(msg)
                }
            }
            close()
        }
        else -> {
            throw error
            lastMsg(error.toString())
        }
    }

    //output helper
    protected inline fun onLineOut(line:String) {
        debug("out", line)
        emit(ProcessingResults.Message(line))
    }
    protected inline fun onLineErr(line:String) {
        error("err", line)
        emit(ProcessingResults.Error(line))
    }
    protected val newIOContext get() = coroutineContext.newCoroutineContext(Dispatchers.IO)
    //flows
    protected val collectors = arrayListOf<FlowCollector<ProcessingResults>>()
    protected val replayCache: ArrayList<ProcessingResults> = arrayListOf()
    fun emit(processingResults: ProcessingResults) {
        debug("sending")
        replayCache.add(processingResults)
        for (collector in collectors) {
            launch {
                collector.emit(processingResults)
            }
        }
        if (processingResults is ProcessingResults.Closed) {
            debug("send done")
            result = replayCache.toResult(id)
            this.cancel()
        }
    }
    override suspend fun collect(collector: FlowCollector<Signal>) {
        debug("calling collecting")
        replayCache.forEach { collector.emit(it) }
        collectors.add(collector)
        while (result==null) delay(10)
    }
    init {
        if (active) {
            debug("calling start",start())
        }
        println("new command", *forest,)
    }

    companion object {
        @JvmStatic
        private var idMaker = 0
        @JvmStatic
//        protected
        fun getNewId(): Int
            = idMaker++
    }

    override suspend fun await(): CommandResult {
        debug("waiting")
        while (result==null) delay(10)
        debug("awaited")
        return result!!
    }
    /**
     * FIXME:永远不可能修复了估计,来个大佬吧
     */

    @Deprecated("DONT USE IT", ReplaceWith("Nothings"),DeprecationLevel.ERROR)
    override val onAwait: SelectClause1<CommandResult> get() = TODO("Not yet implemented")
    @ExperimentalCoroutinesApi
    override fun getCompleted(): CommandResult {
        return result!!
    }

}
