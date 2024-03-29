
package me.heizi.kotlinx.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun getLogger(name: String?) =
    LoggerFactory.getLogger(name?:"unknown")?:Unknown.logger
fun getLogger(klz: KClass<out Any>) =
    LoggerFactory.getLogger(klz.java)?:Unknown.logger
object Unknown {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass)
}
@get:JvmName("anyGetLogger")
private val Any?.logger:Logger get() =
    if (this is String) getLogger(this)
    else kotlin.runCatching {
        getLogger((this?:Unknown)::class)
    }.getOrDefault(Unknown.logger)

@JvmName("loggingInfo")
actual fun Any?.println(any: Any?) {
    logger.info(any.toStringNamed())
}
@JvmName("loggingError")
actual fun Any?.error(any: Any?) {
    logger.error(any.toStringNamed())
}
@JvmName("loggingOut")
actual fun Any?.debug(any: Any?) {
    logger.debug(any.toStringNamed())
}