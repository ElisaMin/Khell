package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext



@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
abstract class AbstractKShell(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val prefix: Array<String>,
    private val env: Map<String, String>? = null,
    private val isMixingMessage: Boolean = false,
    private val isEcho: Boolean = false,
    startWithCreate: Boolean = true,
    val id: Int = getNewId(),
    private val charset: Charset,
    private val onRun: suspend RunScope.() -> Unit,
): KShell,
    CoroutineScope by CoroutineScope(coroutineContext+CoroutineName("shell-worker#$id")),
    AbstractCoroutine<CommandResult>(coroutineContext+CoroutineName("shell-worker#$id"), false, false) {
    companion object {
        private var idMaker = 0

        fun getNewId(): Int
            = idMaker++

    }
}