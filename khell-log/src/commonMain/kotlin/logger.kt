@file:JvmName("Logger")
package me.heizi.kotlinx.logger


import me.heizi.kotlinx.logger.println as Println
import me.heizi.kotlinx.logger.debug as Debug
import me.heizi.kotlinx.logger.error as Error

/**
 * # class as header
 *
 * check if its string plz
 * */

expect fun Any?.println(any: Any?)
expect fun Any?.error(any: Any?)
expect fun Any?.debug(any: Any?)

/**
 * toString
 *
 * just to string
 */
fun Any?.toStringNamed(separator:String = ", ", prefix: String ="[", suffix: String = "]"):String = when(this) {
    is String -> this
    null -> "Nothings"
    is Iterable<*> -> buildString {
        append(prefix)
        this@toStringNamed.forEach {
            append(it.toStringNamed(","))
            append(separator)
        }
        delete(length-separator.length,length)
        append(suffix)
    }
    is Array<*> -> toList().toStringNamed(separator,prefix,suffix)
    else -> toString()
}

fun Any?.println(vararg any: Any?):Unit = this.Println(any.toStringNamed(": ","",""))
fun Any?.error(vararg any: Any?):Unit = this.Error(any.toStringNamed(": ","",""))
fun Any?.debug(vararg any: Any?):Unit = this.Debug(any.toStringNamed(": ","",""))


