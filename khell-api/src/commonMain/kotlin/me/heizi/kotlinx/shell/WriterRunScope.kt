package me.heizi.kotlinx.shell

import me.heizi.kotlinx.logger.debug
import java.io.OutputStreamWriter

fun OutputStreamWriter.getDefaultRunScope(isEcho: Boolean = false, id:Int):RunScope
    = object : RunScope {
        override fun run(command: String) {
            if (isEcho) {
                write("echo \"$command\" \n")
                flush()
            }
            write(command)
            "shell#$id".debug("command", command)
            write("\n")
            flush()
        }
    }