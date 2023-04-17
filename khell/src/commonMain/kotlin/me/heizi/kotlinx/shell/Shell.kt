@file:Suppress("NOTHING_TO_INLINE")
package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * ## Shell - Process的封装类
 * Create and run a [Process] by command line , that's Shell no matter is Windows call it or not .
 * This class implemented [Deferred] asynchronous coroutine and [SharedFlow] ,
 * That means you can use await to wait for [CommandResult]  or collect [ProcessingResults].
 *
 * I recommend the fake constructor if you just want to **run a simple command**
 * @see Shell.invoke
 *
 * @property forest how to start a process.
 * @property isRedirect stderr redirect to stdout when true
 * @property commandWriter you can delay or something to using [RunScope.run] run some fancy line.
 * @param coroutineContext I don't know what's it So I'm just added it.
 * @param charset make sure you won't see some fancy line you have never seen before
 * @param active state of coroutine on start
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class Shell(
    coroutineContext: CoroutineContext= EmptyCoroutineContext,
    forest: Array<String> = defaultPrefix,
    environment: Map<String, String>? = null,
    workdir: File? = null,
    active: Boolean = true,
    charset: Charset = defaultCharset,
    isRedirect: Boolean = false,
    stdin:(suspend WriteScope.() -> Unit)? = null,
): AbstractKShell(
    coroutineContext, forest, environment, workdir,
    active, charset , isRedirect, commandWriter =  stdin,
) {



    private val process by lazy {
        runCatching {
            process()
        }.onFailure {
            handlingProcessError(it)
        }.getOrNull()
    }

    private inline fun process() = ProcessBuilder(*forest).apply {
        if (isRedirect) this.redirectErrorStream(true)
        workdir?.let(this::directory)
        environment?.takeIf { it.isNotEmpty() }?.let {
            val e = environment()
            e.putAll(it)
        }
    }.start()

    private suspend fun collectErrJob() = coroutineScope {
        launch(newIOContext) {
            debug("collecting err")
            stdErrRead(process!!.errorStream)
        }
    }
    private suspend fun collectOutJob() = coroutineScope {
        launch(newIOContext) {
            debug("collecting out")
            stdOutRead(process!!.inputStream)
        }
    }
    private suspend fun writeJob() = commandWriter?.let { run-> coroutineScope {
        launch(newIOContext) {
            process!!.outputStream.bufferedWriter(charset).use {
                run(it.asCommandWriter())
            }
        }
    } }


    private suspend inline fun runJobInside() = coroutineScope {
        debug("building runner")
        require(process!=null) {
            "process is not even running"
        }
        debug("runner bullied")
        collectOutJob()
        //如果混合消息则直接跳过这次的collect
        if (!isRedirect) collectErrJob()
        writeJob()?.invokeOnCompletion {error->
            error?.let {
                debug("write job failed",it)
            }
        }
        val code = withContext(IO) {
            process!!.waitFor()
        }
        debug("process exit with code $code")
//        this.coroutineContext.job.join()
        debug("all job joined")
        runCatching { process!!.destroy() }
        debug("process destroyed")
        emit(ProcessingResults.CODE(code))
        close()
        debug("emit closed")
        debug("run out")
    }

    private suspend inline fun read(stream:InputStream,crossinline onLine:suspend (String)->Unit) {
        stream.bufferedReader(charset).useLines { it.forEach { line ->
            onLine(line)
        } }
    }
    private suspend fun stdErrRead(stream:InputStream) = read(stream) {
        onLineErr(it)
    }
    private suspend fun stdOutRead(stream:InputStream) = read(stream) {
        onLineOut(it)
    }

    override suspend fun create() {
        process!!
    }

    override suspend fun CoroutineScope.running(): CommandResult {
        runJobInside()
        debug("block returning")
        return result!!
    }

    companion object {


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
            workdir: File? = null,
            prefix: Array<String> = defaultPrefix
        ):Shell {
            require(commandLines.isNotEmpty()) {
                "unless one command"
            }
            val prefix = if (isKeepCLIAndWrite) keepCLIPrefix else
                prefix + commandLines.run {
                    if (size == 1) first()
                    else commandLines.joinToString(" && ")
                }
            // Log
//            println("new command",
//                (if (isKeepCLIAndWrite) prefix.joinToString(" && ") else prefix.joinToString(" ")+" "+commandLines.joinToString(" && "))
//            )
            return Shell(
                forest = prefix,
                environment = globalArg,
                isRedirect = isMixingMessage,
                active = startWithCreate,
                charset = charset,
                workdir = workdir
            ) {
                if (!isKeepCLIAndWrite) commandLines.forEach(this::run)
            }
        }
    }
}

