@file:JvmName("khell-android-impl")
package me.heizi.kotlinx.shell

import me.heizi.kotlinx.logger.println
import java.nio.charset.Charset

actual  val defaultPrefix: CommandPrefix
    = arrayOf("sh")
actual  val keepCLIPrefix: CommandPrefix
    get()  = TODO()

actual val defaultCharset:Charset = Charsets.UTF_8