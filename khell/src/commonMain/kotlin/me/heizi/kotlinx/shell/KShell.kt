package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.shell.AbstractKShell.Companion.getNewId
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import me.heizi.kotlinx.shell.Shell.Companion.exceptionRegex
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp



@Deprecated("its shit.")

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("NAME_SHADOWING")
suspend fun shell(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    prefix: Array<String> = defaultPrefix,
    env: Map<String, String>? = null,
    isMixingMessage: Boolean = false,
    isEcho: Boolean = false,
    coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
    id: Int = getNewId(),
    charset: Charset = defaultCharset,
    onRun: suspend RunScope.() -> Unit,
): KShell = coroutineScope  parent@{

    val coroutineName = CoroutineName("shell-worker#$id")
    val coroutineContext = newCoroutineContext(coroutineContext)+coroutineName
    val thisScope = CoroutineScope(coroutineContext)
//    val resultScope = CoroutineScope(newCoroutineContext(Dispatchers.IO)+CoroutineName("result#$id"))

    val idS = coroutineName.name
    fun warpMsg(vararg any: Any?)
        = (any.takeIf { it.size > 1 }?.toList() ?: listOf("running",*any)).toTypedArray()
    fun println(vararg any: Any?) = idS.pppp(*warpMsg(any))
    fun debug(vararg any: Any?) = idS.dddd(*warpMsg(any))
    fun error(vararg any: Any?) = idS.eeee(*warpMsg(any))
    debug("ready")

    val flow = channelFlow {
        suspend fun endWithError(reason:String,code:Int) {
            send(ProcessingResults.Error(reason))
            send(ProcessingResults.CODE(code))
            send(ProcessingResults.Closed)
        }
        runCatching {
            debug("building runner")
            ProcessBuilder(*prefix).run {
                env?.let { environment().putAll(it) }
                redirectErrorStream(!isMixingMessage)
                val p = start()
                debug("runner bullied")
                p
            }.let { p ->
                require(p!=null) { "process is not even running" }
                val writeJob = launch {
                    debug("write","start")
                    p.outputStream.writer(charset).use {
                        it.getDefaultRunScope(isEcho,id).let { s ->
                            debug("writing")
                            onRun(s)
                        }
                    }
                }.also { it.invokeOnCompletion {
                    println("write","join")
                    runCatching {
                        p.outputStream.close()
                    }
                } }
                val readJob = launch {

                    debug("read","start")
                    suspend fun InputStream.readAwait(): Pair<Boolean, ByteArray> {
                        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                        return runCatching { (read(bytes) != -1) }.getOrDefault(false) to bytes
                    }
                    suspend fun InputStream.readAsFlow()= flow {
                        do {
                            val (keep,bytes) = readAwait()
                            if (bytes.isBlank()) continue else emit(bytes)
                        } while (keep)
                    }
                    fun Flow<ByteArray>.mapToString()
                            = map { String(it,charset) }
                    suspend fun InputStream.readAndSend(block:((String)->ProcessingResults)) {
                        readAsFlow()
                            .mapToString()
                            .map(block)
                            .collect(::send)
                    }
                    launch {
                        debug("read","input")
//                        debug(p.inputStream.bufferedReader(charset).use { it.readText() })
                        p.inputStream.buffered().use {
                            debug(it.bufferedReader(charset).readLines())
                            it.readAndSend { s ->
                                println("message", s)
                                ProcessingResults.Message(s)
                            }
                        }

                    }
                    if (!isMixingMessage) launch {
                        debug("read","error")
                        debug(p.errorStream.bufferedReader(charset).use { it.readText() })
                        p.errorStream.buffered().use {
                            it.readAndSend { s ->
                                error("error", s)
                                ProcessingResults.Message(s)
                            }
                        }
                    }
                    debug("wait","code")
                    send(ProcessingResults.CODE(p.waitFor()))
                    debug("join","read")
                    joinAll()

                    println("read","end")
                }.also { job -> job.invokeOnCompletion { e->
                    println("read","join")
                    runCatching {
                        p.errorStream.close()
                        p.inputStream.close()
                    }
                }}
                debug("join","jobs")
                writeJob.join()
                readJob.join()
                debug("close","send")
                send(ProcessingResults.Closed)
            }
        }.onFailure { e->
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
                        else -> endWithError(reason = e.toString(),-1)
                    }
                } else -> endWithError(reason = e.toString(),-1)
            }
        }
        debug("main-line","waiting")
    }

    val result = async(start = coroutineStart) {
        flow.toList().toResult(id)
    }

    debug("main-line","result flow build")
    return@parent (object :
        Flow<ProcessingResults> by flow.shareIn(CoroutineScope(coroutineContext), started = SharingStarted.Lazily),
        Deferred<CommandResult> by result,
        KShell {
//            override suspend fun collect(collector: FlowCollector<ProcessingResults>) {
//                flow.takeWhile { it !is ProcessingResults.Closed }.collect(collector)
//                collector.emit(ProcessingResults.Closed)
//            }

    }
    ).also { debug("main-line","return") }
}

private fun ByteArray.isBlank(): Boolean {
    for (i in this) {
        if (i != 0.toByte()) {
            return false
        }
    }
    return true
}
