package com.hilight.studio

import com.hilight.core.RendererContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRecoveryTest {
    @Test
    fun `auto recovers root after confirmed exit while adb has not started cleanup`() {
        assertTrue(shouldPreferRecoveredRoot(Transport.AUTO, Transport.ROOT, true,
            Transport.ADB, false, true))
    }

    @Test
    fun `root recovery preserves source ownership explicit selections and fallback cleanup`() {
        assertFalse(shouldPreferRecoveredRoot(Transport.AUTO, Transport.ROOT, false,
            Transport.ADB, false, true))
        assertFalse(shouldPreferRecoveredRoot(Transport.ADB, Transport.ROOT, true,
            Transport.ADB, false, true))
        assertFalse(shouldPreferRecoveredRoot(Transport.SHIZUKU, Transport.ROOT, true,
            Transport.ADB, false, true))
        assertFalse(shouldPreferRecoveredRoot(Transport.AUTO, Transport.SHIZUKU, true,
            Transport.ADB, false, true))
        assertFalse(shouldPreferRecoveredRoot(Transport.AUTO, Transport.ROOT, true,
            Transport.ADB, true, true))
        assertFalse(shouldPreferRecoveredRoot(Transport.AUTO, Transport.ROOT, true,
            Transport.ADB, false, false))
    }

    @Test
    fun `exact stop failure marks root state error only when source owner is root`() {
        assertEquals(
            RootBackend.State.ERROR,
            rootStateAfterExactStopFailure("root", RootBackend.State.AVAILABLE),
        )
        assertEquals(
            RootBackend.State.AVAILABLE,
            rootStateAfterExactStopFailure("adb", RootBackend.State.AVAILABLE),
        )
        assertEquals(
            RootBackend.State.RUNNING,
            rootStateAfterExactStopFailure("adb", RootBackend.State.RUNNING),
        )
    }

    @Test
    fun `safeRootPush targets current root instance and strips target or sanitizes when unready`() {
        val readyStatus = HelperStatus(
            alive = true,
            identityResolved = true,
            pid = 9001,
            uid = 0,
            owner = "root",
            rendererInstanceId = "root-instance-1",
            rendererContractVersion = RendererContract.CONTRACT_VERSION,
            rendererImplementationRevision = RendererContract.IMPLEMENTATION_REVISION,
            rendererStatusSchemaVersion = RendererContract.STATUS_SCHEMA_VERSION,
            rendererClearAlgorithmVersion = RendererContract.CLEAR_ALGORITHM_VERSION,
            rendererReady = true,
        )
        val visible = JSONObject()
            .put("v", 2)
            .put("stateRevision", 12L)
            .put("enabled", true)
            .put("privacyOutputEnabled", false)
            .toString()

        val targeted = safeRootPush(visible, readyStatus)
        assertEquals(AdbPushSafety.TARGETED_CURRENT, targeted.safety)
        assertEquals("root-instance-1", JSONObject(targeted.json).getString("bridgeRendererInstanceId"))

        val unreadyDisabled = JSONObject()
            .put("v", 2)
            .put("stateRevision", 13L)
            .put("enabled", false)
            .put("privacyOutputEnabled", false)
            .put("bridgeRendererInstanceId", "stale-root-id")
            .toString()
        val broadcast = safeRootPush(unreadyDisabled, readyStatus.copy(alive = false))
        assertEquals(AdbPushSafety.BROADCAST_DISABLED, broadcast.safety)
        assertFalse(JSONObject(broadcast.json).has("bridgeRendererInstanceId"))

        val disabledWithAlert = JSONObject(unreadyDisabled)
            .put("alert", JSONObject().put("id", 5L))
            .toString()
        val sanitized = safeRootPush(disabledWithAlert, readyStatus.copy(alive = false))
        assertEquals(AdbPushSafety.SANITIZED_DISABLED, sanitized.safety)
        assertFalse(JSONObject(sanitized.json).has("alert"))
    }
}
