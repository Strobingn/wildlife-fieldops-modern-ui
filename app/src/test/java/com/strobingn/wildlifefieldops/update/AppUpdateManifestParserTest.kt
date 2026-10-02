package com.strobingn.wildlifefieldops.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManifestParserTest {

    private val mainApk =
        "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk"

    @Test
    fun parsesUpdateJson() {
        val parsed = AppUpdateManifestParser.parseUpdateJson(
            """
            {
              "schemaVersion": 1,
              "channel": "main",
              "tag": "debug-latest",
              "packageName": "com.strobingn.wildlifefieldops",
              "versionCode": 1000647,
              "versionName": "2.5.1-in-app-update",
              "apkUrl": "$mainApk",
              "apkAssetName": "app-debug.apk",
              "commitSha": "abc123def",
              "buildTime": "2026-10-01T23:00:00Z",
              "runNumber": 647,
              "changelog": "- In-app updater\n- Sync flush before install",
              "ignoredExtra": true
            }
            """.trimIndent()
        )
        assertNotNull(parsed)
        assertEquals("main", parsed!!.channel)
        assertEquals(1_000_647, parsed.versionCode)
        assertEquals("2.5.1-in-app-update", parsed.versionName)
        assertEquals(mainApk, parsed.apkUrl)
        assertEquals("abc123def", parsed.commitSha)
        assertTrue(parsed.changelog.contains("In-app updater"))
        assertNull(AppUpdatePolicy.accept(parsed))
    }

    @Test
    fun rejectsInvalidOrHtmlBodies() {
        assertNull(AppUpdateManifestParser.parseUpdateJson(""))
        assertNull(AppUpdateManifestParser.parseUpdateJson("<html>404</html>"))
        assertNull(AppUpdateManifestParser.parseUpdateJson("{not-json"))
        assertNull(
            AppUpdateManifestParser.parseUpdateJson(
                """{"channel":"main","versionCode":0,"versionName":"x","apkUrl":"$mainApk"}"""
            )
        )
    }

    @Test
    fun parsesEmbeddedReleaseBodyBlock() {
        val body = """
            Native Kotlin / Jetpack Compose debug APK.
            <!-- fieldops-update
            versionCode=1000648
            versionName=2.5.1-in-app-update
            channel=main
            tag=debug-latest
            packageName=com.strobingn.wildlifefieldops
            apkUrl=$mainApk
            commitSha=deadbeef
            buildTime=2026-10-01T23:10:00Z
            runNumber=648
            -->
            ## In-app update
            ### Changes since last main build
            - Add update.json
            - Verify CI signer
        """.trimIndent()
        val parsed = AppUpdateManifestParser.parseReleaseBody(body)
        assertNotNull(parsed)
        assertEquals(1_000_648, parsed!!.versionCode)
        assertEquals("2.5.1-in-app-update", parsed.versionName)
        assertEquals("deadbeef", parsed.commitSha)
        assertEquals("- Add update.json\n- Verify CI signer", parsed.changelog)
        assertNull(AppUpdatePolicy.accept(parsed))
    }

    @Test
    fun parsesLegacyMainRunLineAndRejectsBranchRunLine() {
        val mainBody =
            "Native Kotlin / Jetpack Compose debug APK from `60d9f7c8c9384ae639b680fe920c65e1b08cb6e8` (run 646, branch `main`)."
        val parsed = AppUpdateManifestParser.parseReleaseBody(mainBody, mainApk, "debug-latest")
        assertNotNull(parsed)
        assertEquals(1_000_646, parsed!!.versionCode)
        assertEquals("main", parsed.channel)
        assertEquals("60d9f7c8c9384ae639b680fe920c65e1b08cb6e8", parsed.commitSha)

        val branchBody =
            "Native Kotlin / Jetpack Compose debug APK from `abc` (run 700, branch `cursor/in-app-updater-a8e4`)."
        assertNull(AppUpdateManifestParser.parseReleaseBody(branchBody, mainApk, "debug-latest"))
    }

    @Test
    fun githubReleaseJsonPrefersUpdateJsonAssetAndRejectsOtherTags() {
        val withAsset = """
            {
              "tag_name": "debug-latest",
              "body": "run 1, branch `main`",
              "assets": [
                {
                  "name": "update.json",
                  "browser_download_url": "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/update.json"
                },
                {
                  "name": "app-debug.apk",
                  "browser_download_url": "$mainApk"
                }
              ]
            }
        """.trimIndent()
        val lookup = AppUpdateManifestParser.parseGithubReleaseJson(withAsset)
        assertTrue(lookup is GithubReleaseLookup.ManifestJsonUrl)
        assertTrue((lookup as GithubReleaseLookup.ManifestJsonUrl).url.endsWith("/update.json"))

        val otherTag = """
            {"tag_name":"apk-feature-latest","body":"","assets":[]}
        """.trimIndent()
        val rejected = AppUpdateManifestParser.parseGithubReleaseJson(otherTag)
        assertTrue(rejected is GithubReleaseLookup.Unavailable)
    }

    @Test
    fun mapsRateLimitAndOfflineErrors() {
        assertTrue(
            AppUpdateManifestParser.httpErrorMessage(403, "API rate limit exceeded", "0")
                .contains("rate-limiting")
        )
        assertTrue(
            AppUpdateManifestParser.httpErrorMessage(429, "", null)
                .contains("rate-limiting")
        )
        assertTrue(
            AppUpdateManifestParser.networkErrorMessage(RuntimeException("Unable to resolve host api.github.com"))
                .contains("Can't reach GitHub")
        )
    }
}
