package com.hilight.studio

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun `experimental prerelease is offered when its version is newer`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = releases(
                release("v1.0.7-experimental", prerelease = true),
                release("v1.0.6-experimental", prerelease = true),
            ),
        )

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("1.0.7", available.release.versionName)
        assertEquals("v1.0.7-experimental", available.release.tagName)
        assertEquals(
            "https://github.com/Peacetothemountain/hilight-studio/releases/tag/v1.0.7-experimental",
            available.release.pageUrl,
        )
    }

    @Test
    fun `same or older published version reports current`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = releases(
                release("v1.0.5-experimental", prerelease = true),
                release("v1.0.6-experimental", prerelease = true),
            ),
        )

        assertTrue(result is UpdateCheckResult.Current)
        assertEquals("1.0.6", (result as UpdateCheckResult.Current).latestVersionName)

        val developerBuildAhead = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = releases(release("v1.0.5-experimental", prerelease = true)),
        )
        assertTrue(developerBuildAhead is UpdateCheckResult.Current)
        assertEquals(
            "1.0.5",
            (developerBuildAhead as UpdateCheckResult.Current).latestVersionName,
        )
    }

    @Test
    fun `draft and malformed releases are ignored`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = releases(
                release("v99.0.0-experimental", draft = true),
                release("nightly/latest"),
                release("v1.0.7-experimental"),
            ),
        )

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("1.0.7", (result as UpdateCheckResult.Available).release.versionName)
    }

    @Test
    fun `targeted root test release is not offered to ordinary users`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.14",
            response = releases(
                release("test/issue-19-root-cleanup-1.0.15-1", prerelease = true),
                release("v1.0.14-experimental", prerelease = true),
            ),
        )
        assertTrue(result is UpdateCheckResult.Current)
        assertEquals("1.0.14", (result as UpdateCheckResult.Current).latestVersionName)
    }

    @Test
    fun `empty list and invalid response have distinct safe results`() {
        assertEquals(
            UpdateCheckResult.NoPublishedRelease,
            GitHubUpdateChecker.resolve("1.0.6", "[]"),
        )
        assertEquals(
            UpdateCheckResult.Failed,
            GitHubUpdateChecker.resolve("1.0.6", "not json"),
        )
    }

    @Test
    fun `release with apk asset extracts apk download url`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = """
                [
                  {
                    "tag_name": "v1.0.13",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "HiLightStudio1.0.13.apk",
                        "browser_download_url": "https://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.13/HiLightStudio1.0.13.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("1.0.13", available.release.versionName)
        assertEquals(
            "https://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.13/HiLightStudio1.0.13.apk",
            available.release.apkUrl,
        )
    }

    @Test
    fun `stable release upgrades same base version rc build`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.15-rc.1",
            response = releases(
                release("v1.0.15", prerelease = false),
            ),
        )

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("1.0.15", (result as UpdateCheckResult.Available).release.versionName)
    }

    @Test
    fun `untrusted or non-https apk asset url is rejected`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.6",
            response = """
                [
                  {
                    "tag_name": "v1.0.13",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "HiLightStudio1.0.13.apk",
                        "browser_download_url": "http://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.13/HiLightStudio1.0.13.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals(null, available.release.apkUrl)
    }

    @Test
    fun `numeric prerelease identifiers compare by value so rc 10 beats rc 2`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.15-rc.2",
            response = releases(
                release("v1.0.15-rc.2", prerelease = true),
                release("v1.0.15-rc.10", prerelease = true),
            ),
        )

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("v1.0.15-rc.10", available.release.tagName)
        assertEquals("1.0.15", available.release.versionName)
    }

    @Test
    fun `skips unsigned or untrusted apk assets to select valid signed apk`() {
        val result = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.14",
            response = """
                [
                  {
                    "tag_name": "v1.0.15",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "app-release-unsigned.apk",
                        "browser_download_url": "https://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.15/app-release-unsigned.apk"
                      },
                      {
                        "name": "untrusted.apk",
                        "browser_download_url": "https://user@github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.15/untrusted.apk"
                      },
                      {
                        "name": "app-release.apk",
                        "browser_download_url": "https://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.15/app-release.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )

        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals(
            "https://github.com/Peacetothemountain/hilight-studio/releases/download/v1.0.15/app-release.apk",
            available.release.apkUrl,
        )
    }

    @Test
    fun `prerelease comparison follows semver precedence for numeric vs alphanumeric and token length`() {
        val alphaVsNumeric = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.15-rc.1",
            response = releases(
                release("v1.0.15-rc.1", prerelease = true),
                release("v1.0.15-rc.alpha", prerelease = true),
            ),
        )
        assertTrue(alphaVsNumeric is UpdateCheckResult.Available)
        assertEquals("v1.0.15-rc.alpha", (alphaVsNumeric as UpdateCheckResult.Available).release.tagName)

        val longerTokenSet = GitHubUpdateChecker.resolve(
            currentVersionName = "1.0.15-rc.1",
            response = releases(
                release("v1.0.15-rc.1", prerelease = true),
                release("v1.0.15-rc.1.1", prerelease = true),
            ),
        )
        assertTrue(longerTokenSet is UpdateCheckResult.Available)
        assertEquals("v1.0.15-rc.1.1", (longerTokenSet as UpdateCheckResult.Available).release.tagName)
    }

    @Test
    fun `readBoundedText accepts payload up to max cap and rejects oversized payload`() {
        val exactCapPayload = "a".repeat(GitHubUpdateChecker.MAX_RESPONSE_CHARS)
        assertEquals(
            exactCapPayload,
            GitHubUpdateChecker.readBoundedText(StringReader(exactCapPayload)),
        )

        val oversizedPayload = "a".repeat(GitHubUpdateChecker.MAX_RESPONSE_CHARS + 1)
        assertNull(GitHubUpdateChecker.readBoundedText(StringReader(oversizedPayload)))
    }

    private fun releases(vararg entries: String): String = entries.joinToString(
        prefix = "[",
        postfix = "]",
    )

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String = """
        {
          "tag_name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease
        }
    """.trimIndent()
}
