package com.v2ray.ang.handler

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.GEOIP_PRIVATE
import com.v2ray.ang.AppConfig.GEOSITE_PRIVATE
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.enums.VpnInterfaceAddressConfig
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager.decodeAllServerList
import com.v2ray.ang.handler.MmkvManager.decodeServerConfig
import com.v2ray.ang.handler.MmkvManager.decodeSubsList
import com.v2ray.ang.handler.MmkvManager.decodeSubscription
import com.v2ray.ang.handler.MmkvManager.encodeSubscription
import com.v2ray.ang.handler.MmkvManager.removeSubscription
import com.v2ray.ang.R
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.random.Random

object SettingsManager {

    @Volatile
    private var runtimeSocksPort: Int? = null

    fun initApp(context: Context) {
        ensureDefaultSettings()
        seedDefaultSubscription(context)
        initRoutingRulesets(context)
        migrateServerListToSubscriptions()
        migrateHysteria2PinSHA256()
        migrateRemoveWireguardProfiles()
    }

    /**
     * Seeds the built-in Derafsh source subscription (free public configs from the active
     * source) exactly once, on first launch. Users can still add their own subscriptions.
     * Only the ACTIVE source is materialized as a subscription and updated; the source
     * switcher ("Derafsh 1", "Derafsh 2", …) rewrites its URL on demand.
     */
    private fun seedDefaultSubscription(context: Context) {
        val existing = decodeSubscription(DEFAULT_SUBSCRIPTION_ID)
        // v1.2.52: the pre-v1.2.37 legacy sub URL (the developer's GitHub) is no longer
        // referenced anywhere in the app; installs still pointing at it keep working off
        // their stored URL and re-join the built-in source through the source picker.
        // Migration: installs seeded with the older 6-hour cadence move to the 4-hour cycle.
        if (existing != null && existing.updateInterval == 360L) {
            existing.updateInterval = 240L
            encodeSubscription(DEFAULT_SUBSCRIPTION_ID, existing)
            LogUtil.i(AppConfig.TAG, "Migrated default Derafsh subscription interval to 4h")
        }

        // One-time migration (v1.2.53): installs seeded with the third-party free-pool URL
        // move to the worker-served pool (version-gated, proxied through imsoroush.top).
        // Only the default free source is rewritten — the premium endpoint and any other
        // active source are never touched.
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_FREE_SUB_MOVED) != "1") {
            val stored = decodeSubscription(DEFAULT_SUBSCRIPTION_ID)
            if (stored != null && stored.url == AppConfig.DERAFSH_SUB_URL_LEGACY) {
                stored.url = AppConfig.DERAFSH_SUB_URL
                encodeSubscription(DEFAULT_SUBSCRIPTION_ID, stored)
                LogUtil.i(AppConfig.TAG, "Migrated default Derafsh subscription to the worker-served free pool")
            }
            MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_FREE_SUB_MOVED, "1")
        }

        // One-time migration from the multi-pool seeding: only the active source is kept as a
        // subscription now — the extra built-in pools are removed so nothing stale is updated.
        val singleSourceMigrated =
            MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_SINGLE_SOURCE_MIGRATED) == "1"
        if (!singleSourceMigrated) {
            listOf("__derafsh_plus1__", "__derafsh_plus2__", "__derafsh_turbo__").forEach { subId ->
                if (decodeSubscription(subId) != null) {
                    removeSubscription(subId)
                    LogUtil.i(AppConfig.TAG, "Removed stale built-in Derafsh pool: $subId")
                }
            }
            MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_SINGLE_SOURCE_MIGRATED, "1")
        }

        // One-time migration to per-primary chunk ids: overflow groups created with the old
        // flat "__derafsh_chunk_<n>__" naming are dissolved once; every subscription's next
        // refresh rebuilds its own groups under the new namespaced scheme.
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_CHUNK_NAMESPACED) != "1") {
            MmkvManager.decodeSubscriptions()
                .filter { it.guid.startsWith(AppConfig.DERAFSH_POOL_CHUNK_PREFIX) }
                .forEach { cache ->
                    removeSubscription(cache.guid)
                    LogUtil.i(AppConfig.TAG, "Dissolved legacy chunk group: ${cache.guid}")
                }
            MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_CHUNK_NAMESPACED, "1")
        }

        // Ensure the single built-in subscription exists and tracks the active source.
        val activeIndex = DerafshSourceManager.activeIndex()
        val activeName = context.getString(R.string.derafsh_source_name, activeIndex + 1)
        val current = decodeSubscription(DEFAULT_SUBSCRIPTION_ID)
        if (current == null) {
            encodeSubscription(
                DEFAULT_SUBSCRIPTION_ID,
                SubscriptionItem(
                    remarks = activeName,
                    url = DerafshSourceManager.sourceUrl(activeIndex),
                    enabled = true,
                    autoUpdate = true,
                    updateInterval = 240, // every 4 hours
                )
            )
            LogUtil.i(AppConfig.TAG, "Seeded active Derafsh source subscription: $activeName")
        } else if (current.remarks != activeName) {
            // Keep the built-in subscription naming in sync with the switcher.
            current.remarks = activeName
            encodeSubscription(DEFAULT_SUBSCRIPTION_ID, current)
        }
        MmkvManager.encodeSettings(AppConfig.PREF_DEFAULT_SUB_SEEDED, "1")
    }

    /**
     * Initialize routing rulesets.
     * @param context The application context.
     */
    private fun initRoutingRulesets(context: Context) {
        val exist = MmkvManager.decodeRoutingRulesets()
        if (exist.isNullOrEmpty()) {
            // Fresh installs: seed the Iran whitelist preset — domestic (.ir / category-ir /
            // geoip:ir) traffic goes direct, everything else through the proxy.
            val rulesetList = getPresetRoutingRulesets(context, RoutingType.WHITE_IRAN)
            MmkvManager.encodeRoutingRulesets(rulesetList)
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_IRAN_MIGRATED, "1")
            return
        }
        // One-time migration: installs still running the untouched China-whitelist preset
        // move to the Iran whitelist. Only applied when the user never customized rules.
        val migrated = MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_IRAN_MIGRATED) == "1"
        if (!migrated) {
            runCatching {
                val chinaPreset = getPresetRoutingRulesets(context, RoutingType.WHITE)
                if (JsonUtil.toJson(exist) == JsonUtil.toJson(chinaPreset)) {
                    val iranPreset = getPresetRoutingRulesets(context, RoutingType.WHITE_IRAN)
                    MmkvManager.encodeRoutingRulesets(iranPreset)
                    LogUtil.i(AppConfig.TAG, "Migrated routing rulesets to Iran whitelist")
                }
            }
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_IRAN_MIGRATED, "1")
        }
    }

    /**
     * Get preset routing rulesets.
     * @param context The application context.
     * @param type The routing preset type.
     * @return A mutable list of RulesetItem.
     */
    private fun getPresetRoutingRulesets(context: Context, type: RoutingType = RoutingType.WHITE): MutableList<RulesetItem>? {
        val assets = Utils.readTextFromAssets(context, type.fileName)
        if (TextUtils.isEmpty(assets)) {
            return null
        }

        return JsonUtil.fromJsonSafe(assets, Array<RulesetItem>::class.java)?.toMutableList()
    }

    /**
     * Reset routing rulesets from presets.
     * @param context The application context.
     * @param type The routing preset type.
     */
    fun resetRoutingRulesetsFromPresets(context: Context, type: RoutingType) {
        val rulesetList = getPresetRoutingRulesets(context, type) ?: return
        resetRoutingRulesetsCommon(rulesetList)
    }

    /**
     * Reset routing rulesets.
     * @param content The content of the rulesets.
     * @return True if successful, false otherwise.
     */
    fun resetRoutingRulesets(content: String?): Boolean {
        if (content.isNullOrEmpty()) {
            return false
        }

        try {
            val rulesetList = JsonUtil.fromJsonSafe(content, Array<RulesetItem>::class.java)?.toMutableList()
            if (rulesetList.isNullOrEmpty()) {
                return false
            }

            resetRoutingRulesetsCommon(rulesetList)
            return true
        } catch (e: Exception) {
            LogUtil.e(ANG_PACKAGE, "Failed to reset routing rulesets", e)
            return false
        }
    }

    /**
     * Common method to reset routing rulesets.
     * @param rulesetList The list of rulesets.
     */
    private fun resetRoutingRulesetsCommon(rulesetList: MutableList<RulesetItem>) {
        val rulesetNew: MutableList<RulesetItem> = mutableListOf()
        MmkvManager.decodeRoutingRulesets()?.forEach { key ->
            if (key.locked == true) {
                rulesetNew.add(key)
            }
        }

        rulesetNew.addAll(normalizeImportedRulesetIds(rulesetNew, rulesetList))
        MmkvManager.encodeRoutingRulesets(rulesetNew)
    }

    /**
     * Ensures imported rulesets can join the kept list without identity collisions.
     * Import content arrives from untrusted clipboard/QR JSON and the routing UI keys
     * its LazyColumn items by [RulesetItem.id] (duplicate keys crash recomposition),
     * so any imported id that is blank or already used — by a kept item or by an
     * earlier imported item — is replaced with a fresh UUID. Ruleset ids are
     * UI-internal; core routing never consumes them.
     */
    internal fun normalizeImportedRulesetIds(kept: List<RulesetItem>, imported: List<RulesetItem>): List<RulesetItem> {
        val seenIds = kept.mapTo(mutableSetOf()) { it.id }
        return imported.map { item ->
            if (item.id.isBlank() || !seenIds.add(item.id)) {
                val fresh = item.copy(id = UUID.randomUUID().toString())
                seenIds.add(fresh.id)
                fresh
            } else {
                item
            }
        }
    }

    /**
     * Get a routing ruleset by index.
     * @param index The index of the ruleset.
     * @return The RulesetItem.
     */
    fun getRoutingRuleset(index: Int): RulesetItem? {
        val rulesetList = MmkvManager.decodeRoutingRulesets().orEmpty()
        val resolved = resolveRulesetIndex(index, rulesetList.size) ?: return null
        return rulesetList[resolved]
    }

    /**
     * Save a routing ruleset.
     * @param index The index of the ruleset.
     * @param ruleset The RulesetItem to save.
     */
    fun saveRoutingRuleset(index: Int, ruleset: RulesetItem?) {
        if (ruleset == null) return

        var rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            rulesetList = mutableListOf()
        }

        if (resolveRulesetIndex(index, rulesetList.count()) == null) {
            rulesetList.add(0, ruleset)
        } else {
            rulesetList[index] = ruleset
        }
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    /**
     * Remove a routing ruleset by index.
     * @param index The index of the ruleset.
     */
    fun removeRoutingRuleset(index: Int) {
        val rulesetList = MmkvManager.decodeRoutingRulesets() ?: return
        val resolved = resolveRulesetIndex(index, rulesetList.size) ?: return
        rulesetList.removeAt(resolved)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    /**
     * Resolves a caller-supplied list index against the current list size, or null when
     * the index is out of bounds. The ruleset list lives in MMKV and can shrink between
     * the UI capturing a position and acting on it (concurrent edit windows, double
     * taps); a stale index used to throw IndexOutOfBoundsException in
     * [getRoutingRuleset]/[removeRoutingRuleset] and could remove the wrong rule.
     */
    internal fun resolveRulesetIndex(index: Int, size: Int): Int? {
        return index.takeIf { index >= 0 && index < size }
    }

    /**
     * Check if routing rulesets bypass LAN.
     * @return True if bypassing LAN, false otherwise.
     */
    fun routingRulesetsBypassLan(): Boolean {
        val vpnBypassLan = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
        if (vpnBypassLan == "1") {
            return true
        } else if (vpnBypassLan == "2") {
            return false
        }

        val guid = MmkvManager.getSelectServer() ?: return false
        val config = decodeServerConfig(guid) ?: return false
        if (config.configType == EConfigType.CUSTOM) {
            val raw = MmkvManager.decodeServerRaw(guid) ?: return false
            val v2rayConfig = JsonUtil.fromJsonSafe(raw, V2rayConfig::class.java)
            val exist = v2rayConfig?.routing?.rules?.filter { it.outboundTag == TAG_DIRECT }?.any {
                it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
            }
            return exist == true
        }

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        val exist = rulesetItems?.filter { it.enabled && it.outboundTag == TAG_DIRECT }?.any {
            it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
        }
        return exist == true
    }

    /**
     * Get server via remarks.
     * @param remarks The remarks of the server.
     * @return The ProfileItem.
     */
    fun getServerViaRemarks(remarks: String?): ProfileItem? {
        if (remarks.isNullOrEmpty()) {
            return null
        }
        val serverList = decodeAllServerList()
        return serverList
            .mapNotNull { guid -> decodeServerConfig(guid) }
            .firstOrNull { it.remarks == remarks }
    }

    /**
     * Collects non-empty profile remarks while excluding specific config types.
     */
    fun getProfileRemarks(excludeConfigTypes: Set<EConfigType> = setOf(EConfigType.CUSTOM)): List<String> {
        return decodeAllServerList()
            .asSequence()
            .mapNotNull { guid -> decodeServerConfig(guid) }
            .filter { profile -> profile.configType !in excludeConfigTypes }
            .map { it.remarks.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /**
     * Removes the subscription.
     * If there are no remaining subscriptions,
     * it creates a new default subscription to ensure that ungroup
     **/
    fun removeSubscriptionWithDefault(subid: String) {
        SubscriptionUpdater.cancelOne(subId = subid)
        // Overflow chunk groups carved out of this subscription die with it.
        val chunkPrefix = AppConfig.DERAFSH_POOL_CHUNK_PREFIX + subid + "_"
        MmkvManager.decodeSubscriptions()
            .filter { it.guid.startsWith(chunkPrefix) }
            .forEach { chunk ->
                SubscriptionUpdater.cancelOne(subId = chunk.guid)
                removeSubscription(chunk.guid)
                LogUtil.i(AppConfig.TAG, "Removed chunk group of deleted subscription: ${chunk.guid}")
            }
        // Remove the subscription
        removeSubscription(subid)

        // After removal, check if there are any subscriptions left. If not, create a default subscription.
        val subsList2 = decodeSubsList()
        if (subsList2.isNotEmpty()) {
            return
        }

        val defaultSub = SubscriptionItem(
            remarks = "Default",
        )
        encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)
    }

    /**
     * Get the SOCKS port.
     * @return The SOCKS port.
     */
    fun getSocksPort(): Int {
        val port =
            if (IsDynamicSocksPort()) {
                runtimeOrNewSocksPort()
            } else {
                Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
            }
        return port ?: AppConfig.PORT_SOCKS.toInt()
    }

    /**
     * Returns the runtime SOCKS port, generating one only when none exists yet. The
     * whole check-and-generate is synchronized: concurrent first callers (config build,
     * root proxy, tproxy) each used to re-enter [refreshRuntimeSocksPort] through the
     * unsynchronized null-check and could receive two DIFFERENT random ports — the
     * core then listened on one while the data plane forwarded to the other.
     */
    @Synchronized
    private fun runtimeOrNewSocksPort(): Int {
        return runtimeSocksPort
            ?: refreshRuntimeSocksPort()
            ?: AppConfig.PORT_SOCKS.toInt()
    }

    @Synchronized
    fun refreshRuntimeSocksPort(): Int? {
        if (IsDynamicSocksPort()) {
            runtimeSocksPort = generateRandomSocksPort()
            return runtimeSocksPort
        }
        return null
    }

    fun getSocksUsername(): String? {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_USERNAME)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getSocksPassword(): String? {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PASSWORD)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Get the HTTP port.
     * @return The HTTP port.
     */
    fun getHttpPort(): Int {
        return getSocksPort() + if (Utils.isXray()) 0 else 1
    }

    private fun IsDynamicSocksPort(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
    }

    private fun generateRandomSocksPort(): Int {
        return Random.nextInt(10000, 65535)
    }

    /**
     * Initialize assets.
     * @param context The application context.
     * @param assets The AssetManager.
     */
    fun initAssets(context: Context, assets: AssetManager) {
        val extFolder = Utils.userAssetPath(context)

        try {
            val geo = arrayOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)
            assets.list("")
                ?.filter { geo.contains(it) }
                ?.filter { !File(extFolder, it).exists() }
                ?.forEach {
                    val target = File(extFolder, it)
                    assets.open(it).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    LogUtil.i(AppConfig.TAG, "Copied from apk assets folder to ${target.absolutePath}")
                }
        } catch (e: Exception) {
            LogUtil.e(ANG_PACKAGE, "asset copy failed", e)
        }
    }

    /**
     * Get domestic DNS servers from preference.
     * @return A list of domestic DNS servers.
     */
    fun getDomesticDnsServers(): List<String> {
        val domesticDns =
            MmkvManager.decodeSettingsString(AppConfig.PREF_DOMESTIC_DNS) ?: AppConfig.DNS_DIRECT
        val ret = domesticDns.split(",").filter { Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_DIRECT)
        }
        return ret
    }

    /**
     * Get remote DNS servers from preference.
     * @return A list of remote DNS servers.
     */
    fun getRemoteDnsServers(): List<String> {
        val remoteDns =
            MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        val ret = remoteDns.split(",").filter { Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_PROXY)
        }
        return ret
    }

    /**
     * Get VPN DNS servers from preference.
     * @return A list of VPN DNS servers.
     */
    fun getVpnDnsServers(): List<String> {
        val vpnDns = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS) ?: AppConfig.DNS_VPN
        return vpnDns.split(",").filter { Utils.isPureIpAddress(it) }
    }

    /**
     * Get delay test URL.
     * @param second Whether to use the second URL.
     * @return The delay test URL.
     */
    fun getDelayTestUrl(second: Boolean = false): String {
        return if (second) {
            AppConfig.DELAY_TEST_URL2
        } else {
            MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL)
                ?: AppConfig.DELAY_TEST_URL
        }
    }

    /**
     * Get real ping concurrency.
     * An explicitly stored value always wins. Otherwise the default scales with the batch
     * size: small manual batches run 32 workers, while large auto-maintenance pools
     * (hundreds to thousands of configs) scale up to 64 so a first full pass doesn't
     * crawl — the per-test cost is dominated by spinning up a short-lived core instance,
     * and bigger pools amortize it across more workers.
     * @param batchSize Number of configs in the batch (0 = treat as a small batch).
     * @return The number of concurrent real-ping tests (clamped to 1..128).
     */
    fun getRealPingConcurrency(batchSize: Int = 0): Int {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_REAL_PING_CONCURRENCY)
        if (stored != null) {
            return stored.toIntOrNull()?.coerceIn(1, 128) ?: 32
        }
        return when {
            batchSize <= 256 -> 32
            batchSize <= 1500 -> 48
            else -> 64
        }
    }

    /**
     * Retrieves the currently selected VPN interface address configuration.
     * This method reads the user's preference for VPN interface addressing and returns
     * the corresponding configuration containing IPv4 and IPv6 addresses.
     *
     * @return The selected VpnInterfaceAddressConfig instance, or the default configuration
     *         if no valid selection is found or if the stored index is invalid.
     */
    fun getCurrentVpnInterfaceAddressConfig(): VpnInterfaceAddressConfig {
        val selectedIndex = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")?.toInt()
        return VpnInterfaceAddressConfig.getConfigByIndex(selectedIndex ?: 0)
    }

    /**
     * Get the VPN MTU from settings, defaulting to AppConfig.VPN_MTU.
     */
    fun getVpnMtu(): Int {
        return Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_MTU), AppConfig.VPN_MTU)
    }

    /**
     * Check if HEV TUN is being used.
     * @return True if HEV TUN is used, false otherwise.
     */
    fun isUsingHevTun(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true)
    }

    /**
     * Check if VPN mode is enabled.
     * @return True if VPN mode is enabled, false otherwise.
     */
    fun isVpnMode(): Boolean {
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE)
        return mode == null || mode == VPN
    }

    /**
     * Check if a root (system-wide) run mode is selected.
     */
    fun isRootMode(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
    }

    /**
     *  Check if process routing can be used.
     */
    fun canUseProcessRouting(): Boolean {
        // Android 10+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        // Must xray tun
        if (isUsingHevTun()) {
            return false
        }

        // Must have route only enabled
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false) == false) {
            return false
        }

        return true
    }

    /**
     * Ensure default settings are present in MMKV.
     */
    private fun ensureDefaultSettings() {
        // Write defaults in the exact order requested by the user
        ensureDefaultValue(AppConfig.PREF_MODE, VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_MTU, AppConfig.VPN_MTU.toString())
        ensureDefaultValue(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
        ensureDefaultValue(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY)
        ensureDefaultValue(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        ensureDefaultValue(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DELAY_TEST_URL)
        ensureDefaultValue(AppConfig.PREF_IP_API_URL, AppConfig.IP_API_URL)
        ensureDefaultValue(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, AppConfig.HEVTUN_RW_TIMEOUT)
        ensureDefaultValue(AppConfig.PREF_MUX_CONCURRENCY, "8")
        ensureDefaultValue(AppConfig.PREF_MUX_XUDP_CONCURRENCY, AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY)
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")
        ensureDefaultValue(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
        ensureDefaultValue(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
        ensureDefaultValue(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD)
        ensureDefaultValue(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
        ensureDefaultValue(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)
    }

    private fun ensureDefaultValue(key: String, default: String) {
        if (MmkvManager.decodeSettingsString(key).isNullOrEmpty()) {
            MmkvManager.encodeSettings(key, default)
        }
    }

    private fun migrateHysteria2PinSHA256() {
        // Check if migration has already been done
        val migrationKey = "hysteria2_pin_sha256_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        val serverList = decodeAllServerList()

        for (guid in serverList) {
            val profile = decodeServerConfig(guid) ?: continue
            if (profile.configType != EConfigType.HYSTERIA2) {
                continue
            }
            if (profile.pinSHA256.isNullOrEmpty() || !profile.pinnedCA256.isNullOrEmpty()) {
                continue
            }
            profile.pinnedCA256 = profile.pinSHA256
            profile.pinSHA256 = null
            MmkvManager.encodeServerConfig(guid, profile)
        }

        MmkvManager.encodeSettings(migrationKey, true)
    }

    /**
     * One-time migration: WireGuard was removed from the app (no editor, no import paths),
     * so any stored type-7 profile is retired everywhere it can appear — every group index,
     * all payload stores, and the selected-server pointer when it pointed at one of them.
     * The EConfigType.WIREGUARD enum value itself is kept so decoding never trips on the
     * retired value during this sweep.
     */
    private fun migrateRemoveWireguardProfiles() {
        val migrationKey = "wireguard_profiles_removed"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        val guids = MmkvManager.decodeAllServerList()
            .filter { guid ->
                MmkvManager.decodeServerConfig(guid)?.configType == EConfigType.WIREGUARD
            }
        if (guids.isNotEmpty()) {
            MmkvManager.removeServerProfiles(guids)
            LogUtil.i(AppConfig.TAG, "Removed ${guids.size} retired WireGuard profile(s)")
        }
        MmkvManager.encodeSettings(migrationKey, true)
    }

    /**
     * Migrates server list from legacy KEY_ANG_CONFIGS to subscription-based storage.
     * This method should be called once during app initialization after the storage structure change.
     * Servers are grouped by their subscriptionId into respective subscription's serverList.
     * Servers without subscription are moved to the default subscription.
     * After migration, KEY_ANG_CONFIGS is removed.
     */
    private fun migrateServerListToSubscriptions() {
        // Check if migration has already been done
        val migrationKey = "server_list_to_subscriptions_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        // Ensure default subscription exists before migration
        ensureDefaultSubscription()

        // Read existing server list from legacy KEY_ANG_CONFIGS
        val oldJson = MmkvManager.readLegacyServerList()
        if (oldJson.isNullOrBlank()) {
            // No data to migrate, mark as done
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val guids = JsonUtil.fromJsonSafe(oldJson, Array<String>::class.java) ?: run {
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val subscriptionServerMap = mutableMapOf<String, MutableList<String>>()

        // Group servers by subscription (use default subscription for empty subscriptionId)
        guids.forEach { guid ->
            val config = decodeServerConfig(guid) ?: return@forEach
            val subId = config.subscriptionId.ifEmpty { DEFAULT_SUBSCRIPTION_ID }

            subscriptionServerMap.getOrPut(subId) { mutableListOf() }.add(guid)
        }

        // Update each subscription's serverList (including default subscription)
        subscriptionServerMap.forEach { (subId, serverGuids) ->
            MmkvManager.encodeServerList(serverGuids, subId)
        }


        // Mark migration as complete
        MmkvManager.encodeSettings(migrationKey, true)
    }

    /**
     * Ensures the default subscription exists for ungrouped servers.
     * This subscription is used internally to store servers without a subscription.
     * Made public for migration in SettingsManager.
     */
    private fun ensureDefaultSubscription() {
        if (decodeSubscription(DEFAULT_SUBSCRIPTION_ID) == null) {
            val defaultSub = SubscriptionItem(
                remarks = "Default",
            )
            encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)

            // Move to the top
            val subsList = decodeSubsList()
            if (subsList.moveItem(subsList.lastIndex, 0)) {
                MmkvManager.encodeSubsList(subsList)
            }
        }
    }

}
