package me.heizi.kotlinx.shell

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "ExampleInstrumentedTest"
/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("me.heizi.kotlinx.shell.test", appContext.packageName)
    }
    @Test
    fun runOnRealEm(){
        runBlocking {
            Log.i(TAG, "runOnRealEm: on blocking")
            launch {
                delay(3000)
                println("after 3 second")
            }
            launch {
                when (val result = su("echo hello world").await()) {
                    is CommandResult.Success -> {
                        Log.i(TAG, "useAppContext: 已经su")
                    }
                    is CommandResult.Failed -> {
                        Log.i(TAG, "runOnRealEm: ${result.processingMessage}")
                        Log.i(TAG, "runOnRealEm: ${result.errorMessage}")
                        Log.i(TAG, "runOnRealEm: ${result.code}")
                        Log.i(TAG, "useAppContext: 失败")
                    }
                }
            }
        }
    }
}