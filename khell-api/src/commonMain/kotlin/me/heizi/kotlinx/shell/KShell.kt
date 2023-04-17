@file:Suppress("unused", "NOTHING_TO_INLINE", "FunctionName", "MemberVisibilityCanBePrivate",)
package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

interface KShell: Flow<ProcessingResults>, Deferred<CommandResult> {
    companion object:CoroutineScope {
        @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
        override val coroutineContext: CoroutineContext = SupervisorJob() + CoroutineName("KShell")

    }
}

fun Flow<Signal>.closable() =
    takeWhile {
        it !is Signal.Closed
    }
//fun ReShell.input(
//    charset: Charset = Charset.defaultCharset(),
//    block: suspend WriteScope.() -> Unit
//) = process.outputWriter(charset).use {
//    block(it.asCommandWriter())
//}
@JvmInline
value class ReShell(
    internal val process: Process,
) {
    companion object {
        operator fun invoke(
            vararg commands: String,
            charset: Charset = Charset.defaultCharset(),
            forest: Array<String> = emptyArray(),
            environment: Map<String, String>? = null,
            workdir: File? = null,
            isRedirect: Boolean = false,
        ) {

        }
    }
}

fun ReKShell(
    charset: Charset = Charset.defaultCharset(),
    forest: Array<String> = emptyArray(),
    environment: Map<String, String>? = null,
    workdir: File? = null,
    isRedirect: Boolean = false,
    stdin:(suspend WriteScope.() -> Unit)? = null,
):KShell {
    val flow = MutableSharedFlow<Signal>()
    val async = KShell.async(Dispatchers.IO) {
        val shell = buildReShell(
            flow = flow,
            forest = forest,
            charset = charset,
            environment = environment,
            workdir = workdir,
            isRedirect = isRedirect,
            stdin = stdin
        ).id()
        flow
            .closable()
            .toList()
            .toResult(shell.toInt())
    }
    return object :
        Deferred<CommandResult> by async,
        Flow<ProcessingResults> by flow.asSharedFlow(),
        KShell {}
}
internal suspend fun buildReShell(
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(),
    forest: Array<String> = emptyArray(),
    charset: Charset = Charset.defaultCharset(),
    environment: Map<String, String>? = null,
    workdir: File? = null,
    isRedirect: Boolean = false,
    stdin:(suspend WriteScope.() -> Unit)? = null,
) = ReShell(
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
).running(flow,isRedirect, charset, stdin)

internal suspend inline fun ReShell.running(
    flow: MutableSharedFlow<Signal> = MutableSharedFlow(),
    isRedirected: Boolean,
    charset: Charset = Charset.defaultCharset(),
    noinline block: (suspend WriteScope.() -> Unit)?
) = coroutineScope {
    debug("running", "ready")
    launch {
        block?.let {
            debug("running", "input")
            input(charset, it)
        }
        debug("running", "waiting")
        waitFor().collect { flow.emit(it) }
    }
    launch {
        debug("running", "output")
        stdout(charset).collect { flow.emit(it) }
    }
    if (!isRedirected) launch {
        debug("running", "error")
        stderr(charset).collect { flow.emit(it) }
    }
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
}.flowOn(Dispatchers.IO)

internal suspend inline fun ReShell.input(
    charset: Charset = Charset.defaultCharset(),
    crossinline block: suspend WriteScope.() -> Unit
) = process.outputWriter(charset).use {
    block(it.asCommandWriter())
}

fun ReShell.stderr(charset: Charset = Charset.defaultCharset()) = flow {
    debug("stderr","ready")
    process.errorReader(charset).useLines {
        it.forEach { s -> emit(s) }
    }
}.map(Signal::Error)
fun ReShell.stdout(charset: Charset = Charset.defaultCharset()) = flow {
    debug("stdout","ready")
    process.inputReader(charset).useLines {
        it.forEach { s -> emit(s) }
    }
}.map(Signal::Output)


internal inline fun ReShell.id() = process.pid()
internal inline fun ReShell.debug(vararg any: Any?) = "KShell${id()}".dddd(any)
internal inline fun ReShell.error(vararg any: Any?) = "KShell${id()}".eeee(any)
internal inline fun ReShell.infos(vararg any: Any?) = "KShell${id()}".pppp(any)

sealed interface Signal:ProcessingResults {
    @JvmInline
    value class Error(val message:String):Signal {
        override fun toString(): String = "Error(message='$message')"
    }
    @JvmInline
    value class Output(val message:String):Signal {
        override fun toString(): String = "Output(message='$message')"
    }
    @JvmInline
    value class Code(val code:Int):Signal
    object Closed:Signal {
        override fun toString(): String = "Closed"
    }
}
