package me.heizi.kotlinx.shell

/**
 * 单次执行 执行完毕后立即作废
 */

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.heizi.kotlinx.shell.CommandResult.Companion.waitForResult
import java.io.IOException


/**
 * 用于匹配错误的Regex
 */
private val exceptionRegex by lazy { "Cannot run program \".+\": error=(\\d+), (.+)".toRegex() }

/**
 * 使用SU异步执行Shell[commandLines]
 *
 * @param dispatcher 至少有3个任务同时进行
 * @return [Deferred]
 */
fun CoroutineScope.su(
        vararg commandLines: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        isErrorNeeding: Boolean = false
): Deferred<CommandResult> = async(dispatcher) {
    Log.i(TAG, "su: ${commandLines.joinToString()}")
    shell(commandLines = commandLines,arrayOf("su"),dispatcher,isErrorNeeding,isErrorNeeding).waitForResult()
}
/**
 *
 *
 * @param commandLines 所要丢给shell的指令
 * @param prefix 决定了以哪种形式打开这个解释器
 * @return
 */
@Suppress( "BlockingMethodInNonBlockingContext")
fun CoroutineScope.shell(
        commandLines: Array<out String>,
        prefix:Array<String> = arrayOf("sh"),
        dispatcher: CoroutineDispatcher = Default,
        isMixingMessage: Boolean = false,
        isEcho: Boolean = false
): Flow<ProcessingResults> {

    Log.i(TAG, "run: called")

    val flow = MutableSharedFlow<ProcessingResults>()

    val process = try {
        ProcessBuilder(*prefix).run {
            if (isMixingMessage) this.redirectErrorStream(true)
            start()
        }
    }catch (e:IOException) {
        Log.w(TAG, "run: catch IO exception", e)
        if (e.message != null) when {
            e.message!!.matches(exceptionRegex) -> {
                launch(dispatcher) {
                    exceptionRegex.find(e.message!!)!!.groupValues.let {
                        flow.emit(ProcessingResults.Error(it[2]))
                        flow.emit(ProcessingResults.CODE(it[1].toInt()))
                        flow.emit(ProcessingResults.Closed)
                    }
                }
                return flow
            }
        }
        throw IOException("未知错误",e)
    }
    //runner构建完成
    Log.i(TAG, "run: bullied")
    //开始run
    val waitQueue = Array(3) {false}
    launch(dispatcher) {
        Log.i(TAG, "run: writing")
        process.outputStream.writer().let {
            for( i in commandLines) {
                if (isEcho) {
                    it.write("echo \"$i\" \n")
                    it.flush()
                }
                it.write(i)
                Log.i(TAG, "run: command{$i}")
                it.write("\n")
                it.flush()
            }
            process.outputStream.runCatching {
                close()
            }
            waitQueue[0] = true
        }
    }
    launch(dispatcher) {
        Log.i(TAG, "run: reading")
        process.inputStream.bufferedReader().lineSequence().forEach {
            Log.i(TAG, "run: message{$it}")
            flow.emit(ProcessingResults.Message(it))
        }
        waitQueue[1] = true
    }
    //如果混合消息则直接跳过这次的collect
    if (!isMixingMessage) launch(dispatcher) {
        process.errorStream.bufferedReader().lineSequence().forEach {
            Log.w(TAG, "run: error{$it}")
            flow.emit(ProcessingResults.Error(it))
        }
        waitQueue[2] = true
    } else waitQueue[2] = true

    launch(dispatcher) {
        //等待执行完成
        //如果100毫秒内没有反应等待100毫秒
        var times = 0
        while (waitQueue.contains(false)) {
            if(times++ > 100) delay(99)
            if(times > 104) delay(100)
            delay(1)
        }
        flow.emit(ProcessingResults.CODE(process.waitFor()))
        kotlin.runCatching {
            process.inputStream.close()
            process.errorStream.close()
            process.destroy()
        }
        flow.emit(ProcessingResults.Closed)
    }
    return flow
}


