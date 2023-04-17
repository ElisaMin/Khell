package me.heizi.kotlinx.shell


import me.heizi.kotlinx.logger.debug


/**
 * 程序结束时会拿到的结果
 */
sealed class CommandResult {


    companion object {
        private var idMaker = 0

        fun Sequence<Signal>.toResult(): CommandResult {
            val code = filterIsInstance<Signal.Code>().firstOrNull()?.code ?: Int.MIN_VALUE
            return if (code == 0) Success (
                mapNotNull {
                    when(it) {
                        is Signal.Output -> it.message
                        is Signal.Error -> it.message
                        else -> null
                    }
                }.joinToString("\n")
            ) else {
                val msg = filterIsInstance<Signal.Output>().joinToString("\n") { it.message }
                val err = filterIsInstance<Signal.Error>().joinToString("\n") { it.message }
                Failed(msg,err,code)
            }
        }
        /**
         * 对完整的ResultList进行整合
         * @return 另外一种形式的ResultList(
         */
        fun List<ProcessingResults>.toResult(id:Int= idMaker++):CommandResult {
            val key = "toResult#${ id }"
            key.debug("new")
            require(last() is ProcessingResults.Closed) {
                "process is running still !!"
            }
            val msg = StringBuilder()
            val err = StringBuilder()
            var code = Int.MIN_VALUE
            key.debug("size",this.size)
            forEach { when(it) {
                is ProcessingResults.Message -> msg.appendLine(it.message)
                is ProcessingResults.Error -> err.appendLine(it.message)
                is ProcessingResults.CODE -> code = it.code
                else -> {}
            } }

            key.debug(msg.toString().lines().joinToString(limit = 5))
            key.debug(err.toString().lines().joinToString(limit = 5))
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