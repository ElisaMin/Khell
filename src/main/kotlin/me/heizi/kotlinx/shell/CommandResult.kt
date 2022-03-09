package me.heizi.kotlinx.shell


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
                else -> {}
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

        private var errorTimes = -1

        fun Exception.toResult():Failed {
            return Failed(stackTrace.joinToString(":"),message, errorTimes--)
        }
    }
    data class Success internal constructor(val message: String): CommandResult()
    data class Failed internal constructor(val processingMessage: String,val errorMessage:String?,val code: Int): CommandResult()
}