package me.heizi.kotlinx.logger

import android.util.Log


actual fun Any?.println(any: Any?) {
    Log.i(TAG,any.toStringNamed())
}
actual fun Any?.error(any: Any?) {
    Log.e(TAG,any.toStringNamed())
}
actual fun Any?.debug(any: Any?) {
    Log.d(TAG,any.toStringNamed())
}


fun String.println(msg:String) { Log.i(this,msg) }
fun String.error(msg:String) { Log.e(this,msg) }
fun String.debug(msg:String) { Log.d(this,msg) }


val Any?.TAG:String get() =
    toStringNamed()