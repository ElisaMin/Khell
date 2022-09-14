package me.heizi.kotlinx.shell

import java.nio.charset.Charset


typealias CommandPrefix = Array<String>

expect val defaultPrefix:CommandPrefix
expect val defaultCharset:Charset
expect val keepCLIPrefix:CommandPrefix