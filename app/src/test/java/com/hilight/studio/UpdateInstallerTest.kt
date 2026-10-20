package com.hilight.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateInstallerTest {

    private val release = setOf("aa11")

    private fun reject(
        archivePackage: String? = "com.hilight.studio",
        archiveVersionCode: Long = 21,
        archiveSigners: Set<String> = release,
        archiveSignerHistory: Set<String> = emptySet(),
        installedSigners: Set<String> = release,
    ) = updateRejection(
        installedPackage = "com.hilight.studio",
        installedVersionCode = 20,
        installedSigners = installedSigners,
        archivePackage = archivePackage,
        archiveVersionCode = archiveVersionCode,
        archiveSigners = archiveSigners,
        archiveSignerHistory = archiveSignerHistory,
    )

    @Test
    fun `newer build from the same signer is accepted`() {
        assertNull(reject())
    }

    @Test
    fun `an unparseable download is refused`() {
        assertEquals(UpdateRejection.NOT_AN_APK, reject(archivePackage = null))
    }

    @Test
    fun `another app's apk is refused`() {
        assertEquals(UpdateRejection.WRONG_PACKAGE, reject(archivePackage = "com.example.other"))
    }

    @Test
    fun `same or older version is never offered as an update`() {
        assertEquals(UpdateRejection.NOT_NEWER, reject(archiveVersionCode = 20))
        assertEquals(UpdateRejection.NOT_NEWER, reject(archiveVersionCode = 3))
    }

    @Test
    fun `a differently signed apk is refused before the installer prompt`() {
        assertEquals(UpdateRejection.WRONG_SIGNER, reject(archiveSigners = setOf("bb22")))
        assertEquals(UpdateRejection.WRONG_SIGNER, reject(archiveSigners = emptySet()))
        assertEquals(UpdateRejection.WRONG_SIGNER, reject(installedSigners = emptySet()))
    }

    @Test
    fun `a rotated key is accepted only when its history contains the installed signer`() {
        assertNull(reject(archiveSigners = setOf("bb22"), archiveSignerHistory = setOf("aa11", "bb22")))
        assertEquals(
            UpdateRejection.WRONG_SIGNER,
            reject(archiveSigners = setOf("bb22"), archiveSignerHistory = setOf("cc33", "bb22")),
        )
    }

    @Test
    fun `flashlight hold mirrors the renderer cap and never reports zero`() {
        assertEquals(30_000, flashlightHoldRemainingMs(startedAtMs = 1_000, nowMs = 1_000, holdMs = 30_000))
        assertEquals(20_000, flashlightHoldRemainingMs(startedAtMs = 1_000, nowMs = 11_000, holdMs = 30_000))
        assertEquals(1, flashlightHoldRemainingMs(startedAtMs = 1_000, nowMs = 90_000, holdMs = 30_000))
        // A clock that reads earlier than the start cannot extend the hold.
        assertEquals(30_000, flashlightHoldRemainingMs(startedAtMs = 5_000, nowMs = 1_000, holdMs = 30_000))
    }
}
