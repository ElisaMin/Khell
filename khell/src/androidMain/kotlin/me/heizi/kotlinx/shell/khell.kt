@file:JvmName("khell-android-impl")
package me.heizi.kotlinx.shell

import java.nio.charset.Charset

actual  val defaultPrefix: CommandPrefix
    = arrayOf("sh")
actual  val keepCLIPrefix: CommandPrefix
    get()  = TODO()

actual val defaultCharset:Charset = Charsets.UTF_8
@Suppress("NOTHING_TO_INLINE")
@ExperimentalApiReShell
internal actual inline fun ReShell.id(): Number {
     return process.runCatching {
        javaClass.getDeclaredField("pid").run {
            isAccessible = true
            getInt(process)
        }
    }.getOrDefault(0)
}