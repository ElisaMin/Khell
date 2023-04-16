package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.internal.resumeCancellableWith
import kotlinx.coroutines.selects.SelectClause1
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.intercepted
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp



@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE", "NOTHING_TO_INLINE")
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
abstract class AbstractKShell(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val startCommand: Array<String>,
    private val environment: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    protected val id: Int = getNewId(),
    private val charset: Charset,
    private val workdir: File? = null,
    private val onRun: suspend RunScope.() -> Unit,
): KShell,
    CoroutineScope by CoroutineScope(coroutineContext+CoroutineName("shell-worker#$id")),
    AbstractCoroutine<CommandResult>(coroutineContext+CoroutineName("shell-worker#$id"), false, false)
{

    public constructor(
        vararg commandLines:String,
        environment: Map<String, String>?,
        isMixingMessage: Boolean = false,
        keepCLIPrefix: Array<String>? = arrayOf("cmd.exe", "/k","echo off"), // keep cmd maybe (
        startWithCreate: Boolean = true,
        charset: Charset = Charsets.UTF_8,
        workdir: File? = null,
        prefix: Array<String> = arrayOf("cmd.exe", "/c"),
    ):this(
        startCommand = kotlin.run {
            require(commandLines.isNotEmpty()) {
                "unless one command"
            }
            keepCLIPrefix ?: kotlin.run {
                prefix + commandLines.run {
                    if (size == 1) first()
                    else commandLines.joinToString(" && ")
                }
            }
        },onRun = {
            if (keepCLIPrefix!=null) commandLines.forEach(this::run)
        } , environment = environment,
        isMixingMessage=isMixingMessage,
        isEcho = false, startWithCreate = startWithCreate,
        charset = charset, workdir = workdir
    )

    private val block:suspend CoroutineScope.()->CommandResult = {
        running()
    }
    protected abstract suspend fun running():CommandResult

    private val continuation by lazy {
        block.createCoroutineUnintercepted(this, this)
    }
    override fun onStart() {
        try {
            continuation.intercepted().resumeCancellableWith(Result.success(Unit))
            debug("resumed")
        }catch (e:Exception) {
            handlingProcessError(e)
        }
    }
    protected var result:CommandResult? = null
    protected val idS = "shell#${id}"
    protected fun println(vararg any: Any?) = "shell#${id}".pppp("running",*any)
    protected fun debug(vararg any: Any?) = idS.dddd("running", *any)
    protected fun error(vararg any: Any?) = idS.eeee("running", *any)

    protected fun lastMsg(msg:String?, code:Int = -1) {
        msg?.let {
            emit(ProcessingResults.Error(it))
        }
        emit(ProcessingResults.CODE(code))
        close()
    }
    protected fun close()  {
        emit(ProcessingResults.Closed)
    }
    protected inline fun <T:Any?> handled(crossinline block: () -> T?): T? = try {
        block()
    } catch (e: Throwable) {
        runBlocking {
            handlingProcessError(e)
        }
        null
    }
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

    protected inline fun onLineOut(line:String) {
        debug("out", line)
        emit(ProcessingResults.Message(line))
    }
    protected inline fun onLineErr(line:String) {
        error("err", line)
        emit(ProcessingResults.Error(line))
    }

    protected val newIOContext get() = coroutineContext.newCoroutineContext(Dispatchers.IO)

    protected val collectors = arrayListOf<FlowCollector<ProcessingResults>>()

    protected val replayCache: ArrayList<ProcessingResults> = arrayListOf()

    fun emit(processingResults: ProcessingResults) {
        replayCache.add(processingResults)
        for (collector in collectors) {
            launch {
                collector.emit(processingResults)
            }
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
    init {
        if (startWithCreate) start()
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
