package me.heizi.kotlinx.logger

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

//@RunWith(JUnit4::class)
class LoggerTest {
//    @Test
    fun log():Unit = runBlocking {
        this.println("log_normal")
        this.debug("log_normal")
        this.error("log_normal")
        "test".println("log_normal")
        "test".debug("log_normal")
        "test".error("log_normal")
        println("log_normal","log_normal")
        debug("log_normal","log_normal")
        error("log_normal","log_normal")
        launch {
            println("log_normal","log_normal")
            debug("log_normal","log_normal")
            error("log_normal","log_normal")
            launch {
                println("log_normal","log_normal")
                debug("log_normal","log_normal")
                error("log_normal","log_normal")
            }
            launch {
                println("log_normal","log_normal")
                debug("log_normal","log_normal")
                error("log_normal","log_normal")
            }
        }
    }
}