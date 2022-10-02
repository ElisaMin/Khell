package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import me.heizi.kotlinx.shell.WriterRunScope.Companion.getDefaultRunScope
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
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
    coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
    id: Int = Shell.idMaker++,
    charset: Charset = defaultCharset,
    onRun: suspend RunScope.() -> Unit,
): KShell = coroutineScope  parent@{

    val coroutineName = CoroutineName("shell-worker#$id")
    val coroutineContext = newCoroutineContext(coroutineContext)+coroutineName
    val thisScope = CoroutineScope(coroutineContext)
    val resultScope = CoroutineScope(newCoroutineContext(coroutineContext)+CoroutineName("result#$id"))

    val idS = coroutineName.name
    fun println(vararg any: Any?) = idS.pppp("running",*any)
    fun debug(vararg any: Any?) = idS.dddd("running", *any)
    fun error(vararg any: Any?) = idS.eeee("running", *any)
    debug("ready")

    val flow = MutableSharedFlow<ProcessingResults>()

    suspend fun emit(processingResults: ProcessingResults)
        = flow.emit(processingResults)

    val task: Deferred<CommandResult> = thisScope.async(
        context = coroutineContext,
        start = coroutineStart
    ) {
        debug("starting task")
        val flow = flow.shareIn(this, SharingStarted.Eagerly)
        val result = this@parent.async {
            flow.takeWhile { it !is ProcessingResults.Closed }
                .toList()
                .plus(ProcessingResults.Closed)
                .also { debug("await to list") }
                .toResult(id)
                .also {
                    debug("result converted")
//                    resultScope.cancel()
                }
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
                    suspend fun InputStream.readAndEmit(block:((String)->ProcessingResults)) {
                        readAsFlow()
                            .mapToString()
                            .map(block)
                            .collect(::emit)
                    }
                    launch {
                        debug("read","input")
//                        debug(p.inputStream.bufferedReader(charset).use { it.readText() })
                        p.inputStream.buffered().use {
                            debug(it.bufferedReader(charset).readLines())
                            it.readAndEmit { s ->
                                println("message", s)
                                ProcessingResults.Message(s)
                            }
                        }

                    }
                    if (!isMixingMessage) launch {
                        debug("read","error")
                        debug(p.errorStream.bufferedReader(charset).use { it.readText() })
                        p.errorStream.buffered().use {
                            it.readAndEmit { s ->
                                error("error", s)
                                ProcessingResults.Message(s)
                            }
                        }
                    }
                    debug("wait","code")
                    emit(ProcessingResults.CODE(p.waitFor()))
                    debug("join","read")
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
                    println("read","end")
//                    runCatching {
//                        input.close()
//                        error.close()
//                    }
                }.also { job -> job.invokeOnCompletion { e->
                    println("read","join")
                    runCatching {
                        p.errorStream.close()
                        p.inputStream.close()
                    }
                }}

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
                debug("join","jobs")
                writeJob.join()
                readJob.join()
                debug("close","emit")
                emit(ProcessingResults.Closed)
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
        result.await()
            .also { debug("main-line","got result") }
    }
    val resultFlow = flow.shareIn(resultScope, SharingStarted.Lazily)
    debug("main-line","result flow build")
    return@parent (object :
        Flow<ProcessingResults> by resultFlow,
        Deferred<CommandResult> by task,
        KShell {}
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
