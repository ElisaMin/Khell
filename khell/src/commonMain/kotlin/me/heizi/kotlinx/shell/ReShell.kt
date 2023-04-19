@file:Suppress("unused", "NOTHING_TO_INLINE", "FunctionName", "MemberVisibilityCanBePrivate",)
package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.logger.error
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp


@RequiresOptIn(
    message = "This API is experimental and may be changed in the future.",
    level = RequiresOptIn.Level.WARNING
)
annotation class ExperimentalApiReShell

/**
 * A shell that can be used to execute commands
 *
 * @param forest the prefix of command
 * @param charset the charset of command
 * @param coroutineContext the coroutine context of shell
 * @param environment the environment of shell
 * @param workdir the workdir of shell
 * @param isRedirect whether redirect the output of shell
 * @param signalBuffer the buffer size of signal
 * @param flow signals, use by default please
 * @param coroutineStart the coroutine start of shell
 * @param stdin dsl the stdin of shell
 */
@ExperimentalApiReShell
internal suspend fun buildReShellFlow(
    forest: CommandPrefix = defaultPrefix,
    charset: Charset = defaultCharset,
    coroutineContext: CoroutineContext = IO,
    environment: Map<String, String>? = null,
    workdir: File? = null,
    isRedirect: Boolean = false,
    signalBuffer:Int = 1024,
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(replay = signalBuffer, extraBufferCapacity = 1024),
    coroutineStart: CoroutineStart = CoroutineStart.LAZY,
    stdin:(suspend WriteScope.() -> Unit)? = null,
)= coroutineScope {
    val job = launch(coroutineContext, coroutineStart) {
        val shell = buildReShell(flow = flow, forest = forest, charset = charset, environment = environment, workdir = workdir, isRedirect = isRedirect, stdin = stdin)!!
        shell.debug("shell","built")
    }
    flow.closable().onStart { job.invokeOnCompletion { cause ->
        if (cause != null && cause!is CancellationException) launch {
            handlingProcessError(cause,::lastMsg)
        }
    }
        job.start()
    }.onCompletion {
        job.cancel()
    }
}

@JvmInline
@ExperimentalApiReShell
value class ReShell internal constructor(
    internal val process: Process,
) { companion object {
    @ExperimentalApiReShell
    operator fun invoke(
        forest: CommandPrefix = defaultPrefix,
        charset: Charset = defaultCharset,
        coroutineContext: CoroutineContext = IO,
        environment: Map<String, String>? = null,
        workdir: File? = null,
        isRedirect: Boolean = false,
        signalBuffer: Int = 1024,
        flow: MutableSharedFlow<Signal> = MutableSharedFlow(replay = signalBuffer, extraBufferCapacity = 1024),
        coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
        stdin:(suspend WriteScope.() -> Unit)? = null,
    ):KShell = ReKShell(
        forest = forest,
        charset = charset,
        coroutineContext = coroutineContext,
        environment = environment,
        workdir = workdir,
        isRedirect = isRedirect,
        signalBuffer = signalBuffer,
        coroutineStart = coroutineStart,
        flow = flow,
        stdin = stdin,
    )
    @ExperimentalApiReShell
    suspend operator fun invoke(
        forest: CommandPrefix = defaultPrefix,
        charset: Charset = defaultCharset,
        coroutineContext: CoroutineContext = IO,
        environment: Map<String, String>? = null,
        workdir: File? = null,
        isRedirect: Boolean = false,
        signalBuffer: Int = 1024,
        coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
        stdin:(suspend WriteScope.() -> Unit)? = null,
    ) = buildReShellFlow(
        forest = forest,
        charset = charset,
        coroutineContext = coroutineContext,
        environment = environment,
        workdir = workdir,
        isRedirect = isRedirect,
        signalBuffer = signalBuffer,
        coroutineStart = coroutineStart,
        stdin = stdin,
    )
    @ExperimentalApiReShell
    suspend operator fun invoke(
        vararg commands:String,
        coroutineContext: CoroutineContext = IO,
        coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
        charset: Charset = defaultCharset,
        environment: Map<String, String>? = null,
        workdir: File? = null,
        isRedirect: Boolean = false,
        forest: CommandPrefix = defaultPrefix,
    ) = invoke(
        forest = forest+commands,
        charset = charset,
        coroutineContext = coroutineContext,
        environment = environment,
        workdir = workdir,
        isRedirect = isRedirect,
        coroutineStart = coroutineStart,
        stdin = null,
    )

} }


suspend fun Flow<Signal>.await():CommandResult =
    toList().asSequence().toResult()

/**
 * package as KShell, [Deferred] async result type as [CommandResult], mixing [Flow] as [Signal] .
 *
 * preferred to use await instead @see Flow<Signal>.await
 */
@ExperimentalApiReShell
internal inline fun ReKShell(
    forest: CommandPrefix = defaultPrefix,
    charset: Charset = defaultCharset,
    coroutineContext: CoroutineContext = IO,
    environment: Map<String, String>? = null,
    workdir: File? = null,
    isRedirect: Boolean = false,
    signalBuffer:Int = 1024,
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(replay = signalBuffer, extraBufferCapacity = 1024),
    coroutineStart: CoroutineStart = CoroutineStart.LAZY,
    noinline stdin:(suspend WriteScope.() -> Unit)? = null,
):KShell {

    val async = KShell.async(coroutineContext,coroutineStart) {
        buildReShellFlow(forest = forest, charset = charset, coroutineContext = coroutineContext, environment = environment, workdir = workdir, isRedirect = isRedirect, signalBuffer = signalBuffer, coroutineStart = coroutineStart, stdin = stdin, flow = flow)
            .await()
    }
    async.invokeOnCompletion { cause ->
        if (cause != null && cause !is CancellationException) KShell.launch {
            handlingProcessError(cause,flow::lastMsg)
        }
    }
    return object :
        Deferred<CommandResult> by async,
        Flow<Signal> by flow.closable(),
        KShell {}
}


/**
 * create and catching a jvm Process singleton
 * and covert it just like flow return a cancelable flow
 */
@ExperimentalApiReShell
internal suspend fun buildReShell(
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(),
    forest: CommandPrefix = defaultPrefix,
    charset: Charset = defaultCharset,
    environment: Map<String, String>? = null,
    workdir: File? = null,
    isRedirect: Boolean = false,
    stdin:(suspend WriteScope.() -> Unit)? = null,
) = runCatching {
    "KShellBuilder".pppp(
        "building",forest,
        "charset" to charset,
        "redirect" to isRedirect
    )
    ReShell(
        ProcessBuilder(*forest)
            .directory(workdir)
            .apply {
                if (isRedirect) {
                    redirectErrorStream(true)
                }
                if (!environment.isNullOrEmpty()) {
                    environment().putAll(environment)
                }
            }
            .start()
    )
}.onSuccess {
    it.debug("start"," well")
}.onFailure { e ->
    e.error("start","error",e)
    handlingProcessError(e,flow::lastMsg)
}.getOrNull()?.running(flow,isRedirect, charset, stdin)

/**
 * std in and out 's all handling in here like the hub
 * it contends the waiting job ether
 */
@ExperimentalApiReShell
internal suspend inline fun ReShell.running(
    signals: MutableSharedFlow<Signal> = MutableSharedFlow(),
    isRedirected: Boolean,
    charset: Charset = defaultCharset,
    noinline stdin: (suspend WriteScope.() -> Unit)?
) = coroutineScope {
    debug("run", "ready")
    launch {
        stdin?.let { input(charset, it) }
    }
    launch {
        stdout(charset).collect { it:Signal -> signals.emit(it) }
    }
    if (!isRedirected) launch {
        stderr(charset).collect { it:Signal -> signals.emit(it) }
    }
    debug("wait for", "ready")
    waitFor().collect { signals.emit(it) }
    this@running
}
@ExperimentalApiReShell
internal inline fun ReShell.close() = runCatching {
    process.destroy()
}

/**
 * waiting for process death
 *
 * @return Flow<Signal>
 */
@ExperimentalApiReShell
internal inline fun ReShell.waitFor() = flow {
    debug("waitFor", "ready")
    val r = process.waitFor()
    emit(Signal.Code(r))
    debug("waitFor", "result", r)
    close()
    emit(Signal.Closed)
    debug("waitFor", "closed")
}.flowOn(IO)

/**
 * DSL writing to process
 *
 * suspend function to write in to process
 */
@ExperimentalApiReShell
internal suspend inline fun ReShell.input(
    charset: Charset = defaultCharset,
    crossinline block: suspend WriteScope.() -> Unit
) = process.outputStream.use { it.writer(charset).use { writer ->
    debug("input", "opened", writer)
    block(object : WriteScope {
        override fun write(string: String) {
            debug("input", "command", "writing", string.lines())
            writer.write(string)
            writer.flush()
        }
    })
    debug("input", "closing")
} }

//internal expect inline fun Process.stdin(block: (Writer)->Unit)
//internal expect inline fun Process.stderr(block: (String)->Unit)
//internal expect inline fun Process.stderr(block: (String)->Unit)
internal inline fun InputStream.useAsLinesFlow(charset: Charset) = flow {
    use { stream ->
        stream.bufferedReader(charset).useLines {
            it.forEach { s -> emit(s) }
        }
    }
}
/**
 * collecting stderr
 *
 * covert as flow
 */
@ExperimentalApiReShell
fun ReShell.stderr(charset: Charset = defaultCharset): Flow<Signal> {
    debug("stderr","ready")
    return process.errorStream
        .useAsLinesFlow(charset)
        .map(Signal::Error)
}

/**
 * collecting stdout
 *
 * covert as flow
 */
@ExperimentalApiReShell
fun ReShell.stdout(charset: Charset = defaultCharset):Flow<Signal> {
    debug("stdout","ready")
    return process.inputStream
        .useAsLinesFlow(charset)
        .map(Signal::Output)
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
                lastMsg(msg,null)
            }
        }
        lastMsg(msg,null)
    }
    else -> {
//        throw error
        lastMsg(error.toString(),null)
    }
}

// log
@ExperimentalApiReShell
internal expect inline fun ReShell.id():Number
@ExperimentalApiReShell
internal inline fun ReShell.debug(vararg any: Any?) = "KShell#${id()}".dddd(*any)
@ExperimentalApiReShell
internal inline fun ReShell.error(vararg any: Any?) = "KShell#${id()}".eeee(*any)
@ExperimentalApiReShell
internal inline fun ReShell.infos(vararg any: Any?) = "KShell#${id()}".pppp(*any)
typealias CommandPrefix = Array<String>

expect val defaultPrefix:CommandPrefix
expect val defaultCharset:Charset
expect val keepCLIPrefix:CommandPrefix

internal fun Flow<Signal>.closable() = takeWhile {
    it !is Signal.Closed
}