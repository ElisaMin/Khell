package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


interface KShell: Flow<ProcessingResults>, Deferred<CommandResult>

@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
abstract class AbstractKShell(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val prefix: Array<String>,
    private val env: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    open val id: Int,
    private val charset: Charset,
    private val onRun: suspend RunScope.() -> Unit,
):KShell,AbstractCoroutine<CommandResult>(CoroutineScope(Dispatchers.IO).newCoroutineContext(coroutineContext), false, false)