rootProject.file("local.properties").run {
    if (!exists()) {
        require(createNewFile()) {
            "could not create local.properties"
        }
        bufferedWriter().use {
            it.write("""maven_repo_dir=${rootProject.buildDir}
                |# path to your sdk
                |sdk.dir=
            """.trimMargin().replace("""\""","""\\""").replace(":","\\:"))
            it.flush()
        }
    } else {
        bufferedReader().use {
            val s = it.readText()
            s.lineSequence().find {
                (it.startsWith("maven_repo_dir"))
            }?:bufferedWriter().use {
                it.append(s)
                it.append("\n\nmaven_repo_dir=${rootProject.buildDir}".replace("""\""","""\\""").replace(":","\\:"))
                it.flush()
            }
        }
    }
}
