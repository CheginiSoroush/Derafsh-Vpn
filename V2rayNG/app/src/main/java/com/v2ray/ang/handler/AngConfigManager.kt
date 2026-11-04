package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isGroupType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.V2rayNFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import okhttp3.Headers
import java.net.URI

object AngConfigManager {

    private data class ParsedProfile(
        val profile: ProfileItem,
        val rawConfig: String? = null,
    )

    // Parser mapping for different config types (lazy initialized)
    private val configFmtParsers: Map<String, (String) -> ProfileItem?> by lazy {
        mapOf(
            EConfigType.VMESS.protocolScheme to VmessFmt::parse,
            EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
            EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
            AppConfig.SOCKS4 to SocksFmt::parse,
            AppConfig.SOCKS5 to SocksFmt::parse,
            EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
            EConfigType.VLESS.protocolScheme to VlessFmt::parse,
            EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
            EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
            AppConfig.HY2 to Hysteria2Fmt::parse,
        )
    }

    /**
     * Shares the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares non-custom configurations to the clipboard.
     *
     * @param context The context.
     * @param serverList The list of server GUIDs.
     * @return The number of configurations shared.
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    /**
     * Shares the configuration as a QR code.
     *
     * @param guid The GUID of the configuration.
     * @return The QR code bitmap.
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    /**
     * Shares the full content of the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = CoreConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares the configuration.
     *
     * @param guid The GUID of the configuration.
     * @return The configuration string.
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                EConfigType.CUSTOM -> MmkvManager.decodeServerRaw(guid).orEmpty()
                else -> config.description.orEmpty()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    /**
     * Imports a batch of configurations.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return A pair containing the number of configurations and subscriptions imported.
     */
    fun importBatchConfig(server: String?, subid: String, append: Boolean): Pair<Int, Int> {
        return try {
            var count = parseBatchConfig(Utils.decode(server), subid, append)
            if (count <= 0) {
                count = parseBatchConfig(server, subid, append)
            }
            if (count <= 0) {
                count = parseCustomConfigServer(server, subid, append)
            }

            var countSub = parseBatchSubscription(server)
            if (countSub <= 0) {
                countSub = parseBatchSubscription(Utils.decode(server))
            }
            if (countSub > 0) {
                updateConfigViaSubAll()
            }

            count to countSub
        } catch (e: ProfileStorageException) {
            LogUtil.e(AppConfig.TAG, "Failed to store imported profiles", e)
            0 to 0
        }
    }

    /**
     * Parses a batch of subscriptions.
     *
     * @param servers The servers string.
     * @return The number of subscriptions parsed.
     */
    private fun parseBatchSubscription(servers: String?): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
                .distinct()
                .forEach { str ->
                    if (Utils.isValidSubUrl(str)) {
                        count += importUrlAsSubscription(str)
                    }
                }
            return count
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    /**
     * Parses a batch of configurations.
     *
     * @param servers The servers string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            val allConfigs = parseBatchProfiles(servers, subid)

            if (allConfigs.isNotEmpty()) {
                commitProfiles(
                    configs = allConfigs.map(::ParsedProfile),
                    subid = subid,
                    append = append,
                )
            }

            return allConfigs.size
        } catch (e: ProfileStorageException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    /**
     * Parses a raw subscription payload into profiles without touching storage — the
     * parsing half of [parseBatchConfig]. The Derafsh pool import reuses it to chunk the
     * parsed configs across multiple groups before anything is committed.
     *
     * @param servers The raw payload (plain list or v2rayN format).
     * @param subid The subscription id stamped onto every parsed profile.
     * @return The parsed profiles, in the same order [parseBatchConfig] would commit them.
     */
    private fun parseBatchProfiles(servers: String?, subid: String): List<ProfileItem> {
        if (servers.isNullOrEmpty()) {
            return emptyList()
        }
        try {
            val subItem = MmkvManager.decodeSubscription(subid)

            val configs = mutableListOf<ProfileItem>()
            val v2raynLines = mutableListOf<String>()

            servers.lines()
                .distinct()
                .reversed()
                .forEach {
                    if (it.startsWith(AppConfig.V2RAYNFMTS, ignoreCase = true)) {
                        v2raynLines.add(it)
                    } else {
                        val config = parseConfig(it, subid, subItem)
                        if (config != null) {
                            configs.add(config)
                        }
                    }
                }

            val v2raynConfigs = V2rayNFmt.parse(v2raynLines, subid)
            return v2raynConfigs + configs
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch profiles", e)
        }
        return emptyList()
    }

    /**
     * Commits parsed profiles before removing the profiles they replace.
     *
     * @param configs The parsed profiles to save.
     * @param subid The subscription ID.
     * @param append Whether to append to the existing server list.
     */
    private fun commitProfiles(
        configs: List<ParsedProfile>,
        subid: String,
        append: Boolean,
    ) {
        val keyToProfile = linkedMapOf<String, ProfileItem>()
        val rawConfigs = mutableMapOf<String, String>()

        configs.forEach { parsed ->
            val key = Utils.getUuid()
            keyToProfile[key] = parsed.profile
            parsed.rawConfig?.let { raw -> rawConfigs[key] = raw }
        }

        MmkvManager.saveServerProfiles(
            profiles = keyToProfile,
            rawConfigs = rawConfigs,
            subscriptionId = subid,
            append = append,
        )
    }

    /**
     * Parses a custom configuration server.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseCustomConfigServer(server: String?, subid: String, append: Boolean): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()

                if (serverList.isNotEmpty()) {
                    val configs = serverList.reversed().map { srv ->
                        val config = CustomFmt.parse(JsonUtil.toJson(srv))
                        config.subscriptionId = subid
                        config.description = generateDescription(config)
                        ParsedProfile(
                            profile = config,
                            rawConfig = JsonUtil.toJsonPretty(srv) ?: "",
                        )
                    }
                    commitProfiles(configs, subid, append)
                    return configs.size
                }
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server)
                config.subscriptionId = subid
                config.description = generateDescription(config)
                commitProfiles(
                    configs = listOf(ParsedProfile(config, server)),
                    subid = subid,
                    append = append,
                )
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server)
                config.subscriptionId = subid
                config.description = generateDescription(config)
                commitProfiles(
                    configs = listOf(ParsedProfile(config, server)),
                    subid = subid,
                    append = append,
                )
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    /**
     * Parses the configuration from a QR code or string.
     * Only parses and returns ProfileItem, does not save.
     *
     * @param str The configuration string.
     * @param subid The subscription ID.
     * @param subItem The subscription item.
     * @return The parsed ProfileItem or null if parsing fails or filtered out.
     */
    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?
    ): ProfileItem? {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return null
            }

            val config = configFmtParsers.firstNotNullOfOrNull { (scheme, parser) ->
                if (str.startsWith(scheme)) parser(str) else null
            }

            if (config == null) {
                return null
            }

            // Apply filter
            if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
                val matched = Regex(pattern = subItem?.filter.orEmpty())
                    .containsMatchIn(input = config.remarks)
                if (!matched) return null
            }

            config.subscriptionId = subid
            config.description = generateDescription(config)

            return config
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse config", e)
            return null
        }
    }

    /**
     * Updates every enabled subscription sequentially. [onSubStart] fires with each enabled
     * subscription's name right before it is fetched — used to announce progress on the
     * main-screen maintenance card.
     */
    fun updateConfigViaSubAll(onSubStart: (String) -> Unit = {}): SubscriptionUpdateResult {
        return try {
            // Overflow chunk groups are never updated directly — each primary group owns
            // the fetch and redistributes configs into fresh chunks itself.
            val subscriptions = MmkvManager.decodeSubscriptions()
                .filter { !it.guid.startsWith(AppConfig.DERAFSH_POOL_CHUNK_PREFIX) }
            subscriptions.fold(SubscriptionUpdateResult()) { acc, subscription ->
                if (subscription.subscription.enabled) onSubStart(subscription.subscription.remarks)
                acc + updateConfigViaSub(subscription)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            SubscriptionUpdateResult()
        }
    }

    /**
     * Updates the configuration via a subscription.
     *
     * Every subscription — the built-in Derafsh pool and user-added ones alike — goes
     * through the chunked importer, so no group ever holds more than
     * [AppConfig.DERAFSH_POOL_CHUNK_SIZE] configs: larger payloads spill into additional
     * "«name» (2)", "(3)", … overflow groups automatically.
     *
     * @param it The subscription item.
     * @return Subscription update result.
     */
    fun updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult {
        // Overflow chunk groups are never fetched directly — their primary group owns the
        // refresh and redistributes configs into 200-config chunks itself.
        if (it.guid.startsWith(AppConfig.DERAFSH_POOL_CHUNK_PREFIX)) {
            return SubscriptionUpdateResult(skipCount = 1)
        }
        // Per-subscription containment (upstream parity): upstream wraps the whole per-sub
        // body in try/catch so one malformed subscription — e.g. a scheme-less URL that
        // passes UI validation (Patterns.WEB_URL) but throws MalformedURLException inside
        // HttpUtil.toIdnUrl's java.net.URL() — fails alone as failureCount=1 and the rest
        // of the updateAll fold keeps running. The chunked refactor kept a catch only for
        // the lock-protected mutation phase, so a single bad subscription URL aborted the
        // entire refresh cycle: every later subscription stayed un-refreshed and even the
        // failure count was swallowed by updateConfigViaSubAll's outer catch.
        return try {
            updateConfigViaSubChunked(it)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update subscription: ${it.subscription.remarks}", e)
            SubscriptionUpdateResult(failureCount = 1)
        }
    }

    /**
     * Subscription id of the [n]th overflow chunk group (n ≥ 2) carved out of
     * [primarySubId]. Namespaced per primary so several chunked subscriptions can
     * coexist without stealing each other's groups.
     */
    fun chunkGroupId(primarySubId: String, n: Int): String =
        "${AppConfig.DERAFSH_POOL_CHUNK_PREFIX}${primarySubId}_$n"

    /** Prefix shared by every overflow chunk group carved out of [primarySubId]. */
    fun chunkGroupPrefixFor(primarySubId: String): String =
        "${AppConfig.DERAFSH_POOL_CHUNK_PREFIX}${primarySubId}_"

    /** Overflow chunk groups currently owned by [primarySubId]. */
    fun chunkGroupsOf(primarySubId: String): List<SubscriptionCache> =
        MmkvManager.decodeSubscriptions()
            .filter { it.guid.startsWith(chunkGroupPrefixFor(primarySubId)) }

    /**
     * Fetches a subscription payload with the panel's traffic/expiry headers captured.
     * Tries the configured proxy first, then a direct fetch — the exact behavior the
     * subscription update path has always had, extracted so the Derafsh pool importer
     * can reuse it verbatim.
     *
     * @return The payload text (empty on failure) and any subscription usage info.
     */
    private fun fetchSubscriptionContent(
        it: SubscriptionCache,
        url: String,
    ): Pair<String, SubscriptionUsageInfo?> {
        val userAgent = it.subscription.userAgent
        val requestHeaders = it.subscription.requestHeaders
        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        // Account info (traffic, expiry, support link) the panel sends in the response headers.
        var usageInfo: SubscriptionUsageInfo? = null
        val captureUsageInfo: (Headers) -> Unit = { headers ->
            usageInfo = SubscriptionInfoParser.parse(
                userInfo = headers["Subscription-Userinfo"],
                profileTitle = headers["Profile-Title"],
                supportUrl = headers["Support-Url"],
                webPageUrl = headers["Profile-Web-Page-Url"],
            )
        }

        var configText = try {
            val httpPort = SettingsManager.getHttpPort()
            HttpUtil.getUrlContentWithUserAgent(
                UrlContentRequest(
                    url = url,
                    userAgent = userAgent,
                    requestHeaders = requestHeaders,
                    timeout = 15000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                ),
                captureUsageInfo
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error", e)
            ""
        }
        if (configText.isEmpty()) {
            configText = try {
                HttpUtil.getUrlContentWithUserAgent(
                    UrlContentRequest(
                        url = url,
                        userAgent = userAgent,
                        requestHeaders = requestHeaders
                    ),
                    captureUsageInfo
                )
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Update subscription: Failed to get URL content with user agent", e)
                ""
            }
        }
        return configText to usageInfo
    }

    /**
     * Chunked subscription import: fetches the subscription ONCE and redistributes the
     * parsed configs into groups of at most [AppConfig.DERAFSH_POOL_CHUNK_SIZE] — the
     * primary subscription keeps the first chunk (its remarks are unchanged), every
     * further chunk lands in an overflow group named "«primary remarks» (2)", "(3)", …
     * under a per-primary [AppConfig.DERAFSH_POOL_CHUNK_PREFIX] sub-id, so several
     * primaries can coexist without stealing each other's groups. Overflow groups inherit
     * the primary's settings but never auto-update. A primary's previous overflow groups
     * are removed before redistribution, so a refresh never leaves stale groups or stale
     * servers behind. Only the primary subscription is ever fetched; overflow groups are
     * skipped by [updateConfigViaSub] and [updateConfigViaSubAll].
     */
    private fun updateConfigViaSubChunked(it: SubscriptionCache): SubscriptionUpdateResult {
        // Fetch and parse run OUTSIDE the cross-process refresh lock: they are pure
        // network/CPU work, and holding a cross-process file lock through a slow fetch
        // would stall the other process's refresh for no benefit. Everything that
        // mutates shared pool state (generation read, retire, commit, record) is
        // serialized by [MmkvManager.withSubscriptionUpdateLock] — subscription
        // refreshes run in TWO processes (the :tasks process auto-update and the main
        // process manual pulls), which a JVM lock cannot serialize.
        if (!it.subscription.enabled) {
            return SubscriptionUpdateResult(skipCount = 1)
        }
        if (TextUtils.isEmpty(it.subscription.remarks)
            || TextUtils.isEmpty(it.subscription.url)
        ) {
            return SubscriptionUpdateResult(skipCount = 1)
        }

        val url = HttpUtil.toIdnUrl(it.subscription.url)
        if (!Utils.isValidUrl(url)) {
            return SubscriptionUpdateResult(failureCount = 1)
        }
        if (!it.subscription.allowInsecureUrl && !Utils.isValidSubUrl(url)) {
            return SubscriptionUpdateResult(failureCount = 1)
        }

        val (configText, usageInfo) = fetchSubscriptionContent(it, url)
        if (configText.isEmpty()) {
            return SubscriptionUpdateResult(failureCount = 1)
        }

        val primarySubId = it.guid
        var profiles = parseBatchProfiles(Utils.decode(configText), primarySubId)
        if (profiles.isEmpty()) {
            profiles = parseBatchProfiles(configText, primarySubId)
        }
        if (profiles.isEmpty()) {
            // Got response but no valid configs parsed
            return SubscriptionUpdateResult(failureCount = 1)
        }

        return MmkvManager.withSubscriptionUpdateLock {
            try {
                // The built-in pool shares its group index with user content — manually
                // imported configs and policy groups live in the very same list. Rebuilding
                // that index (append=false) would delete everything that is not a group,
                // so the refresh retires exactly the guids the previous refresh created
                // instead. The first refresh after install/upgrade has no recorded
                // generation yet and falls back to the plain configs stamped with this
                // pool's subscription id (groups and blank-stamped manual imports survive).
                val isBuiltinPool = primarySubId == AppConfig.DEFAULT_SUBSCRIPTION_ID
                val previousGeneration = if (isBuiltinPool) {
                    MmkvManager.decodePoolGeneration().ifEmpty {
                        MmkvManager.decodeServerList(primarySubId).filter { guid ->
                            val config = MmkvManager.decodeServerConfig(guid)
                            config != null && !config.configType.isGroupType() &&
                                    config.subscriptionId == primarySubId
                        }.toSet()
                    }
                } else {
                    emptySet()
                }

                // Snapshot still-fresh test results BEFORE the old generation is deleted, keyed
                // by config identity (protocol + endpoint + credentials), so re-importing the
                // same subscription — a source switch, the 4-hourly refresh, a manual pull —
                // keeps the delay badges instead of forcing a full-pool re-test. Results older
                // than DERAFSH_TEST_FRESH_MS are dropped here so dead servers are re-detected
                // by the next auto test pass.
                val carriedResults = snapshotFreshTestResults(primarySubId)

                // Capture the user's current selection before retiring the old generation:
                // when it belongs to the previous pool, it is re-anchored onto its identity
                // match in the new batch after the commits succeed.
                val selection = MmkvManager.getSelectServer()
                val selectionNeedsReanchor = selection != null && selection in previousGeneration
                val selectionProfile = if (selectionNeedsReanchor && selection != null) {
                    MmkvManager.decodeServerConfig(selection)
                } else {
                    null
                }

                // Retire the previous generation BEFORE committing the new one: exactly the
                // recorded guids (primary index entries, all payload stores, selection) —
                // groups and manually imported configs sharing the index are never touched.
                // Committing first would turn a mid-refresh storage failure into permanently
                // orphaned half-committed batches; retiring first fails exactly like the old
                // index rebuild did and self-heals on the next refresh.
                if (isBuiltinPool) {
                    MmkvManager.removeServerProfiles(previousGeneration)
                }

                // Drop THIS primary's previous overflow groups before redistributing.
                chunkGroupsOf(primarySubId).forEach { cache ->
                    MmkvManager.removeSubscription(cache.guid)
                }

                val chunks = profiles.chunked(AppConfig.DERAFSH_POOL_CHUNK_SIZE.coerceAtLeast(1))
                val baseRemarks = it.subscription.remarks
                val committedProfiles = LinkedHashMap<String, ProfileItem>()
                chunks.forEachIndexed { index, chunk ->
                    val subId = if (index == 0) {
                        primarySubId
                    } else {
                        chunkGroupId(primarySubId, index + 1)
                    }
                    if (index > 0) {
                        // Overflow groups inherit the primary's settings (URL, filter, user agent,
                        // request headers, TLS-bypass switch) so a manual refresh behaves
                        // identically — but they never auto-update: the primary fetches for all.
                        MmkvManager.encodeSubscription(
                            subId,
                            it.subscription.copy(
                                remarks = "$baseRemarks (${index + 1})",
                                enabled = true,
                                autoUpdate = false,
                                lastUpdated = -1,
                                usageInfo = null,
                            )
                        )
                    }
                    // Re-point every profile of this chunk at its owning group before saving.
                    chunk.forEach { profile -> profile.subscriptionId = subId }
                    val savedProfiles = chunk.associate { Utils.getUuid() to it }
                    MmkvManager.saveServerProfiles(
                        profiles = savedProfiles,
                        rawConfigs = emptyMap(),
                        subscriptionId = subId,
                        // The built-in pool's previous generation is retired explicitly
                        // above; the group index it shares with user content is never
                        // rebuilt wholesale.
                        append = !isBuiltinPool,
                    )
                    committedProfiles.putAll(savedProfiles)
                    // Hand the old generation's fresh results to the replacement profiles so
                    // the auto test pass only has to test genuinely new configs.
                    if (carriedResults.isNotEmpty()) {
                        savedProfiles.forEach { (guid, profile) ->
                            carriedResults[configIdentityKey(profile)]?.let { (delay, testedAt) ->
                                MmkvManager.encodeServerTestResult(guid, delay, testedAt)
                            }
                        }
                    }
                }

                if (isBuiltinPool) {
                    // Keep the user connected when their previous selection belonged to the
                    // retired generation: re-anchor onto its identity match in the new
                    // batch, else the first new config (same policy as ProfileReplacement).
                    if (selectionNeedsReanchor && selection != null) {
                        val reanchoredSelection = ProfileReplacement.findSelectedReplacement(
                            profiles = committedProfiles,
                            currentSelection = selection,
                            selectedProfile = selectionProfile,
                        ) ?: committedProfiles.keys.firstOrNull()
                        // committedProfiles is never empty here (empty fetches early-return
                        // above), so the re-anchor always lands on a fresh config.
                        if (reanchoredSelection != null) {
                            MmkvManager.setSelectServer(reanchoredSelection)
                        }
                    }
                    MmkvManager.encodePoolGeneration(committedProfiles.keys)
                }

                it.subscription.lastUpdated = System.currentTimeMillis()
                // Replaced on every successful update so the box disappears if the panel stops sending the headers.
                it.subscription.usageInfo = usageInfo
                MmkvManager.encodeSubscription(primarySubId, it.subscription)
                LogUtil.i(
                    AppConfig.TAG,
                    "Subscription updated: ${it.subscription.remarks}, ${profiles.size} configs in ${chunks.size} groups"
                    + if (carriedResults.isEmpty()) "" else ", ${carriedResults.size} fresh result(s) carried over"
                )
                SubscriptionUpdateResult(configCount = profiles.size, successCount = 1)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to update subscription: ${it.subscription.remarks}", e)
                SubscriptionUpdateResult(failureCount = 1)
            }
        }
    }

    /**
     * Snapshots the subscription's still-fresh speed-test results — the primary group
     * plus its overflow chunk groups — keyed by config identity, ready to be handed to
     * the re-imported replacement profiles. A result qualifies only when it exists
     * (delay ≠ 0) and was measured within [AppConfig.DERAFSH_TEST_FRESH_MS]; legacy
     * entries without a timestamp (testedAt = 0) never qualify, so the first import
     * after the upgrade re-tests once and stamps everything from then on.
     */
    private fun snapshotFreshTestResults(
        primarySubId: String,
    ): Map<String, Pair<Long, Long>> {
        val freshCutoff = System.currentTimeMillis() - AppConfig.DERAFSH_TEST_FRESH_MS
        val carried = linkedMapOf<String, Pair<Long, Long>>()
        val oldGroupIds = listOf(primarySubId) + chunkGroupsOf(primarySubId).map { it.guid }
        oldGroupIds.forEach { groupId ->
            MmkvManager.decodeServerList(groupId).forEach { guid ->
                val aff = MmkvManager.decodeServerAffiliationInfo(guid) ?: return@forEach
                if (aff.testDelayMillis != 0L && aff.testedAt >= freshCutoff) {
                    val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
                    val key = configIdentityKey(profile)
                    if (!carried.containsKey(key)) {
                        carried[key] = aff.testDelayMillis to aff.testedAt
                    }
                }
            }
        }
        return carried
    }

    /**
     * Stable identity of a parsed config for result carry-over: protocol + endpoint +
     * credentials + transport, ignoring cosmetic fields (remarks, subscription id, add
     * time). The same server republished under a different name still matches, while any
     * meaningful change (different port, uuid, sni, transport…) counts as a new config
     * and is tested from scratch.
     */
    private fun configIdentityKey(p: ProfileItem): String = buildString {
        append(p.configType.name); append('|')
        append(p.server?.trim()); append('|')
        append(p.serverPort?.trim()); append('|')
        append(p.password?.trim()); append('|')
        append(p.username?.trim()); append('|')
        append(p.security?.trim()); append('|')
        append(p.sni?.trim()); append('|')
        append(p.network?.trim()); append('|')
        append(p.headerType?.trim()); append('|')
        append(p.host?.trim()); append('|')
        append(p.path?.trim()); append('|')
        append(p.flow?.trim()); append('|')
        append(p.method?.trim())
    }

    /**
     * Removes invalid server configurations for a subscription.
     *
     * @param subId The subscription ID.
     * @return The number of invalid configs removed.
     */
    fun removeInvalidServer(subId: String): Int {
        val serverList = MmkvManager.decodeServerList(subId)
        val invalidServers = serverList.filter {
            val aff = MmkvManager.decodeServerAffiliationInfo(it)
            aff != null && aff.testDelayMillis < 0L
        }
        MmkvManager.removeServers(invalidServers, subId)
        return invalidServers.size
    }

    /**
     * Derafsh: removes every built-in-pool config whose latest real-delay test failed
     * (testDelayMillis < 0) — never-tested configs (delay 0) are always kept. Afterwards,
     * overflow chunk groups left with zero configs are dissolved so no dead tab remains.
     *
     * Unhealthy-config removal is scoped to the built-in pool (the active source and its
     * overflow groups); user subscriptions keep their configs regardless of test results.
     *
     * Runs right after a bulk real-delay pass finishes (see MainViewModel.onTestsFinished).
     *
     * @return The number of unhealthy configs removed.
     */
    fun sweepUnhealthyPoolConfigs(): Int {
        return try {
            var removed = 0
            MmkvManager.decodeSubscriptions()
                .filter { cache ->
                    cache.guid == AppConfig.DEFAULT_SUBSCRIPTION_ID ||
                            cache.guid.startsWith(chunkGroupPrefixFor(AppConfig.DEFAULT_SUBSCRIPTION_ID))
                }
                .forEach { cache ->
                    removed += removeInvalidServer(cache.guid)
                }
            // A chunk group that lost all of its configs no longer earns a tab.
            MmkvManager.decodeSubscriptions()
                .filter { it.guid.startsWith(AppConfig.DERAFSH_POOL_CHUNK_PREFIX) }
                .forEach { cache ->
                    if (MmkvManager.decodeServerList(cache.guid).isEmpty()) {
                        MmkvManager.removeSubscription(cache.guid)
                    }
                }
            if (removed > 0) {
                LogUtil.i(AppConfig.TAG, "Derafsh pool sweep removed $removed unhealthy configs")
            }
            removed
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to sweep unhealthy Derafsh pool configs", e)
            0
        }
    }

    /**
     * Sorts servers by test results for a subscription.
     *
     * @param subId The subscription ID.
     */
    fun sortByTestResultsForSub(subId: String) {
        val serverList = MmkvManager.decodeServerList(subId)
        if (serverList.isEmpty()) return

        val sorted = serverList
            .map { guid ->
                val delay =
                    MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                guid to if (delay <= 0L) Long.MAX_VALUE else delay
            }
            .sortedBy { it.second }
            .map { it.first }
            .toMutableList()
        MmkvManager.encodeServerList(sorted, subId)
    }

    /**
     * Imports a URL as a subscription.
     *
     * @param url The URL.
     * @return The number of subscriptions imported.
     */
    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.subscription.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }

    /** Generates a description for the profile.
     *
     * @param profile The profile item.
     * @return The generated description.
     */
    fun generateDescription(profile: ProfileItem): String {
        // Hide xxx:xxx:***/xxx.xxx.xxx.***
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addrPart = server?.let {
            if (it.contains(":"))
                it.split(":").take(2).joinToString(":", postfix = ":***")
            else
                it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addrPart : ${port ?: ""}"
    }
}
