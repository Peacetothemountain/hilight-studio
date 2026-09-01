package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppTrackerTest {

    @Test
    fun `bootstrap history identifies an app that was already open`() {
        val tracker = ForegroundAppTracker()

        tracker.accept("com.discord", "MainActivity", ForegroundLifecycle.RESUMED)

        assertEquals("com.discord", tracker.currentPackage())
    }

    @Test
    fun `closing a temporary overlay restores the app underneath`() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.discord", "MainActivity", ForegroundLifecycle.RESUMED)
        tracker.accept("com.android.systemui", "PermissionDialog", ForegroundLifecycle.RESUMED)

        tracker.accept("com.android.systemui", "PermissionDialog", ForegroundLifecycle.PAUSED)

        assertEquals("com.discord", tracker.currentPackage())
    }

    @Test
    fun `pausing the only resumed activity clears the foreground app`() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.discord", "MainActivity", ForegroundLifecycle.RESUMED)

        tracker.accept("com.discord", "MainActivity", ForegroundLifecycle.PAUSED)

        assertNull(tracker.currentPackage())
    }

    @Test
    fun `overlapping usage queries can replay events without changing the answer`() {
        val tracker = ForegroundAppTracker()
        repeat(2) {
            tracker.accept("com.discord", "MainActivity", ForegroundLifecycle.RESUMED)
            tracker.accept("com.android.systemui", "PermissionDialog", ForegroundLifecycle.RESUMED)
            tracker.accept("com.android.systemui", "PermissionDialog", ForegroundLifecycle.PAUSED)
        }

        assertEquals("com.discord", tracker.currentPackage())
    }

    @Test
    fun `watcher plan separates foreground and face down work`() {
        val foreground = AppRule(
            pkg = "com.discord",
            label = "Discord",
            trigger = Trigger.FOREGROUND,
        )
        val notification = foreground.copy(trigger = Trigger.NOTIFICATION)
        val faceDownNotification = notification.copy(onlyWhenFaceDown = true)

        assertEquals(
            ForegroundWatchPlan(trackForegroundApps = true, trackFaceDown = false),
            ForegroundWatchPolicy.plan(true, listOf(foreground), globalFaceDownOnly = false),
        )
        assertEquals(
            ForegroundWatchPlan(trackForegroundApps = false, trackFaceDown = true),
            ForegroundWatchPolicy.plan(true, listOf(faceDownNotification), false),
        )
        assertEquals(
            ForegroundWatchPlan(trackForegroundApps = true, trackFaceDown = true),
            ForegroundWatchPolicy.plan(true, listOf(foreground), globalFaceDownOnly = true),
        )
        assertFalse(ForegroundWatchPolicy.plan(false, listOf(foreground), true).shouldRun)
        assertFalse(
            ForegroundWatchPolicy.plan(
                true,
                listOf(foreground.copy(enabled = false), notification),
                false,
            ).shouldRun
        )
    }
}
