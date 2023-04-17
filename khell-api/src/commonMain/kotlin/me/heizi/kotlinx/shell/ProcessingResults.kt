package me.heizi.kotlinx.shell




sealed interface Signal {
    @JvmInline
    value class Error(val message:String):Signal {
        override fun toString(): String = "Error(message='$message')"
    }
    @JvmInline
    value class Output(val message:String):Signal {
        override fun toString(): String = "Output(message='$message')"
    }
    @JvmInline
    value class Code(val code:Int):Signal
    object Closed:Signal {
        override fun toString(): String = "Closed"
    }
}
/**
 * 处理中会弹出的数据
 */
sealed interface ProcessingResults:Signal {



    /**
     * [Process.exitValue] 退出返回值
     */
    class CODE constructor(val code:Int):ProcessingResults {

        companion object {
            const val SUCCESS = 0
        }

        override fun toString(): String {
            return "CODE(code=$code)"
        }
    }

    /**
     * [Process.getErrorStream] 错误数据
     *
     * @param message  错误数据的本身
     */
    class Error constructor(val message:String):ProcessingResults {
        override fun toString(): String {
            return "Error(message='$message')"
        }
    }

    /**
     * [Process.getInputStream] 弹出的数据
     *
     * @param message
     */
    class Message constructor(val message: String):ProcessingResults {
        override fun toString(): String {
            return "Message(message='$message')"
        }
    }

    /**
     * Closed process执行完毕
     */
    object Closed : ProcessingResults {
        override fun toString(): String
            = "Closed"

    }

    /**
     * Starting 正在启动
     *
     */
//    object Starting : ProcessingResults()
}