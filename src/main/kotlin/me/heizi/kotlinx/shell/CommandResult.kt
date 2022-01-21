package me.heizi.kotlinx.shell


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import me.heizi.kotlinx.logger.debug


/**
 * 程序结束时会拿到的结果
 */
sealed class CommandResult {

    companion object {

        /**
         * 对完整的ResultList进行整合
         * @return 另外一种形式的ResultList(
         */
        fun List<ProcessingResults>.toResult():CommandResult {
            require(last() is ProcessingResults.Closed) {
                "process is running still !!"
            }
            val msg = StringBuilder()
            val err = StringBuilder()
            var code = Int.MIN_VALUE
            "result".debug(this)
            forEach { when(it) {
                is ProcessingResults.Message -> msg.appendLine(it.message)
                is ProcessingResults.Error -> err.appendLine(it.message)
                is ProcessingResults.CODE -> code = it.code
            } }

            "result".debug(msg)
            "result".debug(err)
            return if (code != 0 )
                Failed(
                    msg.toString().dropLastWhile { it=='\n' },
                    err.toString().takeIf { it.isNotEmpty() }?.dropLastWhile { it=='\n' },
                    code
                )
            else
                Success(msg.toString().dropLastWhile { it=='\n' })

        }
        /**
         * 等待正在执行的程序退出并返回结果
         * @return 完整的程序执行结果
         */
        suspend fun Flow<ProcessingResults>.waitForResult(
            onMessage:(String)->Unit = {},
            onError:(String)->Unit={},
            onResult:(CommandResult)->Unit = {},
        ): CommandResult = coroutineScope {
            "result".debug("waiting")
            val message:StringBuilder = StringBuilder()
            var error:StringBuilder? = null
            var result: CommandResult? = null
            takeWhile {
                it !is ProcessingResults.Closed
            }.collect {
                println("processing",it)
                when(it) {
                    is ProcessingResults.Message ->  {
                        if (message.isNotEmpty()) message.append("\n")
                        message.append(it.message)
                        onMessage(it.message)
                    }
                    is ProcessingResults.Error -> {
                        error?.let { e ->
                            "result".debug("error",it.message)
                            e.append(it.message)
                        } ?: run {
                            error = StringBuilder(it.message)
                        }
                        onError(it.message)
                    }
                    is ProcessingResults.CODE -> {
                        result = if (it.code == ProcessingResults.CODE.SUCCESS) {
                            Success(message.toString())
                        } else {
                            Failed(message.toString(), error?.toString(), it.code)
                        }
                        CoroutineScope(Dispatchers.Default).launch {
                            onResult(result!!)
                        }
                    }
                    ProcessingResults.Closed -> throw IllegalStateException("Process closed!!")
                }
                }
            "result".debug(result!!::class.simpleName,result)
            result!!
        }
        private var errorTimes = -1

        fun Exception.toResult():Failed {
            return Failed(stackTrace.joinToString(":"),message, errorTimes--)
        }
    }
    data class Success internal constructor(val message: String): CommandResult()
    data class Failed internal constructor(val processingMessage: String,val errorMessage:String?,val code: Int): CommandResult()
}