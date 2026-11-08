package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.DerafshSourceManager
import com.v2ray.ang.handler.LicenseManager
import com.v2ray.ang.handler.MaintenanceStatus
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.StaleProfileTracker
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.getChainNamesReferencing
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null || delayMillis == server.testDelayMillis) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis)
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null || delayMillis == row.testDelayMillis) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis)
    }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * Presentation-only connecting phase between an accepted start request and the next
     * authoritative service-state broadcast; it carries no service control meaning.
     */
    private val _isStartPending = MutableStateFlow(false)
    val isStartPending: StateFlow<Boolean> = _isStartPending.asStateFlow()

    /** Derafsh Kaviani: the honest connect-flow phase behind the status label. */
    private val _connectPhase = MutableStateFlow(ConnectPhase.None)
    val connectPhase: StateFlow<ConnectPhase> = _connectPhase.asStateFlow()

    /** Derafsh: index of the active built-in source shown in the source switcher. */
    private val _derafshActiveSource = MutableStateFlow(DerafshSourceManager.activeUiIndex())
    val derafshActiveSource: StateFlow<Int> = _derafshActiveSource.asStateFlow()

    /**
     * Derafsh premium: whether a valid (or in-grace) license unlocks premium entries.
     * Read at interaction time — NOT cached — so returning from the activation screen
     * takes effect immediately without recreating the ViewModel.
     */
    fun isPremiumUnlocked(): Boolean = LicenseManager.isPremiumUnlocked(app)

    /**
     * Derafsh premium (task 9): point the built-in subscription at the locked premium
     * pool endpoint and refresh immediately. The URL embeds the stored license code and
     * the canonical device id, so only this device can fetch the pool.
     */
    fun selectPremiumSource() {
        if (!LicenseManager.isPremiumUnlocked(app)) return
        val url = LicenseManager.premiumSubUrl(app) ?: return
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    DerafshSourceManager.setPremiumSourceActive(true)
                    _derafshActiveSource.value = DerafshSourceManager.PREMIUM_UI_INDEX

                    val subId = AppConfig.DEFAULT_SUBSCRIPTION_ID
                    val item = dataSource.getSubscriptionItem(subId) ?: SubscriptionItem(
                        enabled = true,
                        autoUpdate = true,
                        updateInterval = 240, // every 4 hours
                    )
                    item.remarks = dataSource.getString(R.string.derafsh_premium_source_name)
                    item.url = url
                    MmkvManager.encodeSubscription(subId, item)

                    val result = dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                        if (pendingTestGuids(dataSource.getServerGuidList("")).isNotEmpty()) {
                            MaintenanceStatus.beginSession()
                            testAllRealPing(allGroups = true, skipTested = true)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Derafsh premium source switch failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    /**
     * Derafsh premium: the license died while the built-in subscription still points at
     * the locked pool — fall back to the last free source (reuses the regular switch
     * pipeline: URL rewrite + immediate fetch + toasts).
     */
    fun onPremiumLicenseLost() {
        if (!DerafshSourceManager.isPremiumSourceActive()) return
        switchDerafshSourceAsync(DerafshSourceManager.activeIndex())
    }

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null

    // The test protocol is touched from BOTH the main thread (event collector, UI actions)
    // and IO threads (startup maintenance, source switch, fastest-server pick all enter
    // testAllRealPing/cancelAllPing off-main): the pending-result map must be a concurrent
    // map (a plain LinkedHashMap once raced a main-thread toMap() against an IO clear()
    // -> ConcurrentModificationException crash) and the job/timestamp fields must be
    // volatile so the IO side sees the latest cancellation state.
    @Volatile
    private var testResultFlushJob: Job? = null
    private val pendingTestResults: MutableMap<String, Long> = ConcurrentHashMap()

    private val testRequests = MainTestRequests()
    @Volatile
    private var bulkTestJob: Job? = null

    /** Pending fastest-server pick: a dedicated bulk test whose finish/cancel completes the deferred. */
    private class FastestPick(val id: String, val deferred: CompletableDeferred<Unit>)

    @Volatile
    private var fastestPick: FastestPick? = null

    /**
     * When the last full-pool bulk test finished (startup maintenance pass or a previous
     * pick). Within [Companion.FASTEST_PICK_FRESH_WINDOW_MS] the fastest-server pick reads
     * those persisted delays directly instead of re-testing every config on connect.
     */
    @Volatile
    private var lastFullTestFinishAt = 0L

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        viewModelScope.launch(ioDispatcher) {
            // Derafsh: results persist across opens now — a stored speed-test result is
            // kept until it expires (DERAFSH_TEST_FRESH_MS), so reopening the app shows
            // the badges immediately and the startup pass only tests configs that are
            // new or stale instead of wiping and re-testing the whole pool every time.
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab()
        }
    }

    // ---------- Session countdown ----------
    // The daemon owns the deadline; the UI only reads the persisted value from multiprocess MMKV.
    private val _sessionDeadlineMillis = MutableStateFlow(dataSource.getSessionDeadlineMillis())
    val sessionDeadlineMillis: StateFlow<Long> = _sessionDeadlineMillis.asStateFlow()

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunningState(true)
            }

            is MainServiceEvent.StateStartFailure -> {
                if (!event.message.isNullOrBlank()) {
                    toastError(event.message)
                } else {
                    toastError(R.string.toast_services_failure)
                }
                updateRunningState(false)
            }

            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning || !testRequests.completeCurrent(event.requestId)) return
                _uiState.update { it.copy(isTesting = testRequests.isTesting, status = MainStatus.ConnectionTest(event.result)) }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                fastestPick?.takeIf { it.id == request.id }?.deferred?.complete(Unit)
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    fastestPick?.takeIf { it.id == event.requestId }?.deferred?.complete(Unit)
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }

            MainServiceEvent.SessionExpired -> {
                // The daemon tears the core down after the deadline; the stop broadcast follows.
                _sessionDeadlineMillis.value = 0L
                _uiState.update { it.copy(sessionTimeUp = true) }
            }

            is MainServiceEvent.SessionExtended -> {
                _sessionDeadlineMillis.value = event.deadlineMillis
                _uiState.update { it.copy(sessionTimeUp = false) }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val visibleGroupId = _uiState.value.selectedGroupId
        val (updates, broken) = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            // Derafsh: configs whose fresh result just failed are pruned from storage while
            // the pass is still streaming (see removeBrokenConfigsLive) — prune the cached
            // group lists in the same beat so the rows vanish together.
            val broken = removeBrokenConfigsLive(drained)
            fun prune(cached: List<ServersCache>): List<ServersCache> =
                if (broken.isEmpty()) cached else cached.filter { it.guid !in broken }
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(prune(cached), drained)
            }
            // A full-pool pass (group "") streams results for every group: keep the
            // visible tab's cache fresh too so its badges tick in live.
            if (request.groupId.isEmpty() && visibleGroupId.isNotEmpty()) {
                groupDataCache[visibleGroupId]?.let { cached ->
                    groupDataCache[visibleGroupId] = applyTestDelayResults(prune(cached), drained)
                }
            }
            drained to broken
        }
        if (updates.isEmpty()) return
        // Derafsh: feed the drained results into the stale tracker (consecutive-failure
        // streaks + stale set). Mark-only: the tracker never deletes or moves profiles.
        StaleProfileTracker.recordTestResults(updates, broken)
        if (testRequests.bulk?.id != request.id) return
        fun pruneServers(servers: List<ServersCache>): List<ServersCache> =
            if (broken.isEmpty()) servers else servers.filter { it.guid !in broken }
        fun pruneRows(rows: List<ServerRowUiModel>): List<ServerRowUiModel> =
            if (broken.isEmpty()) rows else rows.filter { it.guid !in broken }
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(pruneServers(current.servers), updates),
                rows = applyTestDelayResultsToRows(pruneRows(current.rows), updates),
            )
        }
        if (request.groupId.isEmpty() && visibleGroupId.isNotEmpty()) {
            mutableServerGroupState(visibleGroupId).update { current ->
                current.copy(
                    servers = applyTestDelayResults(pruneServers(current.servers), updates),
                    rows = applyTestDelayResultsToRows(pruneRows(current.rows), updates),
                )
            }
        }
    }

    /**
     * Live-removes configs whose fresh result just failed (delay < 0) while the pass is
     * still streaming, so dead servers vanish the moment they are detected instead of
     * lingering until the end-of-pass sweep. Storage sweep semantics are mirrored:
     * built-in pool configs (active source + overflow chunk groups) always go — the pool
     * self-cleans; user-subscription configs go only when
     * [AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST] is enabled. Never-tested configs
     * (delay 0) are never touched, and the removal re-checks the stored affiliation so a
     * stale read can never delete a healthy or unknown config.
     *
     * @param updates The drained guid → delay results of this flush beat.
     * @return The guids actually removed from storage.
     */
    private fun removeBrokenConfigsLive(updates: Map<String, Long>): Set<String> {
        val broken = updates.filterValues { it < 0L }.keys
        if (broken.isEmpty()) return emptySet()
        val autoRemoveUserSubs =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        val chunkPrefix = AngConfigManager.chunkGroupPrefixFor(AppConfig.DEFAULT_SUBSCRIPTION_ID)
        val removed = mutableSetOf<String>()
        for (guid in broken) {
            val profileSub = MmkvManager.decodeServerConfig(guid)?.subscriptionId
                ?.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID } ?: continue
            val isBuiltinPool = profileSub == AppConfig.DEFAULT_SUBSCRIPTION_ID ||
                    profileSub.startsWith(chunkPrefix)
            if (isBuiltinPool || autoRemoveUserSubs) {
                if (dataSource.removeInvalidServerByGuid(guid) > 0) removed.add(guid)
            }
        }
        return removed
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    /**
     * Every server row across all groups flattened for the server picker dialog, keyed by GUID.
     * When the aggregate "All" group is enabled the same server appears in several groups, so
     * duplicates collapse to the first occurrence.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val allServerRows: StateFlow<List<ServerRowUiModel>> = uiState
        .map { it.groups.map { group -> group.id } }
        .distinctUntilChanged()
        .flatMapLatest { groupIds ->
            if (groupIds.isEmpty()) {
                flowOf(emptyList<ServerRowUiModel>())
            } else {
                combine(groupIds.map { mutableServerGroupState(it) }) { states ->
                    states.flatMap { it.rows }.distinctBy { it.guid }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    // ---------- Stale-profile display filter (Derafsh) ----------
    /** Server-picker view filter only: show (default) or hide stale rows. Removes nothing. */
    private val _showStaleProfiles = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_STALE_PROFILES, true)
    )
    val showStaleProfiles: StateFlow<Boolean> = _showStaleProfiles.asStateFlow()

    /** The picker dialog's rows: [allServerRows] with stale rows hidden when the filter is off. */
    internal val pickerServerRows: StateFlow<List<ServerRowUiModel>> = combine(
        allServerRows,
        _showStaleProfiles,
    ) { rows, showStale ->
        if (showStale) rows else rows.filterNot { it.isStale }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList(),
    )

    private fun setShowStaleProfiles(show: Boolean) {
        _showStaleProfiles.value = show
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_STALE_PROFILES, show)
    }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub(uiState.value.selectedGroupId)
            is MainAction.UpdateGroupSubscription -> importConfigViaSub(action.groupId)
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            MainAction.DismissSessionTimeUp -> dismissSessionTimeUp()
            is MainAction.SwitchDerafshSource -> switchDerafshSourceAsync(action.index)
            is MainAction.SetShowStale -> setShowStaleProfiles(action.show)
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent,
            is MainAction.OpenSubscriptionLink,
            MainAction.ExtendSessionWithAd -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
                runStartupMaintenance()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    /**
     * Derafsh startup maintenance, right after the first page is on screen:
     *
     * 1) Subscription refresh — silent fetch of every enabled source, due every
     *    [AppConfig.AUTO_MAINTENANCE_INTERVAL_MS] (4h) and on the very first open. Announced
     *    on the main-screen maintenance card ("refreshing «source»…").
     * 2) Speed test — EVERY open: the whole pool is re-tested with a live percentage on the
     *    card, so the delay badges are fresh and the fastest-server pick at connect starts
     *    from real data. A connect press while the test runs simply supersedes it.
     */
    private suspend fun runStartupMaintenance() {
        val now = System.currentTimeMillis()
        val last = dataSource.getLastAutoMaintenance()
        val subRefreshDue = last <= 0L || now - last >= AppConfig.AUTO_MAINTENANCE_INTERVAL_MS

        if (subRefreshDue) {
            // Announce the session on the main screen: "refreshing subscriptions" first, then
            // the bulk speed test takes over the card with a live percentage from the test service.
            MaintenanceStatus.beginSession()
            try {
                val result = dataSource.updateConfigViaSubAll { remarks ->
                    MaintenanceStatus.setSubLabel(remarks)
                }
                if (result.configCount > 0) {
                    setupGroupTab(forceRefresh = true).join()
                    refreshSelectedGuid()
                }
            } catch (cancelled: CancellationException) {
                MaintenanceStatus.abandon()
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Auto subscription refresh failed", e)
            }
            dataSource.setLastAutoMaintenance(now)
        }

        // Auto pass: only configs without a fresh result are tested — new imports and
        // results older than DERAFSH_TEST_FRESH_MS. Configs tested recently (this open or
        // a previous one) keep their badges, so a quick reopen doesn't re-test 300+
        // servers again; the manual test button still re-tests everything on demand.
        if (pendingTestGuids(dataSource.getServerGuidList("")).isNotEmpty()) {
            testAllRealPing(allGroups = true, skipTested = true)
        } else if (subRefreshDue) {
            // Nothing new to test — close the session so the card never hangs in "updating".
            MaintenanceStatus.finish()
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val filteredServers = applyKeywordFilter(servers)
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = filteredServers,
            rows = buildServerRows(groupId, filteredServers)
        )
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val staleGuids = StaleProfileTracker.staleGuidSet()
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty(),
                staleGuids = staleGuids,
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(
                        id = it.guid,
                        remarks = it.subscription.remarks,
                        usageInfo = it.subscription.usageInfo?.let { info ->
                            SubscriptionInfoParser.withWebPageFallback(info, it.subscription.url)
                        },
                    )
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer(),
                    )
                }
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub(subId: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    /** In-flight guard: onCreate + onStart fire back-to-back on cold start — fetch once. */
    private var openRefreshJob: Job? = null

    /**
     * Derafsh (v1.2.56, hardened in v1.2.57): silently refresh the built-in source
     * subscription when the app opens or returns to the foreground. The 4-hour
     * WorkManager cadence alone left the pool stale between runs — badly so on Iranian
     * OEM battery savers that defer background work — so users opened the app to the
     * same dead configs every time (user report: «ساب یک اصلا آپدیت نمیشه وقتی برنامه
     * رو باز می‌کنم»). This runs the exact per-subscription importer the manual refresh
     * uses (chunked, av-declared since v1.2.55) for the built-in group only.
     *
     * v1.2.57 fixes two real-world failure loops:
     *  - the 15-minute throttle timestamp is stamped ONLY on success. v1.2.56 stamped it
     *    before the fetch, so a single failed attempt (first open while offline, network
     *    not yet up after install) suppressed every retry for a quarter of an hour and
     *    the user kept staring at an empty server list;
     *  - failures are no longer fully silent: a toast says the refresh did not happen
     *    instead of leaving an unexplained "No data available".
     */
    fun refreshBuiltInSourceOnOpen() {
        val subId = AppConfig.DEFAULT_SUBSCRIPTION_ID
        val item = dataSource.getSubscriptionItem(subId) ?: return
        if (!item.enabled || item.url.isEmpty()) return
        val now = System.currentTimeMillis()
        val throttleMillis = AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES * 60_000L
        if (now - MmkvManager.decodeSettingsLong(AppConfig.PREF_LAST_OPEN_SUB_REFRESH, 0L) < throttleMillis) return
        if (openRefreshJob?.isActive == true) return
        openRefreshJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                try {
                    val result = dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    if (result.successCount > 0) {
                        // Success-only throttle (v1.2.57): a failed attempt must never
                        // suppress the next open's retry.
                        MmkvManager.encodeSettings(AppConfig.PREF_LAST_OPEN_SUB_REFRESH, now)
                        // Reset the 4h periodic timer the same way a manual refresh does.
                        SubscriptionUpdater.updateLastUpdatedAndReschedule(app, subId)
                        if (result.configCount > 0) {
                            setupGroupTab(forceRefresh = true)
                            refreshSelectedGuid()
                        }
                        // v1.2.58: say when the pool came from the bundled snapshot so a
                        // possibly stale list is never mistaken for a live refresh.
                        if (AngConfigManager.lastImportUsedBundledBackup) {
                            toast(R.string.toast_bundled_pool_loaded)
                        }
                    } else {
                        LogUtil.w(AppConfig.TAG, "On-open built-in source refresh did not succeed")
                        toastError(R.string.toast_builtin_refresh_failed)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "On-open built-in source refresh failed", e)
                    toastError(R.string.toast_builtin_refresh_failed)
                }
            }
        }
    }

    /**
     * Derafsh source switcher: point the single built-in subscription at the chosen source
     * and refresh it immediately. Only the active source is ever updated — switching reuses
     * the same subscription id, so the previous pool's servers are replaced in place.
     *
     * v1.2.52 monetization: only [AppConfig.DERAFSH_FREE_SOURCE_COUNT] sources stay free;
     * every other index (except the already-active one, so no install is stranded) requires
     * a premium license. The picker routes locked taps to the premium gate — this check is
     * the server-side guard behind it.
     */
    private fun switchDerafshSourceAsync(index: Int) {
        if (index >= AppConfig.DERAFSH_FREE_SOURCE_COUNT &&
            index != DerafshSourceManager.activeIndex() &&
            !LicenseManager.isPremiumUnlocked(app)
        ) {
            return
        }
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    DerafshSourceManager.setActiveIndex(index)
                    DerafshSourceManager.setPremiumSourceActive(false)
                    _derafshActiveSource.value = index

                    val subId = AppConfig.DEFAULT_SUBSCRIPTION_ID
                    val item = dataSource.getSubscriptionItem(subId) ?: SubscriptionItem(
                        enabled = true,
                        autoUpdate = true,
                        updateInterval = 240, // every 4 hours
                    )
                    item.remarks = dataSource.getString(R.string.derafsh_source_name, index + 1)
                    item.url = DerafshSourceManager.sourceUrl(index)
                    MmkvManager.encodeSubscription(subId, item)

                    val result = dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                        // Kick the real-delay pass right away for the configs that still
                        // need one. Fresh results carried over from a previous import are
                        // kept, so switching back to a recently-tested source re-tests
                        // nothing — the pass only covers genuinely new or stale configs.
                        // The card takes over only when there is something to test.
                        if (pendingTestGuids(dataSource.getServerGuidList("")).isNotEmpty()) {
                            MaintenanceStatus.beginSession()
                            testAllRealPing(allGroups = true, skipTested = true)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Derafsh source switch failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            // O4 cheap guard: resolve the victim's remark and scan the chains
            // BEFORE removal lands; warn NON-blockingly if a chain still holds
            // it as a member (members are stored by remark, so the hop would
            // silently drop at connect).
            val remarks = MmkvManager.decodeServerConfig(guid)?.remarks.orEmpty()
            val chains = getChainNamesReferencing(remarks)
            dataSource.removeServer(guid)
            if (chains.isNotEmpty()) {
                val joined = chains.joinToString(", ")
                toast(getString(R.string.toast_chain_reference_warning, joined))
                LogUtil.w(message = "Deleted profile '$remarks' is referenced by proxy chain(s): $joined — the matching hop(s) will be dropped at connect time")
            }
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        if (keywordFilter.isNotBlank()) {
            // The visible rows are a filtered SUBSET of the group; persisting a subset
            // order would rewrite the whole group's index from it. Display-only move.
            return
        }
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            // Atomic read-modify-write against the CURRENT index under the profile index
            // lock: configs committed by a concurrent refresh after the drag snapshot
            // survive the persist (A13, via the cycle-3 updateServerListAtomic helper).
            dataSource.reorderServerList(groupId, guids)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    /**
     * Bulk real-ping test. Default scope is the selected tab; pass [allGroups] = true for
     * the full pool — every profile in every group, independent of the visible tab and of
     * any keyword filter. The startup and source-switch passes use the full scope with
     * [skipTested] = true: configs with a fresh stored result keep it, and only never-
     * tested or expired ones are sent to the test service — so reopening the app or
     * switching sources never re-tests the whole pool again. Manual passes (test button,
     * keyword-filtered tab pass) always re-test their full scope.
     */
    fun testAllRealPing(onlyTcp: Boolean = false, allGroups: Boolean = false, skipTested: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val servers = currentServers()
        val poolGuids = if (allGroups) dataSource.getServerGuidList("") else servers.map { it.guid }
        // Auto passes keep fresh results: only configs with no result (or an expired one)
        // are tested, so switching sources or reopening the app never re-tests everything.
        val targetGuids = if (skipTested) pendingTestGuids(poolGuids) else poolGuids
        if (targetGuids.isEmpty()) {
            return
        }
        if (!skipTested) {
            // Full manual pass: clear the visible tab's badges up front; cached groups
            // follow inside the job.
            mutableServerGroupState(groupId).update { current ->
                current.copy(
                    servers = current.servers.map { server ->
                        if (server.testDelayMillis == 0L) server
                        else server.copy(testDelayMillis = 0L)
                    },
                    rows = current.rows.map { row ->
                        if (row.testDelayMillis == 0L) row
                        else row.copy(testDelayMillis = 0L)
                    }
                )
            }
        }
        // Full-pool pass: empty subscriptionId + empty guids makes CoreTestService expand
        // decodeAllServerList() — every profile in every group. A skipTested pass sends
        // the explicit pending guids instead — the service tests exactly those.
        val request = testRequests.beginBulk(if (allGroups) "" else groupId)
        val message = if (allGroups && !skipTested) {
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, onlyTcp = onlyTcp)
        } else {
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = if (allGroups) "" else groupId,
                serverGuids = if (skipTested || keywordFilter.isNotEmpty()) targetGuids else emptyList(),
                onlyTcp = onlyTcp
            )
        }
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                if (!skipTested) {
                    dataSource.clearAllTestDelayResults(poolGuids)
                    val resetGuids = poolGuids.toHashSet()
                    cacheMutex.withLock {
                        val cachedGroupIds = groupDataCache.keys.toList()
                        cachedGroupIds.forEach { key ->
                            val cached = groupDataCache[key] ?: return@forEach
                            // Tab passes only touch their own group; a full-pool pass resets
                            // every loaded group so stale delays never survive into the pass.
                            if (allGroups || key == groupId) {
                                groupDataCache[key] = cached.map { server ->
                                    if (server.guid !in resetGuids || server.testDelayMillis == 0L) server
                                    else server.copy(testDelayMillis = 0L)
                                }
                            }
                        }
                    }
                }
            }
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    /**
     * Derafsh: filters out configs whose speed-test result is still fresh — anything
     * with a non-zero delay and a stamp inside [AppConfig.DERAFSH_TEST_FRESH_MS] is
     * skipped, so the auto passes only spend time on never-tested or expired configs.
     * Storage is the source of truth here: auto passes run right after an import (or
     * at app open, before any test), so every fresh result is already persisted, and
     * the pending flush loop drains any in-flight results within milliseconds.
     */
    private fun pendingTestGuids(poolGuids: List<String>): List<String> {
        if (poolGuids.isEmpty()) return poolGuids
        val freshCutoff = System.currentTimeMillis() - AppConfig.DERAFSH_TEST_FRESH_MS
        return poolGuids.filter { guid ->
            val aff = dataSource.decodeAffiliationInfo(guid)
            val delay = aff?.testDelayMillis ?: 0L
            val testedAt = aff?.testedAt ?: 0L
            // delay 0 → never tested → pending; a stale result also counts as pending.
            delay == 0L || testedAt < freshCutoff
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing(requestId)
    }

    // ---------- Auto-connect fastest server ----------
    /** Derafsh: whether one-tap connect should test everything and pick the fastest server. */
    fun isAutoPickFastestEnabled(): Boolean = dataSource.isAutoPickFastestEnabled()

    /**
     * Runs the fastest-server pick off the main thread and reports the winner through
     * [onPicked] on the main thread. The result is null when auto-pick is disabled (caller
     * should not have asked) or when storage holds no valid server at all.
     */
    fun pickFastestServer(onPicked: (String?) -> Unit) {
        if (!dataSource.isAutoPickFastestEnabled()) {
            onPicked(null)
            return
        }
        viewModelScope.launch {
            val guid = withContext(ioDispatcher) {
                try {
                    kickAndAwaitFastestTest()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Fastest-server pick failed", e)
                    null
                }
            }
            withContext(Dispatchers.Main) { onPicked(guid) }
        }
    }

    /**
     * Kicks a real-ping bulk test of every profile in every group — the same
     * MSG_MEASURE_CONFIG_START message with an empty subscriptionId and empty guids that
     * CoreTestService expands to decodeAllServerList() — waits for its FINISH broadcast (or
     * FASTEST_PICK_TIMEOUT_MS), then returns the guid with the lowest positive persisted delay.
     */
    private suspend fun kickAndAwaitFastestTest(): String? {
        val allGuids = dataSource.getServerGuidList("")
        if (allGuids.isEmpty()) return null

        // A full-pool test that just finished (the startup maintenance pass, or a recent
        // pick) already produced fresh delays — pick directly instead of re-testing every
        // config on connect.
        if (System.currentTimeMillis() - lastFullTestFinishAt < FASTEST_PICK_FRESH_WINDOW_MS) {
            return pickBestGuid(allGuids)
        }

        // A background auto-maintenance test (or any stray bulk) must not race the pick:
        // stop it, drop its request, and start a clean full-profile bulk test below.
        if (bulkTestJob?.isActive == true) bulkTestJob?.cancel()
        if (testRequests.bulk != null) {
            testRequests.cancelBulk()
            resetTestStatus()
            dataSource.cancelAllPing()
        }

        // Stale persisted results must not win the pick: wipe them before the bulk test starts.
        dataSource.clearAllTestDelayResults(allGuids)

        val request = testRequests.beginBulk(groupId = "")
        val pick = FastestPick(request.id, CompletableDeferred())
        fastestPick = pick

        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        try {
            dataSource.sendMsg2TestService(
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START),
                request.id,
            )
            withTimeoutOrNull(FASTEST_PICK_TIMEOUT_MS) { pick.deferred.await() }
        } finally {
            if (fastestPick?.id == request.id) fastestPick = null
        }

        // Timed out without a FINISH broadcast: stop the still-running bulk test; its cancel
        // reply is dropped because the request was already closed here.
        if (testRequests.bulk?.id == request.id) {
            testRequests.cancelBulk()
            resetTestStatus()
            dataSource.cancelAllPing()
        }

        return pickBestGuid(allGuids)
    }

    /**
     * Best guid = lowest positive testDelayMillis across ALL profiles (0/negative/absent means
     * untested or failed and never wins). Derafsh Kaviani security policy: a profile that
     * disabled TLS verification (allowInsecure) must never displace a verified-secure winner;
     * insecure profiles are only used when nothing secure passed the test. With no positive
     * result at all, fall back to the previously selected server, else the first valid
     * profile; null only when nothing is connectable.
     *
     * Derafsh stale bias: profiles whose consecutive-failure streak marked them stale are
     * skipped first — fresh candidates win within each security tier — and only when no
     * non-stale candidate remains does the pick fall back to the full set. Security still
     * dominates staleness: a verified-secure (even stale) winner is never displaced by an
     * insecure profile.
     */
    private fun pickBestGuid(allGuids: List<String>): String? {
        val staleGuids = StaleProfileTracker.staleGuidSet()
        val working = allGuids.mapNotNull { guid ->
            val delay = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis
                ?.takeIf { it > 0L }
                ?: return@mapNotNull null
            val secure = dataSource.decodeServerConfig(guid)?.insecure != true
            Triple(guid, delay, secure)
        }
        val fresh = working.filter { it.first !in staleGuids }
        fresh.filter { it.third }.minByOrNull { it.second }?.let { return it.first }
        working.filter { it.third }.minByOrNull { it.second }?.let { return it.first }
        fresh.minByOrNull { it.second }?.let { return it.first }
        working.minByOrNull { it.second }?.let { return it.first }

        return dataSource.getSelectServer()
            ?.takeIf { dataSource.decodeServerConfig(it) != null }
            ?: allGuids.firstOrNull { dataSource.decodeServerConfig(it) != null }
    }

    private fun onTestsFinished(requestId: String) {
        val finished = testRequests.completeBulk(requestId) ?: return
        // A full-pool test ("All" group, no keyword filter) just made every persisted delay fresh.
        if (finished.groupId.isEmpty() && keywordFilter.isEmpty()) {
            lastFullTestFinishAt = System.currentTimeMillis()
        }
        resetTestStatus()
        viewModelScope.launch(ioDispatcher) {
            // Derafsh: drop built-in-pool configs that failed this real-delay pass BEFORE
            // reloading, so dead servers never linger in the list. Never-tested configs
            // (delay 0) are always kept.
            val removed = dataSource.sweepUnhealthyPoolConfigs()
            cacheMutex.withLock { groupDataCache.clear() }
            if (removed > 0) {
                refreshSelectedGuid()
                toast(dataSource.getString(R.string.derafsh_unhealthy_removed, removed))
                setupGroupTab(forceRefresh = true)
            } else {
                reloadAllGroups(_uiState.value.groups.map { it.id })
            }
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Start-pending phase ----------
    private var startPendingTimeoutJob: Job? = null

    /**
     * Marks a start request as pending for the connect button, opening [phase]. The service
     * has no "starting" broadcast, so the phase is closed by any service-state broadcast
     * (see updateRunningState) or, if a start dies without any broadcast, by a timeout fallback.
     */
    fun markStartRequested(phase: ConnectPhase) {
        startPendingTimeoutJob?.cancel()
        _isStartPending.value = true
        _connectPhase.value = phase
        startPendingTimeoutJob = viewModelScope.launch {
            delay(START_PENDING_TIMEOUT_MS)
            _isStartPending.value = false
            _connectPhase.value = ConnectPhase.None
        }
    }

    /** Derafsh Kaviani: the fastest-server winner was picked; the tunnel is going up. */
    fun markTunnelStarting() {
        _connectPhase.value = ConnectPhase.EstablishingTunnel
    }

    private fun clearStartPending() {
        startPendingTimeoutJob?.cancel()
        startPendingTimeoutJob = null
        _isStartPending.value = false
        _connectPhase.value = ConnectPhase.None
    }

    // ---------- Session time-up dialog ----------
    fun dismissSessionTimeUp() {
        _uiState.update { it.copy(sessionTimeUp = false) }
    }

    // ---------- Running state ----------
    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        // Every authoritative service-state broadcast closes the connecting phase.
        clearStartPending()
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
        const val START_PENDING_TIMEOUT_MS = 10_000L

        /** Fastest-pick bulk test must answer within this window or the old selection is used. */
        const val FASTEST_PICK_TIMEOUT_MS = 10_000L

        /** Persisted full-pool results younger than this make connect pick without re-testing. */
        const val FASTEST_PICK_FRESH_WINDOW_MS = 5 * 60_000L
    }
}
