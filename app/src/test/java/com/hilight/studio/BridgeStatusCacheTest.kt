package com.hilight.studio

import com.hilight.core.RendererContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeStatusCacheTest {

    private fun statusJson(
        ts: Long,
        pid: Int = 4321,
        uid: Int = 0,
        owner: String = "root",
        instanceId: String = "root-instance-1",
    ): String = JSONObject().apply {
        put("ts", ts)
        put("pid", pid)
        put("uid", uid)
        put("owner", owner)
        put("rendererInstanceId", instanceId)
        put("ledCount", 12)
        put("session", false)
        put("blackClearPending", false)
        put("blackClearTerminal", true)
        put("blackClearUnreleasedFatal", false)
        put("blackClearResult", "completed_unverified")
        put("blackClearAttemptResult", "binder_accepted_unverified")
        put("blackClearStage", "closed")
        put("blackClearTimestampElapsedMs", ts)
        put("blackClearCycleId", 1L)
        put("blackClearCycleSource", "automatic")
        put("blackClearAttemptsUsed", 3)
        put("blackClearAttemptsRemaining", 0)
        put("blackClearStopAttemptAvailable", true)
        put("blackClearCloseFailures", 0)
        put("lightMinUpdatePeriodMs", 33L)
        put("blackClearStrategy", "alpha_black_then_zero")
        put("blackClearStrategyVersion", 1)
        put("receivedStateRevision", 2L)
        put("settledStateRevision", 2L)
        put("releasedStateRevision", 2L)
        put("lastSeenManualBlackClearRequestId", 0L)
        put("lastAcceptedManualBlackClearRequestId", 0L)
        put("privacyObserverEnabled", false)
        put("privacyObserverState", "stopped")
        put("privacyPhase", "inactive")
        put("rendererContractVersion", RendererContract.CONTRACT_VERSION)
        put("rendererImplementationRevision", RendererContract.IMPLEMENTATION_REVISION)
        put("rendererStatusSchemaVersion", RendererContract.STATUS_SCHEMA_VERSION)
        put("rendererClearAlgorithmVersion", RendererContract.CLEAR_ALGORITHM_VERSION)
        put("heartbeatElapsedRealtimeMs", ts)
    }.toString()

    @Test
    fun `forget rejects subsequent on-disk status with newer timestamp for same instance`() {
        val cache = Bridge.BridgeStatusCache()
        val initial = cache.read(statusJson(ts = 1_000L), nowMs = 1_000L, nowElapsedMs = 1_000L)
        assertTrue(initial.alive)
        assertEquals("root-instance-1", initial.rendererInstanceId)

        cache.forget("root-instance-1")

        // Even if the exiting process flushed a final heartbeat with a newer timestamp before dying,
        // that forgotten rendererInstanceId must never resurrect as a live status.
        val afterFinalFlush = cache.read(statusJson(ts = 1_500L), nowMs = 1_500L, nowElapsedMs = 1_500L)
        assertFalse(afterFinalFlush.alive)
        assertEquals(-1, afterFinalFlush.pid)
        assertEquals("", afterFinalFlush.rendererInstanceId)

        // A genuinely new successor instance is accepted normally.
        val successor = cache.read(
            statusJson(ts = 2_000L, pid = 5432, instanceId = "root-instance-2"),
            nowMs = 2_000L,
            nowElapsedMs = 2_000L,
        )
        assertTrue(successor.alive)
        assertEquals(5432, successor.pid)
        assertEquals("root-instance-2", successor.rendererInstanceId)
    }

    @Test
    fun `safeRootPush and safeAdbPush strip target on disabled broadcast and sanitize alert-bearing disabled state`() {
        val unreadyRoot = HelperStatus(alive = false, owner = "root")
        val unreadyAdb = HelperStatus(alive = false, owner = "adb")

        val scopedDisabled = JSONObject().apply {
            put("v", 2)
            put("stateRevision", 7L)
            put("enabled", false)
            put("privacyOutputEnabled", false)
            put("bridgeRendererInstanceId", "root-old")
        }.toString()

        val rootBroadcast = safeRootPush(scopedDisabled, unreadyRoot)
        assertEquals(AdbPushSafety.BROADCAST_DISABLED, rootBroadcast.safety)
        assertFalse(JSONObject(rootBroadcast.json).has("bridgeRendererInstanceId"))

        val adbBroadcast = safeAdbPush(scopedDisabled, unreadyAdb)
        assertEquals(AdbPushSafety.BROADCAST_DISABLED, adbBroadcast.safety)
        assertFalse(JSONObject(adbBroadcast.json).has("bridgeRendererInstanceId"))

        val disabledWithAlert = JSONObject().apply {
            put("v", 2)
            put("stateRevision", 8L)
            put("enabled", false)
            put("privacyOutputEnabled", false)
            put("alert", JSONObject().put("id", 99L).put("pattern", "pulse"))
        }.toString()

        val rootSanitized = safeRootPush(disabledWithAlert, unreadyRoot)
        assertEquals(AdbPushSafety.SANITIZED_DISABLED, rootSanitized.safety)
        assertFalse(JSONObject(rootSanitized.json).has("alert"))

        val adbSanitized = safeAdbPush(disabledWithAlert, unreadyAdb)
        assertEquals(AdbPushSafety.SANITIZED_DISABLED, adbSanitized.safety)
        assertFalse(JSONObject(adbSanitized.json).has("alert"))
    }

    @Test
    fun `preemptive forget suppresses unseen instance id and respects bounded lru eviction`() {
        val cache = Bridge.BridgeStatusCache()
        cache.forget("root-preempted-1")

        val suppressed = cache.read(
            statusJson(ts = 3_000L, instanceId = "root-preempted-1"),
            nowMs = 3_000L,
            nowElapsedMs = 3_000L,
        )
        assertFalse(suppressed.alive)
        assertEquals(-1, suppressed.pid)
        assertTrue(suppressed.identityResolved)

        // Evict root-preempted-1 by forgetting 64 newer valid instance IDs.
        for (i in 1..64) {
            cache.forget("root-evict-$i")
        }

        // The most recently forgotten entries remain suppressed.
        val recentSuppressed = cache.read(
            statusJson(ts = 4_000L, instanceId = "root-evict-64"),
            nowMs = 4_000L,
            nowElapsedMs = 4_000L,
        )
        assertFalse(recentSuppressed.alive)
    }
}
