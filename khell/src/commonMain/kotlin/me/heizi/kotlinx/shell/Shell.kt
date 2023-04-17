@file:Suppress("NOTHING_TO_INLINE","unused","MemberVisibilityCanBePrivate","FunctionName",)
package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

/**
 * ## Shell - Process的封装类
 * Create and run a [Process] by command line , that's Shell no matter is Windows call it or not .
 * This class implemented [Deferred] asynchronous coroutine and [SharedFlow] ,
 * That means you can use await to wait for [CommandResult]  or collect [ProcessingResults].
 *
 * I recommend the fake constructor if you just want to **run a simple command**
 * @see Shell
 *
 * @property prefix how to start a process.
 * @property env event args
 * @property isMixingMessage stderr redirect to stdout when true
 * @property isEcho echo command line before run the command line.
 * @property onRun you can delay or something to using [RunScope.run] run some fancy line.
 * @param coroutineContext I don't know what's it So I'm just added it.
 * @param charset make sure you won't see some fancy line you have never seen before
 * @param startWithCreate don't you can read the name ? idiot
 */
@OptIn(ExperimentalApiReShell::class)
inline fun Shell(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    prefix: Array<String> = defaultPrefix,
    env: Map<String, String>? = null,
    isMixingMessage: Boolean = false,
    startWithCreate: Boolean = true,
    workdir: File? = null,
    charset: Charset = defaultCharset,
    noinline onRun: (suspend WriteScope.() -> Unit)?=null,
) = ReShell(
    coroutineContext = coroutineContext,
    charset = charset,
    environment = env,
    isRedirect = isMixingMessage,
    coroutineStart = if (startWithCreate) CoroutineStart.DEFAULT else CoroutineStart.LAZY,
    stdin = onRun,
    forest = prefix,
    workdir = workdir,
    flow = MutableSharedFlow(1024,1024)
)

/**
 * 假构造器
 *
 * @see Shell
 * @param isKeepCLIAndWrite ture means launch the Non-Close-CLI first and write command lines after. it'll exit
 * while read the exit command on your [commandLines]. run on Prefix that joined commands by && sign if false
 */
inline fun Shell(
    vararg commandLines:String,
    globalArg:Map<String,String>?=null,
    isMixingMessage: Boolean = false,
    isKeepCLIAndWrite: Boolean = false,// keep cmd maybe (
    startWithCreate: Boolean = true,
    charset: Charset = defaultCharset,
    prefix: Array<String> = defaultPrefix
) = Shell(env = globalArg, isMixingMessage=isMixingMessage, startWithCreate = startWithCreate, charset = charset,
    prefix = if (isKeepCLIAndWrite) keepCLIPrefix else
        prefix + commandLines.run {
            if (size == 1) first()
            else commandLines.joinToString(" && ")
        },
    onRun = if (!isKeepCLIAndWrite) { {
        prefix.forEach(this::echoRun)
    } } else null
)

