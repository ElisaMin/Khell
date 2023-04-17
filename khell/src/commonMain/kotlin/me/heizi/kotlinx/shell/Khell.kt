@file:Suppress("unused", "NOTHING_TO_INLINE", "FunctionName", "MemberVisibilityCanBePrivate",)
package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.logger.error
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

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
        val shell = buildReShell(
            flow = flow,
            forest = forest,
            charset = charset,
            environment = environment,
            workdir = workdir,
            isRedirect = isRedirect,
            stdin = stdin
        )!!
        shell.debug("shell","built")
    }
    flow.closable().onStart {
        job.invokeOnCompletion { cause ->
            if (cause != null && cause!is CancellationException) launch {
                handlingProcessError(cause,::lastMsg)
            }
        }
        job.start()
    }
}


@JvmInline
value class ReShell internal constructor(
    internal val process: Process,
) { companion object {

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

} }


suspend fun Flow<Signal>.await():CommandResult =
        toList().asSequence().toResult()

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
        buildReShellFlow(
            forest = forest,
            charset = charset,
            coroutineContext = coroutineContext,
            environment = environment,
            workdir = workdir,
            isRedirect = isRedirect,
            signalBuffer = signalBuffer,
            coroutineStart = coroutineStart,
            stdin = stdin,
            flow = flow
        ).await()
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

internal suspend inline fun ReShell.running(
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(),
    isRedirected: Boolean,
    charset: Charset = defaultCharset,
    noinline block: (suspend WriteScope.() -> Unit)?
) = coroutineScope {
    debug("run", "ready")
    launch {
        block?.let {
            input(charset, it)
        }
    }
    launch {
        stdout(charset).collect { flow.emit(it) }
    }
    if (!isRedirected) launch {
        stderr(charset).collect { flow.emit(it) }
    }
    debug("run", "waiting")
    waitFor().collect { flow.emit(it) }
    this@running
}

internal inline fun ReShell.close() = runCatching {
    process.destroy()
}
internal inline fun ReShell.waitFor() = flow {
    debug("waitFor", "ready")
    val r = process.waitFor()
    emit(Signal.Code(r))
    debug("waitFor", "result", r)
    close()
    emit(Signal.Closed)
    debug("waitFor", "closed")
}.flowOn(IO)

internal suspend inline fun ReShell.input(
    charset: Charset = defaultCharset,
    crossinline block: suspend WriteScope.() -> Unit
) = process.outputWriter(charset).use { writer ->
    debug("input","opened",writer)
    block(object : WriteScope {
        override fun write(string: String) {
            debug("input", "command","writing",string.lines())
            writer.write(string)
            writer.flush()
        }
    })
    debug("input","closing")
}

fun ReShell.stderr(charset: Charset = defaultCharset) = flow {
    debug("stderr","ready")
    process.errorReader(charset).useLines {
        it.forEach { s -> emit(s) }
    }
}.map(Signal::Error)
fun ReShell.stdout(charset: Charset = defaultCharset) = flow {
    debug("stdout","ready")
    process.inputReader(charset).useLines {
        it.forEach { s -> emit(s) }
    }
}.map(Signal::Output)

suspend inline fun FlowCollector<Signal>.lastMsg (msg:String?,code:Int?){
    msg?.let { s ->
        emit(Signal.Error(s))
    }
    emit(Signal.Code((code?:-1)))
    emit(Signal.Closed)
}
suspend fun handlingProcessError(
    error: Throwable,lastMsg:suspend (String?,Int?)->Unit,
) = when {
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
        throw error
        lastMsg(error.toString(),null)
    }
}


internal inline fun ReShell.id() = process.pid()
internal inline fun ReShell.debug(vararg any: Any?) = "KShell#${id()}".dddd(*any)
internal inline fun ReShell.error(vararg any: Any?) = "KShell#${id()}".eeee(*any)
internal inline fun ReShell.infos(vararg any: Any?) = "KShell#${id()}".pppp(*any)
typealias CommandPrefix = Array<String>

expect val defaultPrefix:CommandPrefix
expect val defaultCharset:Charset
expect val keepCLIPrefix:CommandPrefix

internal fun Flow<Signal>.closable() = takeWhile {
    it !is Signal.Closed
}