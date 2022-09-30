package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import me.heizi.kotlinx.shell.WriterRunScope.Companion.getDefaultRunScope
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp


interface KShell: Flow<ProcessingResults>, Deferred<CommandResult>

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("NAME_SHADOWING")
suspend fun shell(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    prefix: Array<String> = defaultPrefix,
    env: Map<String, String>? = null,
    isMixingMessage: Boolean = false,
    isEcho: Boolean = false,
    coroutineStart: CoroutineStart = CoroutineStart.LAZY,
    id: Int = Shell.idMaker++,
    charset: Charset = defaultCharset,
    onRun: suspend RunScope.() -> Unit,
): KShell = coroutineScope {

    val coroutineName = CoroutineName("shell-worker#$id")
    val coroutineContext = newCoroutineContext(coroutineContext)+coroutineName

    val idS = coroutineName.toString()
    fun println(vararg any: Any?) = idS.pppp("running",*any)
    fun debug(vararg any: Any?) = idS.dddd("running", *any)
    fun error(vararg any: Any?) = idS.eeee("running", *any)

    val flow = MutableSharedFlow<ProcessingResults>()
    suspend fun emit(processingResults: ProcessingResults)
        = flow.emit(processingResults)

    val task: Deferred<CommandResult> = async(
        context = coroutineContext,
        start = coroutineStart
    ) {
        val flow = flow.shareIn(this, SharingStarted.Eagerly)
        val result = async {
            flow.takeWhile { it !is ProcessingResults.Closed }
                .toList()
                .also { debug("await to list") }
                .toResult(id)
                .also { debug("result converted") }
        }

        suspend fun endWithError(reason:String,code:Int) {
            emit(ProcessingResults.Error(reason))
            emit(ProcessingResults.CODE(code))
            emit(ProcessingResults.Closed)

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
                val readJob = launch {
                    suspend fun BufferedInputStream.readAwait(): Pair<Boolean, ByteArray> {
                        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                        return (read(bytes) != -1) to bytes
                    }
                    suspend fun BufferedInputStream.readAsFlow()= flow {
                        do {
                            val (keep,bytes) = readAwait()
                            emit(bytes)
                        } while (keep)
                    }
                    fun Flow<ByteArray>.mapToString()
                        = map { String(it,charset) }
                    suspend fun BufferedInputStream.readAndEmit(block:((String)->ProcessingResults)) {
                        readAsFlow()
                            .mapToString()
                            .map(block)
                            .collect(::emit)
                    }
                    launch {
                        p.inputStream.buffered().use {
                            it.readAndEmit { s ->
                                println("message", s)
                                ProcessingResults.Message(s)
                            }
                        }
                    }
                    launch {
                        p.errorStream.buffered().use {
                            it.readAndEmit { s ->
                                error("error", s)
                                ProcessingResults.Message(s)
                            }
                        }
                    }
                    joinAll()
//
//                    var inputJob:Job? = null
//                    var errorJob:Job? = null
//                    var labelI = 0
//                    var labelE = if (isMixingMessage) -1 else 0
//
//                    val errorTask = async(Dispatchers.IO) {
//                        error.readAwait()
//                    }

//                    while (labelI!=-1&&labelE!=-1) {
//                        inputJob?.join()
//                        //block error
//                        errorJob?.join()
//                        errorJob = if (labelE==-1) null else launch(Dispatchers.IO) {
//
//                            // waiting error
//                            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
//                            labelE = error.read(bytes)
//                            emit(ProcessingResults.Error(String(bytes,charset)))
//                            error("error", p)
//                        }
//                        inputJob = if (labelI==-1) null else launch(Dispatchers.IO) {
//                            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
//                            labelI = input.read(bytes)
//                            emit(ProcessingResults.Message(String(bytes,charset)))
//                            println("message", p)
//                        }
//                    }
                    println("read task done")
//                    runCatching {
//                        input.close()
//                        error.close()
//                    }
                }.also { job -> job.invokeOnCompletion { e->
                    println("closing read stream")
                    runCatching {
                        p.errorStream.close()
                        p.inputStream.close()
                    }
                }}

                val writeJob = launch(coroutineContext) {
                    p.outputStream.writer(charset).use {
                        it.getDefaultRunScope(isEcho,id).let { s ->
                            debug("writing")
                            onRun(s)
                        }
                    }
                }.also { it.invokeOnCompletion {
                    println("closing write stream")
                    p.outputStream.close()
                } }
                val flow = flow.shareIn(this, SharingStarted.Eagerly)
                    .takeWhile { it !is ProcessingResults.Closed }
                debug("joins the jobs")
                writeJob.join()
                readJob.join()
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
        result.await()
    }
    return@coroutineScope object :
        Flow<ProcessingResults> by flow.shareIn(this, SharingStarted.Eagerly),
        Deferred<CommandResult> by task,
        KShell {}
}