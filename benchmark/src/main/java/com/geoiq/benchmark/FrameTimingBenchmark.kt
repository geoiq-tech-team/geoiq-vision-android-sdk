package com.geoiq.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollMainScreen() {
        benchmarkRule.measureRepeated(
            packageName = "com.geoiq.lk_vision_demo",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
            }
        ) {
            val column = device.wait(
                Until.findObject(By.scrollable(true)),
                5_000
            ) ?: return@measureRepeated

            repeat(3) {
                column.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                column.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
