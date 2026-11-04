# Derafsh Core Audit Log — دفترچه بازبینی هسته

Thória-ye cod-e cloud-e Xray dar Derafsh. Har run-e cron inja miscé const (yafteh-ha, fayl-haye morur-shode, noqte-ye shoroo'-e run-e ba'adi).

> قوانین: فقط تغییر کدِ ریشه‌دار در یافته‌ی واقعی؛ چک‌پوینت `checkpoint-v1.2.25` دست‌نخورده؛ تغییر فقط-مستنداتی = کامیت بدون release.

---

## Run 1 — 2026-11-04

### File reviewed
- `core/CoreOutboundBuilder.kt` (708 lines) — full line-by-line review + diff against upstream `2dust/v2rayNG/HEAD` same file.

### Findings

| # | Severity | Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **HIGH (bug)** | 255-267 | **WireGuard remote DNS fallback broken.** `WIREGUARD_LOCAL_REMOTE_DNS = "1.1.1.1,1.0.0.1,2606:4700:4700::1111,2606:4700:4700::1001"` was wrapped as `listOf(const)` → ONE malformed entry. With IPv6 disabled (default), the IPv4 filter (`!contains(":")`) dropped that entry (it contains `:` from IPv6 parts) and the fallback re-inserted the same malformed string. Result: `wireguard.remoteDNS = ["1.1.1.1,1.0.0.1,2606:4700:4700::1111,2606:4700:4700::1001"]` — invalid single address; tunnel DNS broken. Upstream already fixed this (split default into entries + filter from the split list); the fork predates the fix. | **FIXED** — applied upstream block verbatim (comment added). Released in v1.2.26 (30). |
| 2 | Low (parity) | 122, 144, 167, 189, 211, 227, 300 | `serverPort.orEmpty().toInt()` throws `NumberFormatException` on malformed port; `convert()` has no try/catch and callers (`CoreConfigManager.convertProfile2Outbound` → `putOutbound`/chain builders) don't catch either. Same behavior exists in upstream → not a fork regression. A crash-guard would diverge from upstream. | Deferred — upstream parity; revisit if a malformed-port profile ever crashes in the wild. |
| 3 | Info | 295-296 | `toOutboundHysteria2` mutates the caller's `profileItem` (`network = "hysteria"`, `alpn = "h3"`). Safe today (fresh MMKV decode per call) but fragile if a caller ever reuses the item. | Noted, no change (upstream does the same). |
| 4 | Info | 485-517 | Hysteria2 port-hopping interval parser: clamps to ≥5, handles "a-b" and single values, falls back to "30". No crash paths found (`toIntOrNull` everywhere). | OK. |
| 5 | Info | 605-678 | Fragment/noise finalmask: `appendMask` keeps existing non-empty scopes (KCP mkcp masks preserved), `packets=tlshello` upgraded to `1-3` for REALITY. Logic verified correct. | OK. |
| 6 | Info | 44-80 | mux: disabled for SS/SOCKS/HTTP/Trojan/WG/Hysteria/XHTTP; VLESS+flow → `concurrency = -1`. Non-numeric mux setting → NFE caught → `convert()` returns null (whole outbound dropped) — harsh but caught; matches upstream. | OK. |
| 7 | Info | 680-695 | `getServerAddress` does synchronous DNS (`resolveHostToIP`) only when `PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD == "2"`; callers run config build off the main thread (CoreServiceManager io context). | OK. |

### Upstream comparison result
Only ONE hunk differs between fork and upstream for this file — exactly the WireGuard DNS fix (finding #1). After applying it, comment-stripped logic is line-identical to upstream.

### Next run starting point
- `core/CoreConfigContextBuilder.kt` (273 lines) — routing/DNS/sniffing; then `CoreServiceManager.kt` lifecycle.

### Release
- v1.2.26 (versionCode 30) — WireGuard DNS fix, bilingual changelog.

---

## Run 2 — 2026-11-04

### Files reviewed
- `core/CoreConfigContextBuilder.kt` (273 lines) — full review + upstream diff.
- `core/CoreServiceManager.kt` (589 lines) — full review + upstream diff (started this run, finished same run).

### Findings

| # | Severity | File:Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **CLEAN** | ContextBuilder (all) | Fork file is **byte-identical** to upstream `2dust/v2rayNG/HEAD`. Deep logic check: policy-group error path falls back to the group profile itself (connect fails gracefully downstream); proxy-chain order next→current→prev with remark-based lookup; DNS domain-rule normalization to proxy/direct/block only; fallback-outbound dedup vs resolved tags + builtin tags. No leaks, no shared mutable state (stateless object). | No action. |
| 2 | **CLEAN** | ServiceManager (all) | All 5 upstream-diff hunks are the Derafsh session-timer feature (scope + arm/cancel/expire). Verified: timer armed only after successful core start; cancelled on manual stop AND on start failure; expireSession is service-null-safe and notifies UI before teardown. `isReloading` @Volatile guard correct; receiver unregistered in try/catch; measureV2rayDelay cancel-children pattern correct with per-request ids. | No action. |
| 3 | Info (parity) | ServiceManager:211-218 | `stopLoop()` runs fire-and-forget in an ad-hoc `CoroutineScope(Dispatchers.IO)`; `stopCoreLoop` returns true before the core actually stops. A rapid stop→start could observe `isRunning()==true` ("Core already running"). **Upstream has the identical code** — not a fork regression. | Deferred (upstream parity). |
| 4 | Info (micro-race) | ServiceManager:249-295 | Rewarded-ad extend racing expiry: `cancel()` cannot interrupt `expireSession()` once past `delay()` (no suspension points inside), so in a millisecond window expiry can stop the core after a re-arm persisted a new deadline. Window is negligible; consequences self-heal on next start. | Noted, no change (Derafsh-owned code, acceptable). |
| 5 | Info | ContextBuilder:144-178 | Policy-group resolution decodes every stored profile (O(N) MMKV reads) — with the 9k+ free pool this is measurable but runs off-main-thread during config build only. | Noted, no change (upstream parity). |

### Upstream comparison result
- ContextBuilder: zero hunks.
- ServiceManager: 5 hunks, all Derafsh session-timer additions; no upstream fixes missing.

### Next run starting point
- `core/CoreConfigManager.kt` (1354 lines — the largest file): review in two passes. Run 3 = lines 1-700 (config assembly, DNS, routing), Run 4 = lines 700-1354 (balancer/observability/custom config) + `LauncherManager.kt` (141) + `CoreNativeManager.kt` (99).

### Release
- None this run (no code changes; docs-only commit).

---

## Run 3 — 2026-11-04

### Files reviewed
- `core/CoreConfigManager.kt` **lines 1-735** (active functions: getV2rayConfig, getV2rayConfig4Speedtest, buildV2rayCustomConfig, buildUnifiedConfig, buildOutbounds, handleNormal/ProxyChain/PolicyGroupResolvedOutbound, resolvePolicyGroupFallbackTag, shouldUseStandardObservatory, postProcessForSpeedtest, toConfigResult, initV2rayConfig, needTun, configureInbounds, configureFakeDns, collectUserRuleDomainsByTag, collectCustomOutboundDomains, configureLocalDns, configureRootModeDns, applySpeedDisabled) — full line-by-line review + upstream diff.
- Upstream-file scoping: the whole-file diff surfaced upstream fix **#6376 "Try fix dns" (commit 35f5c4e5, 2026-11-01)** living in the *active* configureDns region (fork lines 882+, originally assigned to Run 4). Analyzed and applied in this run (see finding 1). Structure map: dead commented-out old configureDns = lines 741-877; active configureDns = 882-940; buildDnsHostsFromRoutingRules = 942-985; buildDnsCnModeFromRoutingRules = 987-1028; buildDnsIranModeFromRoutingRules = 1036-1063; buildDnsFromRoutingRules = 1065-1110.

### Findings

| # | Severity | Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **HIGH (missing upstream fix)** | CoreConfigManager 891-903, 1065-1101 + V2rayConfig 355-357 | **DNS leak to wrong resolvers (sync upstream #6376).** (a) Plain remote DNS servers were registered BEFORE rule-matched servers; (b) last domestic DNS per direct-rule had no `finalQuery=true` → a failed domestic resolution fell through to remote/proxy DNS (foreign IP for direct-routed domains); (c) proxy-routed domains were backed by `remoteDns.first()` only with no `finalQuery` → single point of failure + fall-through to domestic resolvers on failure (DNS poisoning/CDN-mismatch risk in the Iran threat model). | **FIXED** — verbatim upstream sync: reorder + `finalQuery` on last domestic server + all remote servers with `finalQuery` on last + `finalQuery: Boolean?` field in `V2rayConfig.DnsBean.ServersBean`. Iran/CN expectIPs modes intentionally keep NO finalQuery (expectIPs filtering legitimately returns empty and relies on fallback; upstream also skipped CN-mode). Older cores ignore the unknown JSON field (graceful). Released in v1.2.27 (31). |
| 2 | Info | 26-27, 481-499 | `initConfigCache`/`initConfigCacheWithTun` unsynchronized lazy caches in a singleton: concurrent config builds can double-read assets — benign (idempotent immutable string, fresh `fromJson` parse per call, no shared mutable template state). | Noted, no change (upstream parity). |
| 3 | Info | 89-166 | `buildV2rayCustomConfig`: stats/policy inject-strip logic correct (keeps user policy levels, strips only system block); process→UID rewrite only under `canUseProcessRouting()`; TUN inbound injection from template does NOT copy sniffing (unlike configureInbounds:579) — fakedns over tun in custom configs depends on user's own config. Upstream-identical. | Noted, no change. |
| 4 | Info | 213-229 | POLICYGROUP catch-all balancer rule appended after all DNS rules (dns-out rules inserted at index 0) → correct evaluation order. IPIfNonMatch → ip 0.0.0.0/0+::/0 rule, else network tcp,udp. | OK. |
| 5 | Info | 335-362 | PROXYCHAIN: hop tags `proxy-<tag>-<n>`, collision check before commit, `dialerProxy` chains exit-first order (chain[0]=exit dials via chain[1]). Single valid profile degrades to NORMAL. | OK. |
| 6 | Info | 376-430 | POLICYGROUP member tag collision → member silently dropped from selector (log only); index+remarks tags are self-unique; balancer dedup by tag; fallback resolution (432-448): observatory-required → firstMemberTag (excludes dead candidates), random/roundRobin+test → user fallback or firstMemberTag, else null. | OK. |
| 7 | Info | 514-581 | configureInbounds: local proxy forced ON for hev-tun/root (matches DNS rules that reference the socks inbound — consistent); sniffing bean shared between socks and tun inbounds (intentional aliasing, same desired config); `!sniffAll` clears template destOverride before fakedns append. | OK. |
| 8 | Info | 597-630 | `decodeRoutingRulesets()` decoded multiple times per config build (per tag + per collect call) — O(N) MMKV reads, off-main-thread only. | Noted (upstream parity), possible future single-decode refactor. |

### Upstream comparison result
- Fork lines 1-890 byte-identical to upstream (zero hunks below 891).
- After this run's fix, remaining whole-file diffs = Derafsh-only additions: Iran DNS mode (call + function) and two sync comments. No other upstream fixes missing.

### Next run starting point (Run 4)
- `core/CoreConfigManager.kt` **lines 1108-1354** (`//region outbound related functions`: convertProfile2Outbound tail, putOutbound/chain builders, balancer/observability helpers `applyObservability` 1173+, `resolveOutboundDomainsToHosts` 1112+) — note lines 741-877 are a dead commented block, skim once to confirm nothing alive depends on it; then `core/LauncherManager.kt` (141 lines) + `core/CoreNativeManager.kt` (99 lines).

### Release
- **v1.2.27 (versionCode 31)** — DNS leak fix (upstream #6376 sync), bilingual changelog. Commit d359914.

---

## Run 4 — 2026-11-04

### Files reviewed
- `core/CoreConfigManager.kt` **lines 1108-1364** (resolveOutboundDomainsToHosts 1121-1164, convertProfile2Outbound 1169-1171, applyObservability 1182-1208, configureRouting 1213-1227, appendRoutingUserRule 1232-1289, buildBalancerStrategy 1295-1334, decodeObservatoryDuration/Sampling 1336-1351, BalancerStrategy 1356-1360) — full line-by-line review + upstream diff.
- Dead-block verification: lines **737-876 confirmed fully commented** via a nesting-aware tracker (Kotlin block comments nest; tracker handled nested `/* */`, `//`, string/char literals). Final depth 0 — file balanced. Cross-proof: CI compiles the file with a single active `configureDns` (882) — a live duplicate would be a redeclaration error.
- `core/LauncherManager.kt` (141 lines) — full review + upstream diff.
- `core/CoreNativeManager.kt` (100 lines) — full review + upstream diff.

### Findings

| # | Severity | File:Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **CLEAN** | ConfigManager 1108-1364 | Region is upstream-identical (whole-file remaining diff = 42 fork-only lines: Iran DNS mode + sync comments; **zero upstream-only lines**). `resolveOutboundDomainsToHosts` (mode "1"): dns-null early return unreachable in unified path (configureDns always sets dns before it); hosts map accepts single IP or list; UseIP+happyEyeballs set consistently on both branches. `appendRoutingUserRule`: balancer-tag rewrite happens BEFORE the missing-tag fallback and the fallback reads the captured original tag — order correct; ext-dat geoip cn/private replacement; UID conversion empty→null. `buildBalancerStrategy`: duration regex-validated, sampling int>0-validated — no malformed-input paths. | No action. |
| 2 | Info | ConfigManager 1121-1164 | Outbound-domain resolution is sequential per proxy outbound — for large POLICYGROUPs this is N sequential DNS lookups during connect (off-main-thread). Also runs in the speedtest path whose result is then discarded by postProcessForSpeedtest (wasted work, harmless). | Noted (upstream parity). Possible future: parallelize or skip in speedtest path. |
| 3 | Info | ConfigManager 1182-1208 | `applyObservability` merges all strategies into ONE observatory/burstObservatory (Xray allows only one per config): first strategy's probe settings as template, union of all subjectSelectors → groups with different probeUrl share the first one's. Inherent Xray constraint, upstream-identical. | Noted, no change. |
| 4 | **CLEAN** | LauncherManager (all) | **Byte-identical to upstream.** `startContextService`: complex-type configs correctly exempt from URL/IP validation; root-availability pre-checked before CoreRootService; `startForegroundService` failures wrapped (SecurityException → ISE; API-31+ ForegroundServiceStartNotAllowedException matched by class name and rethrown as ISE; other RTEs rethrown as-is); isRunning check deliberately deferred to the daemon process (comment documents why — UI process must not load native libs). Stateless; no leaks, no coroutines. | No action. |
| 5 | **CLEAN** | CoreNativeManager (all) | **Byte-identical to upstream.** init-once via `AtomicBoolean.compareAndSet`; on native-init failure `initialized.set(false)` + rethrow → retryable; all Libv2ray calls exception-guarded (`measureOutboundDelay`→-1L contract, `getLibVersion`→"Unknown"); `reconcileBrowserDialer` failure is log-only by design. `Seq.setContext` before `initCoreEnv` — AndroidLibXrayLite contract respected. | No action. |
| 6 | Info | LauncherManager 98-101 | `insecure==true && pinnedCA256 empty` → toast + copies a message to the user's clipboard (deprecated-flag UX). Quirky but intentional (upstream-identical). | Noted. |

### Upstream comparison result
- LauncherManager: zero hunks (byte-identical).
- CoreNativeManager: zero hunks (byte-identical).
- CoreConfigManager: zero upstream-only lines remaining after Run 3's #6376 sync — the file is now fully synced; the only divergences are Derafsh features (Iran DNS mode) + comments.

### Next run starting point (Run 5)
- `handler/AngConfigManager.kt` (869 lines — LAST file in the priority list; import/parse of subs): Run 5 = **lines 1-440** (import flow: parseOutbound/subscribe fetch/base64 decode paths), Run 6 = **lines 440-869** (remaining parse helpers + export/transfer) + cross-check of the ProfileItem/ServerAffiliationInfo contract used by the smart-speedtest feature. After AngConfigManager, loop back to a second pass over `handler/MmkvManager.kt` (1073) if the cron continues.

### Release
- None this run (no code changes; docs-only commit).

---

## Run 5 — 2026-11-04

### Files reviewed
- `handler/AngConfigManager.kt` **lines 1-465** (share2Clipboard 63, shareNonCustomConfigsToClipboard 86, share2QRCode 113, shareFullContent2Clipboard 134, shareConfig 156, importBatchConfig 185, parseBatchSubscription 216, parseBatchConfig 245, parseBatchProfiles 275, commitProfiles 314, parseCustomConfigServer 344, parseConfig 423-456) — full line-by-line review + upstream diff (29 hunks classified).

### Findings

| # | Severity | Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **Fork improvement (no action)** | 160-170 | `shareConfig`: fork added `EConfigType.CUSTOM -> decodeServerRaw(guid)` + `else -> config.description`. Upstream HEAD has `else -> {}` — a lambda branch in a when-expression → `scheme + lambda.toString()` pastes **garbage** to the clipboard for CUSTOM configs. Fork's CUSTOM scheme is `""` (AppConfig.CUSTOM) so the branch yields clean raw JSON; `else` covers POLICYGROUP/PROXYCHAIN (also scheme `""`) with the description. Fork is strictly better than upstream here. | No action (keep fork behavior). |
| 2 | CLEAN | 185-334 | Import flow: `importBatchConfig` tries decoded→raw→custom-config in order, then sub-URL import (raw→decoded), then `updateConfigViaSubAll()` if any subs imported — identical semantics to upstream's `parseConfigViaSub` (which the fork inlined). Fork's split of `parseBatchConfig` into parse-only `parseBatchProfiles` + `commitProfiles(ParsedProfile)` exists to serve the Derafsh chunked pool importer; `ParsedProfile`/`commitProfiles`/`saveServerProfiles` infra itself is upstream's. `ProfileStorageException` rethrow chain (parseBatchConfig/parseCustomConfigServer → importBatchConfig → "0 to 0") is fork-added error signaling — no swallow paths beyond upstream's. `.lines().distinct().reversed()` ordering and `v2raynConfigs + configs` merge are upstream-identical. | No action. |
| 3 | CLEAN | 344-412 | `parseCustomConfigServer`: JSON-array-first (per-element raw preserved via `toJsonPretty(srv)`), single-config fallback, WireGuard `[Interface]+[Peer]` detection — upstream-identical. Reversal of `serverList` preserves payload order on commit (same rationale as parseBatchProfiles). | No action. |
| 4 | Info (parity) | 442-446 | Subscription `filter` is compiled per config line: an **invalid user regex throws inside parseConfig → that config is silently dropped** (log only). A broken filter therefore drops ALL configs of the sub, one log line each. Upstream-identical behavior; UX nit only (filter edit screen validates separately upstream — same). | Noted (upstream parity). |
| 5 | Info (parity) | 433-435 | Parser map is insertion-ordered (`mapOf`→LinkedHashMap): `socks://` listed BEFORE `socks4://`/`socks5://` — prefix match routes socks4/5 to `SocksFmt::parse`, which handles all three schemes. Upstream-identical map; no misparse. | OK. |
| 6 | Info | 86-105 | `shareNonCustomConfigsToClipboard` return count = `sb.lines().count() - 1` — correct for the trailing `appendLine()`; empty-URL entries skipped before append. `share2QRCode`/`share2Clipboard`/`shareFullContent2Clipboard` all try/catch → -1/null. `shareFullContent2Clipboard` builds the FULL runtime config via `CoreConfigManager.getV2rayConfig` — safe (already audited in Runs 1-4). | OK. |

### Upstream comparison result
- 29 diff hunks total. Run-5 region: fork-only refactors/features (parse/commit split for chunked importer, CUSTOM share branch, ProfileStorageException chain, onSubStart callback, chunk-group filter) — **zero fork regressions, zero missing upstream fixes** in lines 1-465. The big fork-only block (chunked update + smart-test carry-over, lines 466-870) belongs to Run 6.
- Run 6 preliminary note recorded: fork's `updateConfigViaSub` (490) dropped upstream's redundant `TextUtils.isEmpty(it.guid)` guard (guid is the MMKV key, always non-empty) — defensive-only, evaluate in Run 6.

### Next run starting point (Run 6)
- `handler/AngConfigManager.kt` **lines 466-870**: `updateConfigViaSubAll` (463-489), `updateConfigViaSub` (490-503), chunk-group helpers (504-523), `fetchSubscriptionContent` (524-591), `updateConfigViaSubChunked` (592-705) — the Derafsh pool-chunking core (fetch-once → chunk 200 → overflow groups → carry fresh test results), `snapshotFreshTestResults` (706-733), `configIdentityKey` (734-755), `removeInvalidServer` (756-777), `sweepUnhealthyPoolConfigs` (778-833), `importUrlAsSubscription` (834-853), `generateDescription` (854-870).

### Release
- None this run (no code changes; docs-only commit).

---

## Run 6 — 2026-11-04

### Files reviewed
- `handler/AngConfigManager.kt` **lines 458-870** (updateConfigViaSubAll 463, updateConfigViaSub 490, chunkGroupId/chunkGroupPrefixFor/chunkGroupsOf 504-514, fetchSubscriptionContent 524-578, updateConfigViaSubChunked 592-696 — the Derafsh pool-chunking core, snapshotFreshTestResults 706-725, configIdentityKey 734-748, removeInvalidServer 756-764, sweepUnhealthyPoolConfigs 778-805, sortByTestResultsForSub 812-826, importUrlAsSubscription 834-847, generateDescription 854-868) — full line-by-line review + upstream diff classification.
- **Cross-checked MMKV contracts in `MmkvManager.kt`** to validate the chunked-refresh lifecycle: `saveServerProfiles` (308-385), `removeServer`/`removeServerViaSubid` (392-436), `removeServers` (444-461), `removeSubscription` (688-695), `encodeSubscription` (703-712), `encodeServerTestResult` (502-510).

### Findings

| # | Severity | Lines | Finding | Action |
|---|---|---|---|---|
| 1 | **CLEAN** | AngConfigManager 592-696 | **Chunked refresh lifecycle verified end-to-end, no orphan leak:** (a) `snapshotFreshTestResults` runs BEFORE any deletion (correct order); (b) old overflow groups removed via `removeSubscription` → which calls `removeServerViaSubid` → deletes profile/aff/raw payloads AND clears selected-server if removed; (c) `saveServerProfiles(append=false)` computes `replacedServers`, protects the selected profile via `ProfileReplacement.findSelectedReplacement`/`findRemovablePayloads` (incl. cross-group references) before `removeProfilePayloads`; (d) carry-over `encodeServerTestResult` writes fresh aff entries AFTER old payloads are removed — no overwrite risk. Mid-loop exception → lastUpdated not bumped → next refresh retries (self-healing). | No action. |
| 2 | CLEAN (design) | 706-725 | Fresh-result snapshot **carries failures too** (`testDelayMillis != 0` includes < 0): deliberate — the post-test sweep (`sweepUnhealthyPoolConfigs`) then removes the failed re-imports; after sweep-removal the next refresh's snapshot can't carry them (gone from storage) → each dead config gets exactly one re-test per refresh cycle. Steady state = healthy pool, no unbounded re-testing. | No action (correct). |
| 3 | Info | 734-748 | `configIdentityKey` omits reality pbk, fingerprint, alpn: two configs differing only in those share an identity → delay carried across. Same server+sni in practice → negligible drift. Heuristic documented. | Noted. |
| 4 | Info | 504-514 | `chunkGroupsOf` prefix `derafsh_chunk_<primary>_` could theoretically match chunks of another primary whose guid starts with `<primary>_` — impossible in practice: primaries are UUIDs or DEFAULT_SUBSCRIPTION_ID (no underscore-suffix relationships). | Noted. |
| 5 | Info (parity) | 834-847 | `importUrlAsSubscription`: `URI(fixIllegalUrl(url))` throws on a malformed URL → caught by `parseBatchSubscription`'s outer try → **remaining URLs of the batch are skipped**. Upstream-identical structure (function not in diff). | Noted (upstream parity). |
| 6 | Info (parity) | 490-497 | Fork dropped upstream's defensive `TextUtils.isEmpty(it.guid)` check (guid is the MMKV key — always non-empty from `decodeSubscriptions`). Also dropped upstream's `LogUtil.i(TAG, url)` before fetch (privacy-positive). | No action. |
| 7 | Info | 778-805 | `sweepUnhealthyPoolConfigs` scope: removeInvalidServer only for DEFAULT sub + its chunks; empty-chunk dissolution runs for ALL chunk groups (any primary) — harmless cleanup (user chunks are only empty between refresh phases). Comment "(the active source …)" refers to the built-in pool being the active source by default. | Noted. |
| 8 | Info | 812-826 | `sortByTestResultsForSub`: untested (delay ≤ 0) sorted last via MAX_VALUE, `sortedBy` stable → original order kept within equal delays. | OK. |
| 9 | Info | 854-868 | `generateDescription`: single-label hosts produce ".*** : port" (odd but harmless); IPv6 masked to first 2 groups. Upstream-identical. | Noted. |

### Upstream comparison result
- All Run-6-region code is either upstream-identical (`removeInvalidServer` logic — fork only added Int return, `sortByTestResultsForSub`, `importUrlAsSubscription`, `generateDescription`) or deliberate Derafsh features (chunked import, smart-test carry-over, sweep, onSubStart progress). **Zero fork regressions, zero missing upstream fixes.**
- `fetchSubscriptionContent` is a verbatim extraction of upstream's inline fetch logic (proxy attempt → direct attempt → usage-info capture) — semantics preserved, captured twice-by-design (last successful response wins).

### Next run starting point (Run 7)
- Priority list exhausted (all 5 core/handler files reviewed). Second pass begins with the fork's most-touched supporting code: **`handler/MmkvManager.kt` (1074 lines) Run 7 = lines 1-540** (server profile storage: saveServerProfiles, ProfileReplacement contract, removeServers/subscription, test-result encoding) — Run 8 = lines 540-1074 (subscriptions, settings, routing rules storage) + `handler/ProfileReplacement.kt` (76 lines) full review. Run 9+ = fmt parsers (`VmessFmt` 177, `VlessFmt` 47, then Trojan/Hysteria2/SS/Socks/Wireguard) focusing on parse-roundtrip correctness for the 9k-config pool.

### Release
- None this run (no code changes; docs-only commit).

---

## Run 7 — 2026-11-04

### Files reviewed
- `handler/MmkvManager.kt` — deep line-by-line of **lines 1-540** (storage init/recovery, `withProfileIndexLock`, `removeProfilePayloads`, `requireStorageWrite`, index read/write, `decodeServersReferencedByOtherGroups`, `initialize`/`recoverFromStorageError`, `getSelectServer`/`setSelectServer`, `encodeServerList`/`decodeServerList`/`decodeAllServerList`, `decodeServerConfig`/`encodeServerConfig`, `saveServerProfiles` 309-393, `removeServer`/`removeServerViaSubid`/`removeServers`, aff encoding incl. `testedAt` (Derafsh), `clearAllTestDelayResults`, `removeAllServer`) + full-file upstream diff (29 lines, all Derafsh).
- `handler/ProfileReplacement.kt` (76 lines) — **byte-identical to upstream**, contract verified against both call sites.
- Call-site tracing (fork + upstream): `ScScannerActivity`, `UrlSchemeActivity`, `MainActivity`, `MainViewModel`, `MainRepository`, `ServerGroupActivity`, `SettingsManager` seeding, `DerafshSourceManager`, `sweepUnhealthyPoolConfigs`.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | Fork improvement (no action) | MmkvManager 412, 431, 459 | Upstream declares `serverRawStorage` but removes it only in `removeServers` (upstream line 457) — its `removeServer` and `removeServerViaSubid` **leak raw payloads** on every single/subscription deletion. The fork added `serverRawStorage.remove(guid)` in both paths. | No action (keep fork behavior). |
| 2 | **HIGH (fork-context bug)** | AngConfigManager.updateConfigViaSubChunked + saveServerProfiles | **Pool refresh destroyed user content.** The built-in pool is materialized AS the DEFAULT subscription (`SettingsManager` seeding, guid `__default_subscription__`), and its group index is shared with manually imported configs (MainViewModel passes `selectedGroupId`; scanner/URL-scheme pass `""` → same index) and user-created policy groups (`subscriptionId=""` → DEFAULT). The refresh committed with `append=false` → `saveServerProfiles` rebuilt the index keeping ONLY group-type configs → every manual config in the main tab was payload-deleted at the next refresh (≤4h), and `findSelectedReplacement`'s `profiles.keys.firstOrNull()` fallback re-anchored the selection onto a random new config (stealing it from surviving policy groups). Upstream can never hit this: upstream never refreshes DEFAULT. | **FIXED** — pool generation marker (`PREF_DERAFSH_POOL_GENERATION` / `KEY_POOL_GENERATION`): the built-in pool now commits with `append=true` and retires exactly the recorded previous generation (`MmkvManager.decodePoolGeneration/encodePoolGeneration/removeServerProfiles` — strips guids from every group index + payloads + dangling selection, under the index lock). Bootstrap (first refresh after install/upgrade, no recorded generation) falls back to plain configs stamped with the pool's subscription id — groups and blank-stamped (scanner) imports survive; main-tab imports stamped with the pool id are wiped ONE last time for upgraders. Selection continuity: captured before retirement, re-anchored via `ProfileReplacement.findSelectedReplacement` against the full committed batch, else first new config. Refreshes serialized via `subscriptionUpdateLock` (retire-before-commit chosen so a mid-refresh storage failure fails exactly like the old rebuild and self-heals, instead of orphaning half-committed batches forever). User subscriptions keep the exact upstream replace semantics (`isBuiltinPool` guard). |
| 3 | **MEDIUM (selection steal, also upstream)** | MmkvManager.saveServerProfiles + ProfileReplacement.kt:48 | `findSelectedReplacement` ends with `return profiles.keys.firstOrNull()` — when the selected profile survives the commit as a kept group config (upstream commit 44721626 "Preserve groups when replacing profiles" keeps the config but not the user's selection), the fallback silently re-anchors the selection onto an arbitrary new profile. Present in upstream too. | **FIXED** — `saveServerProfiles` now computes `serverList` first and skips the matcher when `previousSelection in serverList` (selection survives → stay selected). All other paths unchanged (append=true and blank-selection bootstrap behave identically to before). |
| 4 | **MEDIUM (fork-context)** | ScScannerActivity:33, UrlSchemeActivity:82 | Deep-link (`v2rayng://`) and scanner-shortcut imports ran `importBatchConfig(..., "", false)` — upstream semantics ("replace the ungrouped index", long-standing upstream design). In the fork the ungrouped index IS the auto-refreshed pool: one import wiped ~9k pool configs (self-healing on next refresh) plus every manual config in the tab (permanent loss). | **FIXED** — both call sites pass `append=true` (matching the in-app scanner/clipboard/file flows, which already append via MainViewModel) with a comment documenting the fork-context divergence from upstream. |
| 5 | CLEAN | MmkvManager 1-308, 333-474 | Storage init/recovery (`OnErrorRecover`, log-only), `withProfileIndexLock` (synchronized + MMKV file lock; no nested acquisition anywhere — no deadlock), `decodeServersReferencedByOtherGroups` (fail-closed null on any unreadable index), `encodeServerConfig` (auto-select-first under lock, `requireStorageWrite` chain), `saveServerProfiles` write/index/removal ordering (payload writes → index publish → selection → protected removal; `add(0, guid)` per guid of the reversed parse order yields correct content order), removal paths all clear selection + profile/aff/raw. `removeAllServer` clears all three stores + every sub index (upstream-identical). | No action. |
| 6 | Info | ProfileReplacement.kt 11-49 | `findSelectedReplacement` match cascade: remarks+server+port+password → remarks → server+port+password → server+port → server → first-profile fallback. `isSameText` = trimmed, case-insensitive; blank never matches. KDoc documents only the blank-selection bootstrap, not the no-match fallback (doc drift only). | Noted. |
| 7 | Info (parity) | MmkvManager 392-436 | `removeServer`/`removeServerViaSubid`/`removeServers` do unlocked read-modify-write on the group index (lock only wraps the final `encodeServerList`) — concurrent saveServerProfiles could interleave. Upstream-identical; single-process UI flows make the window negligible. | Deferred (upstream parity). |
| 8 | Info | MmkvManager 532-542 | `removeAllServer` counts keys then clears all three stores and re-encodes empty indexes per subscription row — the DEFAULT index is included via `decodeSubscriptions()` (the pool row exists), so no ghost entries. Upstream-identical. | OK. |

### Upstream comparison result
- `MmkvManager.kt`: 29 diff lines, all Derafsh (raw-payload removals ×3, `encodeServerTestResult`+`testedAt`, clear-path timestamp reset). `ProfileReplacement.kt`: zero hunks. Upstream's `saveServerProfiles`/`parseBatchConfig`/`commitProfiles` are byte-identical to the fork's — findings #2/#3 are context bugs (fork materialized the pool into the DEFAULT index that upstream never refreshes), not transcription bugs; #3 exists upstream as well (group preserved, selection not).
- After this run's fixes the remaining divergences are: generation marker + `isBuiltinPool` branch (pool protection), `append=true` at 2 call sites, and the selection-preserve guard — all documented in-code.

### Next run starting point (Run 8)
- `handler/MmkvManager.kt` **lines 540-1143** (file grew to 1143 after this run's additions): subscriptions/`SubscriptionCache`, settings helpers, routing-rules storage, WebDav, plus a full review of `handler/OrphanProfileCleaner.kt` (interacts with the new generation retirement) and `handler/SubscriptionUpdater.kt` (refresh triggers — confirms the 4-hourly cadence that made finding #2 recurring). Run 9+ = fmt parsers (`VmessFmt`, `VlessFmt`, Trojan/Hysteria2/SS/Socks/Wireguard) — parse-roundtrip correctness for the 9k-config pool.

### Release
- **v1.2.28 (versionCode 32)** — pool-refresh data-loss fix + selection-preserve + deep-link append. Commit aa70c86.
