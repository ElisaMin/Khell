@file:JvmName("khell-jvm-impl")
package me.heizi.kotlinx.shell

import java.nio.charset.Charset

actual val defaultPrefix:CommandPrefix
    = arrayOf("cmd","/c")
actual val keepCLIPrefix: CommandPrefix
    = arrayOf("cmd","/k","echo off")
actual val defaultCharset: Charset
    = charset("GBK")
@Suppress("NOTHING_TO_INLINE")
@ExperimentalApiReShell
internal actual inline fun ReShell.id(): Number = process.pid()