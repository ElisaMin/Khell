package me.heizi.kotlinx.shell

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.heizi.kotlinx.shell.CommandResult.Companion.toResult
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.heizi.kotlinx.logger.debug as dddd
import me.heizi.kotlinx.logger.error as eeee
import me.heizi.kotlinx.logger.println as pppp

interface KShell: Flow<Signal>, Deferred<CommandResult> {
    companion object:CoroutineScope {
        override val coroutineContext: CoroutineContext = SupervisorJob() + CoroutineName("KShell")
    }
}
