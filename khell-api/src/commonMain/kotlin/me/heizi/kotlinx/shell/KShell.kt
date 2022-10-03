package me.heizi.kotlinx.shell

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow


interface KShell: Flow<ProcessingResults>, Deferred<CommandResult>

