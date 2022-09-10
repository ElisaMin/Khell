package me.heizi.koltinx.version


import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension


val ExtensionAware.versions
    get() = object : Versions {
        override val extraPropertiesExtension: ExtraPropertiesExtension
            get() = extensions.extraProperties
    }
//operator fun ExtraPropertiesExtension.get(string: String):String
//    = get(string+".version") as String

interface Versions {
    val extraPropertiesExtension: ExtraPropertiesExtension
    operator fun get(v:String):String =
        extraPropertiesExtension[v+".version"] as String
}