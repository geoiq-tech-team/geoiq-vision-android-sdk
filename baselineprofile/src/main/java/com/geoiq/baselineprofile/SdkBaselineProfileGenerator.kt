package com.geoiq.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.geoiq.geoiq_android_lk_vision_bot_sdk"
private const val UI_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 45_000L

/**
 * Records the SDK's hot paths through the sample app: connect, event delivery, camera/mic
 * publish, camera flip, local video rendering, and disconnect.
 *
 * Only the SDK module consumes the result (filtered to SDK/LiveKit/WebRTC classes in
 * GEOIQ-ANDROID-LK-VISION-BOT-SDK/build.gradle.kts); the sample app ships no profile of its own.
 *
 * Not covered: sendFile() — the sample only reaches it through the system file picker.
 */
@RunWith(AndroidJUnit4::class)
class SdkBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun sessionJourney() = rule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 3,
    ) {
        // Runtime permissions would otherwise block on a system dialog.
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.CAMERA")
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.RECORD_AUDIO")

        pressHome()
        startActivityAndWait()

        device.clickWhenReady(By.text("Connect"))
        device.waitOrFail(By.textStartsWith("Connected:"), CONNECT_TIMEOUT_MS, "room connection")

        device.clickWhenReady(By.desc("Camera Off"))
        device.waitOrFail(By.desc("Camera On"), UI_TIMEOUT_MS, "camera publish")

        device.clickWhenReady(By.desc("Mic Off"))
        device.waitOrFail(By.desc("Mic On"), UI_TIMEOUT_MS, "microphone publish")

        // Let frames flow through the capture → render pipeline.
        Thread.sleep(3_000)

        device.clickWhenReady(By.desc("Flip"))
        device.waitOrFail(By.textContains("Camera flipped"), UI_TIMEOUT_MS, "camera flip")
        Thread.sleep(2_000)

        device.clickWhenReady(By.desc("Camera On"))
        device.waitOrFail(By.desc("Camera Off"), UI_TIMEOUT_MS, "camera unpublish")
        device.clickWhenReady(By.desc("Mic On"))
        device.waitOrFail(By.desc("Mic Off"), UI_TIMEOUT_MS, "microphone unpublish")

        device.clickWhenReady(By.text("Disconnect"))
        device.waitOrFail(By.text("Disconnected"), UI_TIMEOUT_MS, "disconnect")
    }
}

private fun UiDevice.clickWhenReady(selector: androidx.test.uiautomator.BySelector) {
    val node = wait(Until.findObject(selector.enabled(true)), UI_TIMEOUT_MS)
        ?: error("UI element not found or not enabled: $selector")
    node.click()
}

private fun UiDevice.waitOrFail(
    selector: androidx.test.uiautomator.BySelector,
    timeoutMs: Long,
    step: String,
) {
    check(wait(Until.hasObject(selector), timeoutMs)) { "Timed out waiting for $step ($selector)" }
}
