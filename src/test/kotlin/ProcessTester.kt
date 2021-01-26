
import Coroutines.Companion.GBK
import Coroutines.Companion.not
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Executors
import kotlin.test.Test



class OneTimeExecutor(
) {

    private val processing = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    private val scope = GlobalScope + processing


    @Test
    fun test(){
        GlobalScope.launch {
           OneTimeExecutor().suspendExecute(
               "cmd","/c","echo","shit"
           ).collect {
               println(it)
           }
        }
        while (true) Unit
    }
    sealed class ProcessingMessage {
        companion object {
            const val SUCCESS = 0
        }
        class Code(val code: Int):ProcessingMessage()
        class Message(val string: String):ProcessingMessage()
    }
    suspend fun suspendExecute(vararg commands: String):Flow<ProcessingMessage> {
        var p: Process
        val flow = MutableSharedFlow<ProcessingMessage>()
        scope.launch(processing) {
            p = ProcessBuilder(*commands).start()
            p.inputStream.bufferedReader().forEachLine {
                runBlocking(scope.coroutineContext) {
                    flow.emit(ProcessingMessage.Message(it))
                }
            }
            p.waitFor()
            flow.emit(ProcessingMessage.Code(p.exitValue()))
            p.destroy()
        }
        return flow
    }
}

class Thinking {

    @Test
    fun wrongCommand() {
        ProcessBuilder("cmd",).start().let {
            it.outputStream.bufferedWriter(GBK).let {
                it.append("@exit \n")
                it.flush()
            }
            it.inputStream.bufferedReader(GBK).forEachLine(::println)
            !it.exitValue()
        }
    }

    /**
     * 在主线程wait for
     * 子线程1 做println write somethings
     */

    class BlockShit {
        val process = ProcessBuilder("cmd",).start()
        private val writer = process.outputStream.bufferedWriter(GBK)
        fun execute(command:String) = runBlocking(IO) {
            writer.append(command)
            writer.newLine()
            writer.flush()
        }

        fun execute() {
            execute("echo shit")
        }
        init {
            !"init"
            execute("echo off")
            GlobalScope.launch(IO) {
                process.inputStream.bufferedReader(GBK).lineSequence().forEach {
                    runBlocking(this.coroutineContext+IO) {
                        println(it)
                    }

                }

            }
            !"init launched"
//            process.waitFor()
        }
    }
    @Test
    fun blockingShit() {
        BlockShit().let {
            GlobalScope.launch(IO) {
                !"second IO"
                delay(4000)
                it.execute("exit")
                !"exited"
            }
            it.process.waitFor()
        }
    }
}

class Coroutines {

    companion object {
        val GBK = Charset.forName("GBK")
        val Any?.threadName: String? get() = Thread.currentThread().name
        private const val debug = true
        operator fun Any?.not() {
            if (debug) {
                println("${Date().toString()} 信息 $this")
            }
        }
    }

    @Test
    fun notAny() {
        !(!true) // boolean all ,not println
    }
    @Test
    fun test () {
        println("on live")
        Executors.newSingleThreadExecutor().also {
            it.execute {
                println("on execute ${it.threadName}")
            }
        }.asCoroutineDispatcher().let { new ->
            !"on let $threadName"
            GlobalScope.launch(new) {
                !"on coroutine $threadName"
            }
        }
        println("on live $threadName")

//        Thread {
//            GlobalScope.launch {
//                println()
//            }
//        }
    }
}
class Tester {


//    @JvmStatic
//    fun main(args: Array<String>) {
//        println(File("./").absolutePath)
//    }

    @Test
    fun fileAndBuilder() {
        File("./temp").takeUnless { it.exists() }?.mkdirs()
        val output = File("./temp/o").let {
            if (!it.exists()) it.createNewFile()
            it
        }
        output.let {f->
            f.writer().use { it.flush() }
        }



        GlobalScope.launch() {
            ProcessBuilder("cmd")
                .redirectInput(output)
                .start()
                .also {
                    it.inputStream.bufferedReader(GBK).forEachLine(::println)
                }
                .exitValue()
                .let(::println)
        }

        output.writer().let {
            it.append("echo 123 \n")
            it.flush()
        }

        output.writer().let {
//            it.append("exit \n")
//            it.flush()
        }

    }


    sealed class CommandResult(
        open val message:String?=null
    ) {
        object Start:CommandResult(null)
        object Wait:CommandResult(null)
        class Success(message: String?):CommandResult(message)
        class Failed(val code: Int, message: String):CommandResult(message)
    }


    @Test
    fun windowsCmdExecutor() {
        val wce = WindowsCmdExecutor()
        wce.execute("wrong")
        !wce.waiting()
    }

    /**
     * 用于Windows的CMD Executor
     *
     * @param _scope 一个协程范围
     */
    class WindowsCmdExecutor(
        _scope:CoroutineScope = GlobalScope
    ) {

        /** 私有主子两条线程 两条似乎不够 */
        private val privateDispatcher by lazy {
            !"building dispatcher"
            Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        }

        /** 内协程范围 */
        private val scope by lazy {
            !"building scope"
            _scope+privateDispatcher
        }
//        private val outputFlow by lazy {
//            SharedFlow
//        }

        private lateinit var process:Process
        private val reader by lazy { process.inputStream.reader(GBK) }
        private val writer by lazy {process.outputStream.bufferedWriter(GBK) }
        private val stringBuilder by lazy { StringBuilder() }
        private val _resultFlow = MutableStateFlow<CommandResult>(CommandResult.Wait)

        val resultFlows:StateFlow<CommandResult> get() = _resultFlow


        /**
         * 堵塞然后新建process和foreach job
         */
        init {
            !"init"
            runBlocking(scope.coroutineContext) {
                process = ProcessBuilder("cmd").start()
                execute("echo off")
                !"process running will"
                scope.launch(privateDispatcher) {
                    reader.forEachLine {
                        runBlocking(scope.coroutineContext) {
                            forEachLine(it)
                        }
                    }
                }
                scope.launch(privateDispatcher) {
                    process.errorStream.bufferedReader(GBK).forEachLine {
                        runBlocking(scope.coroutineContext) {
                            !it
                        }
                    }
                }
                !"flow collect outing of init"
            }

        }

        private var started = false
        private var code = false
        /** Foreach job 移动到这里
         * @param [_string] 需要操作的字符串 也就是从cmd里面拿下来的
         */
        private suspend fun forEachLine(string: String) {
            val s = string.trim()
            !s
            if (started) when {
                s.matches("^::start:.+".toRegex()) -> {
                    !"start"
                }
                s.matches("^::end:.+".toRegex()) -> {
                    !"end"
                }
                code -> {
                    !code
                }
                s == "echo %errorlevel%" -> {
                    code = true
                }

            } else {
                started = s == "::end:echo off"
                if (started) _resultFlow.emit(CommandResult.Start)
                stringBuilder.clear()
            }



//                string.matches("".toRegex()) -> {}
//                string.matches("".toRegex()) -> {}
//            !"next line"
        }

        private inline fun run( command: String) {
            writer.write(command)
            writer.newLine()
            writer.flush()
        }

        class NotSingleLineException:Exception("指令不是单行")

        private fun onDestroy() {

        }

        fun execute(command: String,label:String?=null) = runBlocking(scope.coroutineContext){
            if (command.matches("exit.*".toRegex())) {
                run(command)
                onDestroy()
            }
            if ((label?:command ).contains("\n")) throw NotSingleLineException()
            run("::start:${label?:command}")
            run(command)
            run("::end:${label?:command}")
            Unit

        }
        fun waiting() = process.waitFor()
    }


    @Test
    fun usingProcess() {
//        ProcessBuilder("cmd","\\c","dir").start().let { p ->
//            GlobalScope.launch(IO) {
//                println("fucking hello")
//                p.inputStream
//            }
//            GlobalScope.launch(IO) {
//                p.outputStream.write("@echo off\n".toByteArray())
//                p.outputStream.flush()
//                println("fucking hello aging")
//
//                repeat(99) {
//                    delay(300)
//                    p.outputStream.write("@echo hello $it \n".toByteArray(Charset.forName("GBK")))
//                    p.outputStream.flush()
//                }
//                p.destroy()
//
//
//            }
//            p.waitFor()
//        }

    }
}
