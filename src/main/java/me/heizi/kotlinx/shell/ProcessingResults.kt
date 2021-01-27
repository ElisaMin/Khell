package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.shell.CommandResult.Companion.waitForResult
import java.util.concurrent.Executors








///**
// * 解释器专用线程池
// */
//private val Dispatcher: CoroutineDispatcher by lazy { Executors.newFixedThreadPool(4).asCoroutineDispatcher() }

//*
// * 单次解释器
// *
// * @param commandLines
// * @param scope
// * @param dispatcher
// * @param prefix
// * @return
//@Suppress("NAME_SHADOWING", "BlockingMethodInNonBlockingContext")
//fun oneTimeExecutor(
//    vararg commandLines: String,
//    scope: CoroutineScope = GlobalScope,
//    dispatcher: CoroutineDispatcher = Dispatcher,
//    prefix:Array<String> = arrayOf("sh")
//):Flow<ProcessingResults> {
//    val scope = scope + dispatcher
//    val flow = MutableSharedFlow<ProcessingResults>()
//    val process = Runtime.getRuntime().exec(prefix)
//    val waitQueue = Array(3) {false}
//
//    scope.launch(dispatcher) {
//        process.outputStream.writer().let {
//            for( i in commandLines) {
//                it.write(i)
//                it.write("\n")
//                it.flush()
//            }
//            process.outputStream.runCatching {
//                close()
//            }
//            waitQueue[0] = true
//        }
//    }
//    scope.launch(dispatcher) {
//        process.inputStream.bufferedReader().lineSequence().forEach {
//            flow.emit(ProcessingResults.Message(it))
//        }
//        waitQueue[1] = true
//    }
//    scope.launch(dispatcher) {
//        process.errorStream.bufferedReader().lineSequence().forEach {
//            flow.emit(ProcessingResults.Error(it))
//        }
//        waitQueue[2] = true
//    }
//    scope.launch(dispatcher) {
//        //等待执行完成
//        while (waitQueue.contains(false)) delay(1)
//        flow.emit(ProcessingResults.CODE(process.waitFor()))
//        kotlin.runCatching {
//            process.inputStream.close()
//            process.errorStream.close()
//            process.destroy()
//        }
//        flow.emit(ProcessingResults.Closed)
//    }
//    return flow
//}
/**
 * 处理中会弹出的数据
 */
sealed class ProcessingResults {



    /**
     * [Process.exitValue] 退出返回值
     */
    class CODE(val code:Int):ProcessingResults() {

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
    class Error(val message:String):ProcessingResults() {
        override fun toString(): String {
            return "Error(message='$message')"
        }
    }

    /**
     * [Process.getInputStream] 弹出的数据
     *
     * @param message
     */
    class Message(val message: String):ProcessingResults() {
        override fun toString(): String {
            return "Message(message='$message')"
        }
    }

    /**
     * Closed process执行完毕
     */
    object Closed : ProcessingResults()

    /**
     * Starting 正在启动
     *
     */
//    object Starting : ProcessingResults()
}