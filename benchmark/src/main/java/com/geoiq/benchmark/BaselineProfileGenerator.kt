package com.geoiq.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.geoiq.lk_vision_demo",
            maxIterations = 5,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            val scrollable = device.wait(Until.findObject(By.scrollable(true)), 5_000)
            scrollable?.run {
                fling(androidx.test.uiautomator.Direction.DOWN)
                device.waitForIdle()
                fling(androidx.test.uiautomator.Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
