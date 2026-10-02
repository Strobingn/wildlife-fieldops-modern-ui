package com.strobingn.wildlifefieldops.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogRepository
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import com.strobingn.wildlifefieldops.sync.work.FieldOpsSyncScheduler
import com.strobingn.wildlifefieldops.sync.work.FieldOpsSyncWorkNames
import com.strobingn.wildlifefieldops.sync.work.SyncEnqueueResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remote: AppUpdateRemote,
    private val transport: AppUpdateTransport,
    private val store: AppUpdateStore,
    private val inspector: ApkPackageInspector,
    private val installer: AppUpdateInstaller,
    private val installResults: UpdateInstallResultBus,
    private val syncRepository: SyncRepository,
    private val syncBacklogRepository: SyncBacklogRepository,
    private val fieldOpsSyncScheduler: FieldOpsSyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        AppUpdateUiState(
            currentName = BuildConfig.VERSION_NAME,
            currentCode = BuildConfig.VERSION_CODE
        )
    )
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    @Volatile
    private var pendingApk: File? = null

    @Volatile
    private var busy = false

    /** Session-only. Null after process death so the banner returns on next start. */
    @Volatile
    private var dismissedVersionCode: Int? = null

    init {
        scope.launch {
            hydrateFromStore()
            installResults.events.collect { event -> handleInstallStatus(event) }
        }
    }

    fun onForeground() {
        scope.launch { runCatching { check(force = false) } }
    }

    suspend fun check(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && !AppUpdatePolicy.shouldCheck(store.lastCheckAt(), now, force = false)) {
            hydrateFromStore()
            return
        }
        if (!force && _state.value.phase == AppUpdatePhase.Checking) {
            return
        }
        if (busy && _state.value.phase != AppUpdatePhase.Ready && _state.value.phase != AppUpdatePhase.Idle) {
            return
        }
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Checking,
                lastError = null,
                statusMessage = "Checking GitHub for a newer main build…"
            )
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { remote.fetchLatestMain() }
                .getOrElse { AppUpdateFetchResult.Failed(AppUpdateManifestParser.networkErrorMessage(it)) }
        }
        store.markChecked(now)
        when (result) {
            is AppUpdateFetchResult.Success -> applyManifest(result.manifest, lastCheckedAtMs = now)
            is AppUpdateFetchResult.Unavailable -> _state.update {
                it.copy(
                    phase = AppUpdatePhase.Ready,
                    lastError = null,
                    statusMessage = result.message,
                    updateAvailable = false,
                    showBanner = false,
                    lastCheckedAtMs = now
                )
            }
            is AppUpdateFetchResult.Failed -> _state.update {
                it.copy(
                    phase = AppUpdatePhase.Ready,
                    lastError = result.message,
                    statusMessage = result.message,
                    showBanner = false,
                    lastCheckedAtMs = now
                )
            }
        }
    }

    private suspend fun hydrateFromStore() {
        val lastCheck = store.lastCheckAt()
        val cached = store.lastManifest()
        _state.update { it.copy(lastCheckedAtMs = lastCheck) }
        val phase = _state.value.phase
        val working = phase == AppUpdatePhase.Downloading ||
            phase == AppUpdatePhase.Verifying ||
            phase == AppUpdatePhase.Flushing ||
            phase == AppUpdatePhase.Installing ||
            phase == AppUpdatePhase.AwaitingUnsynced ||
            phase == AppUpdatePhase.AwaitingPermission ||
            phase == AppUpdatePhase.Checking
        if (cached != null && !working) {
            applyManifest(cached, lastCheckedAtMs = lastCheck, persist = false)
        }
    }

    private suspend fun applyManifest(
        manifest: AppUpdateManifest,
        lastCheckedAtMs: Long,
        persist: Boolean = true
    ) {
        if (persist) store.saveManifest(manifest)
        val installedCode = BuildConfig.VERSION_CODE
        val newer = AppUpdatePolicy.isNewerVersion(manifest.versionCode, installedCode)
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Ready,
                latest = manifest,
                lastError = null,
                lastCheckedAtMs = lastCheckedAtMs,
                updateAvailable = newer,
                showBanner = AppUpdatePolicy.shouldShowBanner(
                    updateAvailable = newer,
                    remoteVersionCode = manifest.versionCode,
                    dismissedVersionCode = dismissedVersionCode
                ),
                statusMessage = if (newer) {
                    "A newer main build is available."
                } else {
                    "You're on the latest main build."
                }
            )
        }
    }

    fun dismissBanner() {
        dismissedVersionCode = _state.value.latest?.versionCode
        _state.update { it.copy(showBanner = false) }
    }

    fun dismissDialog() {
        _state.update {
            it.copy(
                showDialog = false,
                showUnknownSourcesPrompt = false
            )
        }
    }

    fun showDialog() {
        _state.update { it.copy(showDialog = true, showBanner = false) }
    }

    fun unknownSourcesIntent(): Intent = installer.unknownSourcesIntent(context)

    fun startUpdate() {
        scope.launch { beginUpdate() }
    }

    fun proceedDespiteUnsynced() {
        scope.launch { installPending(skipSyncWait = true) }
    }

    fun waitAndRetrySync() {
        scope.launch { flushThenInstall() }
    }

    fun onReturnedFromUnknownSources() {
        scope.launch {
            if (!installer.canRequestInstalls(context)) {
                _state.update {
                    it.copy(
                        phase = AppUpdatePhase.Ready,
                        lastError = "Install unknown apps is still off. Turn it on for Wildlife FieldOps, then tap Update again.",
                        statusMessage = "Need permission to install the downloaded APK.",
                        showUnknownSourcesPrompt = false
                    )
                }
                return@launch
            }
            _state.update { it.copy(showUnknownSourcesPrompt = false) }
            if (AppUpdatePolicy.resumeDownloadAfterPermission(resolvedPendingApk() != null)) {
                beginUpdate()
            } else {
                installPending(skipSyncWait = _state.value.pendingUnsynced == 0)
            }
        }
    }

    private suspend fun beginUpdate() {
        val manifest = _state.value.latest
            ?: run {
                check(force = true)
                _state.value.latest
            }
            ?: return fail("No main-build update is available to download.")
        val reject = AppUpdatePolicy.accept(manifest)
        if (reject != null) return fail(reject)

        val installed = withContext(Dispatchers.IO) { inspector.inspectInstalled(context) }
            ?: return fail("Couldn't read this app's signing certificate.")
        if (!ApkInstallVerification.installedCanReceiveMainUpdate(
                installed.signerSha256,
                AppUpdatePolicy.CI_SIGNER_SHA256
            )
        ) {
            return fail(
                "This install was not signed with the Wildlife FieldOps CI key, so a main-build APK " +
                    "cannot replace it in place (Android would require uninstall, which would wipe local data)."
            )
        }

        if (!installer.canRequestInstalls(context)) {
            val alreadyPrompted = store.unknownSourcesPromptShown()
            store.markUnknownSourcesPromptShown()
            _state.update {
                it.copy(
                    phase = AppUpdatePhase.AwaitingPermission,
                    showDialog = true,
                    showUnknownSourcesPrompt = true,
                    statusMessage = if (alreadyPrompted) {
                        "Android still needs 'Install unknown apps' turned on for Wildlife FieldOps."
                    } else {
                        "Android needs a one-time permission to install FieldOps updates from GitHub."
                    }
                )
            }
            return
        }

        busy = true
        try {
            val apk = downloadAndVerify(manifest, installed) ?: return
            pendingApk = apk
            store.setPendingApkPath(apk.absolutePath)
            flushThenInstall()
        } finally {
            busy = false
        }
    }

    private suspend fun downloadAndVerify(
        manifest: AppUpdateManifest,
        installed: ApkIdentity
    ): File? {
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Downloading,
                lastError = null,
                statusMessage = "Downloading the main-build APK…",
                showDialog = true,
                downloadBytes = 0L,
                downloadTotal = 0L
            )
        }
        val dest = File(File(context.cacheDir, "updates"), "app-debug.apk")
        val downloaded = withContext(Dispatchers.IO) {
            runCatching {
                if (dest.exists()) dest.delete()
                transport.download(manifest.apkUrl, dest) { read, total ->
                    _state.update {
                        it.copy(downloadBytes = read, downloadTotal = total)
                    }
                }
                dest
            }.onFailure { error ->
                dest.delete()
                fail(AppUpdateManifestParser.networkErrorMessage(error))
            }.getOrNull()
        } ?: return null

        _state.update {
            it.copy(phase = AppUpdatePhase.Verifying, statusMessage = "Checking package name, version, and signer…")
        }
        val archive = withContext(Dispatchers.IO) { inspector.inspectArchive(context, downloaded) }
        if (archive == null) {
            downloaded.delete()
            fail("The download did not look like a valid FieldOps APK. Nothing was installed.")
            return null
        }
        val verdict = ApkInstallVerification.evaluate(
            downloaded = archive,
            installed = installed,
            expected = ExpectedApk(
                packageName = manifest.packageName.ifBlank { AppUpdatePolicy.EXPECTED_PACKAGE },
                versionCode = manifest.versionCode,
                installedSignerSha256 = installed.signerSha256,
                ciSignerSha256 = AppUpdatePolicy.CI_SIGNER_SHA256
            )
        )
        if (verdict is ApkVerificationResult.Refused) {
            downloaded.delete()
            fail(verdict.reason)
            return null
        }
        return downloaded
    }

    private suspend fun flushThenInstall() {
        val apk = resolvedPendingApk()
        if (apk == null) {
            fail("The downloaded APK is no longer on this phone. Check for updates again.")
            return
        }
        pendingApk = apk
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Flushing,
                showDialog = true,
                statusMessage = "Syncing unsaved field work before installing…"
            )
        }
        withContext(Dispatchers.IO) {
            val enqueue = runCatching { fieldOpsSyncScheduler.enqueueSync() }.getOrNull()
            when (enqueue) {
                is SyncEnqueueResult.Enqueued,
                is SyncEnqueueResult.KeptExisting -> awaitSyncWork(8_000L)
                SyncEnqueueResult.Disabled, null -> runCatching { syncRepository.syncAll() }
            }
        }
        val backlog = withContext(Dispatchers.IO) {
            runCatching { syncBacklogRepository.snapshot() }.getOrNull()
        }
        val pending = backlog?.pendingTotal ?: 0
        if (pending > 0) {
            _state.update {
                it.copy(
                    phase = AppUpdatePhase.AwaitingUnsynced,
                    pendingUnsynced = pending,
                    showDialog = true,
                    statusMessage = backlog?.summaryLine()
                        ?: "$pending items are still unsynced.",
                    lastError = null
                )
            }
            return
        }
        installPending(skipSyncWait = true)
    }

    private suspend fun installPending(skipSyncWait: Boolean) {
        if (!skipSyncWait) {
            flushThenInstall()
            return
        }
        val apk = resolvedPendingApk()
            ?: return fail("The downloaded APK is no longer on this phone. Check for updates again.")
        if (!installer.canRequestInstalls(context)) {
            _state.update {
                it.copy(
                    phase = AppUpdatePhase.AwaitingPermission,
                    showDialog = true,
                    showUnknownSourcesPrompt = true,
                    statusMessage = "Turn on 'Install unknown apps' for Wildlife FieldOps to finish the update."
                )
            }
            return
        }
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Installing,
                showDialog = true,
                pendingUnsynced = 0,
                statusMessage = "Installing in place — local jobs and photos stay on this phone."
            )
        }
        val launch = withContext(Dispatchers.IO) {
            runCatching {
                installer.install(context, apk, AppUpdatePolicy.EXPECTED_PACKAGE)
            }.onFailure { error ->
                fail("Couldn't start the installer: ${error.message ?: error.javaClass.simpleName}")
            }.getOrNull()
        }
        if (launch == AppUpdateInstallLaunch.ExternalInstallerOpened) {
            _state.update {
                it.copy(
                    phase = AppUpdatePhase.Ready,
                    lastError = null,
                    statusMessage = "Android opened the package installer. Confirm it to finish. If you cancel, tap Update to try again."
                )
            }
        }
    }

    private suspend fun resolvedPendingApk(): File? =
        pendingApk?.takeIf { it.isFile }
            ?: store.pendingApkPath()?.let { File(it) }?.takeIf { it.isFile }

    private suspend fun awaitSyncWork(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val finished = runCatching {
                val infos = androidx.work.WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME)
                    .get()
                infos.isEmpty() || infos.all { it.state.isFinished }
            }.getOrDefault(true)
            if (finished) return
            delay(250)
        }
    }

    private fun handleInstallStatus(event: UpdateInstallStatus) {
        when (event.statusCode) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                _state.update {
                    it.copy(statusMessage = "Confirm the Android install prompt to finish.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pendingApk?.delete()
                pendingApk = null
                scope.launch { store.setPendingApkPath(null) }
                _state.update {
                    it.copy(
                        phase = AppUpdatePhase.Ready,
                        showDialog = false,
                        showBanner = false,
                        updateAvailable = false,
                        statusMessage = "Update installed. Local data was kept."
                    )
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _state.update {
                    it.copy(
                        phase = AppUpdatePhase.Ready,
                        statusMessage = "Install was cancelled. Local data is unchanged.",
                        lastError = null
                    )
                }
            }
            else -> {
                fail(event.message?.takeIf { it.isNotBlank() } ?: "Android could not install the update.")
            }
        }
    }

    private fun fail(message: String) {
        _state.update {
            it.copy(
                phase = AppUpdatePhase.Ready,
                lastError = message,
                statusMessage = message,
                showUnknownSourcesPrompt = false
            )
        }
    }
}
