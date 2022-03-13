package me.heizi.kotlinx.shell

//@Deprecated("not working")
//object Khell {
//    var env = mapOf<String,String>()
//}

//难产

//@OptIn(InternalCoroutinesApi::class)
//class Khell(
//    coroutineContext: CoroutineContext = EmptyCoroutineContext,
//    private val prefix: Array<String> = arrayOf("cmd", "/k"),
//    private val env: Map<String, String>? = null,
//    private val isMixingMessage: Boolean = false,
//    private val isEcho: Boolean = false,
//    startWithCreate: Boolean = true,
//    private val onRun: suspend RunScope.() -> Unit
//):Flow<CommandResult>by MutableSharedFlow(),AbstractCoroutine<CommandResult>(coroutineContext,false,false),Deferred<CommandResult>  {
//    override val onAwait: SelectClause1<CommandResult>
//}