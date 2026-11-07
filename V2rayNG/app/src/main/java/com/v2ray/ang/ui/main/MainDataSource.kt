package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    fun setSelectServer(guid: String)

    fun getConfirmRemove(): Boolean
    fun getDoubleColumnDisplay(): Boolean
    fun isGroupAllDisplayEnabled(): Boolean

    /** Derafsh: whether one-tap connect should test everything and pick the fastest server. */
    fun isAutoPickFastestEnabled(): Boolean

    /** Derafsh: epoch millis when the daemon tears the session down (0 = no active session). */
    fun getSessionDeadlineMillis(): Long

    /** Derafsh: epoch millis of the last automatic maintenance cycle (0 = never). */
    fun getLastAutoMaintenance(): Long
    fun setLastAutoMaintenance(timeMillis: Long)

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?

    /**
     * Persists a UI drag order for a group atomically: the merge runs against the
     * CURRENT storage index under the profile index lock, so configs committed after
     * the UI snapshot was taken survive (never a full-replace with a stale snapshot).
     */
    fun reorderServerList(groupId: String, uiOrder: List<String>)

    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun removeInvalidServersInGroup(groupId: String): Int

    fun clearAllTestDelayResults(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)
    fun getSubsList(): List<String>

    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int>

    fun updateConfigViaSubAll(onSubStart: (String) -> Unit = {}): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult

    /** Derafsh: removes built-in-pool configs that failed the latest real-delay pass. */
    fun sweepUnhealthyPoolConfigs(): Int

    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun share2QRCode(guid: String): android.graphics.Bitmap?
    fun share2Clipboard(guid: String): Boolean

    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage, requestId: String? = null)
    fun cancelAllPing()
    fun testCurrentServerRealPing(requestId: String)

    fun syncSubscriptions()
    fun initAssets()
}
