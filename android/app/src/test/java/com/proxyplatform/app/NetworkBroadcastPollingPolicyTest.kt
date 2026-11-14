package com.proxyplatform.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBroadcastPollingPolicyTest {
    @Test
    fun pollsTheFirstTimeAdbBecomesReady() {
        assertTrue(
            shouldPollBroadcastStatus(
                adbReady = true,
                busy = false,
                checkedOnce = false,
                running = false,
            ),
        )
    }

    @Test
    fun doesNotPollWhileAdbIsDisconnected() {
        assertFalse(
            shouldPollBroadcastStatus(
                adbReady = false,
                busy = false,
                checkedOnce = false,
                running = false,
            ),
        )
    }

    @Test
    fun doesNotPollWhileAnOperationIsRunning() {
        assertFalse(
            shouldPollBroadcastStatus(
                adbReady = true,
                busy = true,
                checkedOnce = false,
                running = false,
            ),
        )
    }

    @Test
    fun pollsRepeatedlyWhileBroadcastIsActive() {
        assertTrue(
            shouldPollBroadcastStatus(
                adbReady = true,
                busy = false,
                checkedOnce = true,
                running = true,
            ),
        )
    }

    @Test
    fun stopsPollingWhenBroadcastIsIdleAfterInitialStatus() {
        assertFalse(
            shouldPollBroadcastStatus(
                adbReady = true,
                busy = false,
                checkedOnce = true,
                running = false,
            ),
        )
    }
}
