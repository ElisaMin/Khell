package me.heizi.kotlinx.shell

import android.util.Log
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import me.heizi.kotlinx.shell.ProcessingResults.CODE.Companion.SUCCESS

/**
 * 程序结束时会拿到的结果
 */
sealed class CommandResult {
    companion object {
        /**
         * 等待正在执行的程序退出并返回结果
         * @return 完整的程序执行结果
         */
        suspend fun Flow<ProcessingResults>.waitForResult(): CommandResult {
            val message:StringBuilder = StringBuilder()
            var error:StringBuilder? = null
            var result: CommandResult? = null
            this.takeWhile {
                it !is ProcessingResults.Closed
            }.collect {
                when(it) {
                    is ProcessingResults.Message -> {
                        message.append(it.message)
//                        Log.i(TAG, "waitForResult: message{${it.message}}")
                        message.append("\n")
                    }
                    is ProcessingResults.Error -> {
                        error?.let { e ->
                            Log.i(TAG, "waitForResult: error{${it.message}}")
                            e.append(it.message)
                            e.append("\n")
                        } ?: run {
                            error = StringBuilder()
                        }
                    }
                    is ProcessingResults.CODE -> {
//                        Log.i(TAG, "waitForResult: code{${it.code}}")
                        result = if (it.code == SUCCESS) {
                            Success(message.toString())
                        } else {
                            Failed(message.toString(), error?.toString(), it.code)
                        }
                    }
                    is ProcessingResults.Closed -> {
                        currentCoroutineContext().cancel()
                    }
                }
            }
            return result!!
        }
    }

    data class Success internal constructor(val message: String): CommandResult()
    data class Failed  internal constructor(val processingMessage: String,val errorMessage:String?,val code: Int):
        CommandResult()
}