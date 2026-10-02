package com.strobingn.wildlifefieldops.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateRemoteTest {

    private val mainJson = """
        {
          "schemaVersion": 1,
          "channel": "main",
          "tag": "debug-latest",
          "packageName": "com.strobingn.wildlifefieldops",
          "versionCode": 1000701,
          "versionName": "2.5.1-in-app-update",
          "apkUrl": "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk",
          "commitSha": "aaa",
          "changelog": "- main only"
        }
    """.trimIndent()

    @Test
    fun acceptsDirectMainUpdateJson() {
        val remote = AppUpdateRemote(
            transport = MapTransport(
                mapOf(AppUpdateManifestParser.defaultManifestUrl() to AppUpdateHttpResponse(200, mainJson))
            )
        )
        val result = remote.fetchLatestMain()
        assertTrue(result is AppUpdateFetchResult.Success)
        assertEquals(1_000_701, (result as AppUpdateFetchResult.Success).manifest.versionCode)
    }

    @Test
    fun rejectsBranchChannelEvenIfHostedAtMainUrl() {
        val branchJson = mainJson.replace("\"main\"", "\"feature\"")
        val remote = AppUpdateRemote(
            transport = MapTransport(
                mapOf(AppUpdateManifestParser.defaultManifestUrl() to AppUpdateHttpResponse(200, branchJson))
            )
        )
        val result = remote.fetchLatestMain()
        assertTrue(result is AppUpdateFetchResult.Unavailable)
        assertTrue((result as AppUpdateFetchResult.Unavailable).message.contains("non-main"))
    }

    @Test
    fun fallsBackToReleaseApiWhenUpdateJsonMissing() {
        val api = """
            {
              "tag_name": "debug-latest",
              "body": "from `abc123` (run 650, branch `main`).",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "browser_download_url": "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk"
                }
              ]
            }
        """.trimIndent()
        val remote = AppUpdateRemote(
            transport = MapTransport(
                mapOf(
                    AppUpdateManifestParser.defaultManifestUrl() to AppUpdateHttpResponse(404, "missing"),
                    AppUpdateManifestParser.defaultReleaseApiUrl() to AppUpdateHttpResponse(200, api)
                )
            )
        )
        val result = remote.fetchLatestMain()
        assertTrue(result is AppUpdateFetchResult.Success)
        assertEquals(1_000_650, (result as AppUpdateFetchResult.Success).manifest.versionCode)
    }

    @Test
    fun rateLimitDoesNotCrashAndSkipsApiFallback() {
        val remote = AppUpdateRemote(
            transport = MapTransport(
                mapOf(
                    AppUpdateManifestParser.defaultManifestUrl() to AppUpdateHttpResponse(
                        code = 403,
                        body = "API rate limit exceeded",
                        rateLimitRemaining = "0"
                    )
                )
            )
        )
        val result = remote.fetchLatestMain()
        assertTrue(result is AppUpdateFetchResult.Failed)
        assertTrue((result as AppUpdateFetchResult.Failed).message.contains("rate-limiting"))
    }

    @Test
    fun unreachableHostIsReadable() {
        val remote = AppUpdateRemote(
            transport = object : AppUpdateTransport {
                override fun getText(url: String): AppUpdateHttpResponse {
                    throw java.net.UnknownHostException("Unable to resolve host github.com")
                }
                override fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) = Unit
            }
        )
        val result = remote.fetchLatestMain()
        assertTrue(result is AppUpdateFetchResult.Failed)
        assertTrue((result as AppUpdateFetchResult.Failed).message.contains("Can't reach GitHub"))
    }

    private class MapTransport(
        private val responses: Map<String, AppUpdateHttpResponse>
    ) : AppUpdateTransport {
        override fun getText(url: String): AppUpdateHttpResponse =
            responses[url] ?: AppUpdateHttpResponse(404, "not found")

        override fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) = Unit
    }
}
