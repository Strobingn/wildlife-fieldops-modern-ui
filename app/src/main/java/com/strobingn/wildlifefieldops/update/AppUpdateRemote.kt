package com.strobingn.wildlifefieldops.update

interface AppUpdateTransport {
    fun getText(url: String): AppUpdateHttpResponse
    fun download(url: String, dest: java.io.File, onProgress: (read: Long, total: Long) -> Unit)
}

class AppUpdateRemote(
    private val transport: AppUpdateTransport,
    private val manifestUrl: String = AppUpdateManifestParser.defaultManifestUrl(),
    private val releaseApiUrl: String = AppUpdateManifestParser.defaultReleaseApiUrl()
) {
    @javax.inject.Inject
    constructor(transport: AppUpdateTransport) : this(
        transport,
        AppUpdateManifestParser.defaultManifestUrl(),
        AppUpdateManifestParser.defaultReleaseApiUrl()
    )

    fun fetchLatestMain(): AppUpdateFetchResult {
        return try {
            val direct = getManifest(manifestUrl)
            when (direct) {
                is AppUpdateFetchResult.Success -> direct
                is AppUpdateFetchResult.Failed -> {
                    if (direct.message.contains("rate-limiting", ignoreCase = true)) direct
                    else fetchFromReleaseApi(fallback = direct)
                }
                is AppUpdateFetchResult.Unavailable -> fetchFromReleaseApi(fallback = direct)
            }
        } catch (t: Throwable) {
            AppUpdateFetchResult.Failed(AppUpdateManifestParser.networkErrorMessage(t))
        }
    }

    private fun fetchFromReleaseApi(fallback: AppUpdateFetchResult): AppUpdateFetchResult {
        val api = try {
            transport.getText(releaseApiUrl)
        } catch (t: Throwable) {
            return fallbackIfApiFails(fallback, AppUpdateManifestParser.networkErrorMessage(t))
        }
        if (api.code !in 200..299) {
            return fallbackIfApiFails(
                fallback,
                AppUpdateManifestParser.httpErrorMessage(api.code, api.body, api.rateLimitRemaining)
            )
        }
        return when (val lookup = AppUpdateManifestParser.parseGithubReleaseJson(api.body)) {
            is GithubReleaseLookup.ManifestJsonUrl -> getManifest(lookup.url)
            is GithubReleaseLookup.Manifest -> accept(lookup.manifest)
            is GithubReleaseLookup.Unavailable -> AppUpdateFetchResult.Unavailable(lookup.message)
            is GithubReleaseLookup.Failed -> AppUpdateFetchResult.Failed(lookup.message)
        }
    }

    private fun fallbackIfApiFails(
        fallback: AppUpdateFetchResult,
        apiMessage: String
    ): AppUpdateFetchResult {
        return when (fallback) {
            is AppUpdateFetchResult.Failed -> AppUpdateFetchResult.Failed(apiMessage)
            is AppUpdateFetchResult.Unavailable -> AppUpdateFetchResult.Unavailable(fallback.message)
            is AppUpdateFetchResult.Success -> fallback
        }
    }

    private fun getManifest(url: String): AppUpdateFetchResult {
        val response = transport.getText(url)
        if (response.code !in 200..299) {
            return if (response.code == 404) {
                AppUpdateFetchResult.Unavailable(
                    AppUpdateManifestParser.httpErrorMessage(
                        response.code,
                        response.body,
                        response.rateLimitRemaining
                    )
                )
            } else {
                AppUpdateFetchResult.Failed(
                    AppUpdateManifestParser.httpErrorMessage(
                        response.code,
                        response.body,
                        response.rateLimitRemaining
                    )
                )
            }
        }
        val parsed = AppUpdateManifestParser.parseUpdateJson(response.body)
            ?: return AppUpdateFetchResult.Unavailable("The main release update.json could not be parsed.")
        return accept(parsed)
    }

    private fun accept(manifest: AppUpdateManifest): AppUpdateFetchResult {
        val reason = AppUpdatePolicy.accept(manifest)
        return if (reason != null) AppUpdateFetchResult.Unavailable(reason)
        else AppUpdateFetchResult.Success(manifest)
    }
}
