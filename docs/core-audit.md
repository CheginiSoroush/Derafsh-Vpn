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
- `handler/MmkvManager.kt` **lines 540-1146** (file grew to 1146 after this run's additions): subscriptions/`SubscriptionCache`, settings helpers, routing-rules storage, WebDav, plus a full review of `handler/OrphanProfileCleaner.kt` (interacts with the new generation retirement) and `handler/SubscriptionUpdater.kt` (refresh triggers — confirms the 4-hourly cadence that made finding #2 recurring). Run 9+ = fmt parsers (`VmessFmt`, `VlessFmt`, Trojan/Hysteria2/SS/Socks/Wireguard) — parse-roundtrip correctness for the 9k-config pool.

### Release
- **v1.2.28 (versionCode 32)** — pool-refresh data-loss fix + selection-preserve + deep-link append. Commits aa70c86 (fixes) + 3baf463 (compile fix: null-safe re-anchor guard; Kotlin cannot prove `committedProfiles` non-empty at the `setSelectServer` call). First CI round failed on that type error (both flavors), second round: build success, release-job hit a transient GitHub API error while creating the release (tag got created, release object not) — re-ran only the failed release job → v1.2.28 published with 10 APKs; universal APK signature verified = `2642E7F0…FD79F531`. Note: the scheduled `core-updater.yml` workflow failed with `Input required and not supplied: token` — pre-existing config gap, unrelated to this release; flagged to the user.
---

## Run 8 — 2026-11-04

### Files reviewed
- `handler/MmkvManager.kt` **lines 540-1147** (legacy `removeInvalidServer`, `encodeServerRaw`/`decodeServerRaw`, `removeOrphanedServerProfiles` + `StoredProfileReference`, subscriptions region `initSubsList`/`decodeSubscriptions`/`removeSubscription`/`encodeSubscription`/`decodeSubscription`/`decodeSubsList` (distinct-dedupe), asset region, routing-rules encode/decode, settings wrappers, WebDav, Compose `rememberMmkvString/Bool` helpers) — upstream diff: **byte-identical beyond line ~599**; every remaining diff hunk is Run-7's own additions.
- `handler/OrphanProfileCleaner.kt` (53 lines) — full contract review.
- `handler/SubscriptionUpdater.kt` (189 lines) — **byte-identical to upstream**; full review of the scheduling logic (the trigger that made Run 7 finding #2 recurring).
- Call-site/infrastructure tracing: `SettingsManager.removeSubscriptionWithDefault`, `SubEditActivity`/`SubscriptionsViewModel` delete flows, `SubscriptionUpdateService` process placement (AndroidManifest `:tasks`), `BackupViewModel` (orphan cleaner), `HttpUtil` timeouts.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **HIGH (race, introduced by Run 7's fix)** | AngConfigManager.updateConfigViaSubChunked + MmkvManager | **Cross-process refresh race.** Subscription refreshes run in TWO processes: the `:tasks` process (WorkManager `UpdateTask` → `SubscriptionUpdateService`, auto-update every 4h — the pool row is seeded `autoUpdate=true, updateInterval=240`) and the main process (manual pulls). Run 7's generation mechanism (read generation → retire → commit → record) was serialized only by a JVM `synchronized` — invisible to the other process. Two interleaved refreshes could both retire G0, both commit their batch, and the second `encodePoolGeneration` would overwrite the first's → the first batch becomes **permanently orphaned** (indexed, never retired — the old append=false rebuild was self-healing, so Run 7 inadvertently made the pre-existing cross-process race worse). | **FIXED** — `MmkvManager.withSubscriptionUpdateLock`: a dedicated empty MMKV instance (`SUBSCRIPTION_UPDATE_LOCK`, MULTI_PROCESS_MODE) whose file lock is a real inter-process mutex (same pattern as `withProfileIndexLock`). The mutation phase (generation read → snapshot → retire → chunk-row removal → commits → re-anchor → record) runs inside it; **fetch+parse moved outside** so a slow network call never holds the lock. The JVM lock was removed (MMKV `lock()` also serializes threads within a process). No lock-order cycle: nothing acquires the update lock while holding the profile-index lock, and the update-lock instance is never touched inside the critical section (verified across both files). Released in v1.2.29 (33). |
| 2 | CLEAN | OrphanProfileCleaner.kt (all) | Fail-closed everywhere: no SUB_SERVERS_* indexes at all → null (no cleanup); any unreadable index → null; undecodable payload (subscriptionId null) → preserved; a payload whose group key is absent from the index map → preserved. Blank subscriptionId maps to the DEFAULT index (line 45 `ifEmpty { DEFAULT_SUBSCRIPTION_ID }`) — scanner-imported configs are safe. Selected server always preserved. Interacts correctly with Run 7's `removeServerProfiles`: both take the mainStorage lock (no orphan window between index-strip and payload-removal). | No action. |
| 3 | CLEAN | SettingsManager.removeSubscriptionWithDefault 329-353 | User-facing subscription deletion **does** cascade to the chunk groups (`DERAFSH_POOL_CHUNK_PREFIX + subid + "_"`) and cancels every WorkManager task; recreates a bare "Default" row when the list empties. The raw `MmkvManager.removeSubscription` has no other UI callers (only migrations + the refresh/sweep paths that manage their own chunks). Run 7's cascade concern resolved. | No action. |
| 4 | CLEAN | SubscriptionUpdater.kt (all) | Interval clamped to `SUBSCRIPTION_MIN_INTERVAL_MINUTES`; initial delay derived from `lastUpdated` (0 when never updated → immediate first refresh — confirms Run 7's bootstrap analysis); REPLACE-policy floors delay at 5s (anti-reschedule-loop); `cancelOne` on delete; autoUpdate=false → task cancelled. | No action. |
| 5 | Info (parity) | SubscriptionUpdater.UpdateTask 179 | `updateLastUpdatedAndReschedule` stamps `lastUpdated` BEFORE the fetch runs — a failed auto-update still advances the timer (next attempt in a full interval, not immediately). Upstream-identical design. | Noted. |
| 6 | Info | MmkvManager 605-615 | `removeAllServer` does not clear `KEY_POOL_GENERATION` — verified safe: the recorded guids are already gone, so the next refresh's retirement is an idempotent no-op and the generation is re-recorded fresh. | Noted. |
| 7 | Info (parity) | MmkvManager 749-761 | `decodeSubscriptions` maps corrupt JSON to an empty `SubscriptionItem` → a blank tab row can appear after storage corruption (self-heals once the user re-saves the sub). Upstream-identical. | Noted. |
| 8 | Info | MmkvManager 1120-1166 | Compose `rememberMmkvString/Bool`: `drop(1)` + `distinctUntilChanged` + `collectLatest` — initial load never re-encodes; rapid toggles coalesce. Two composables bound to the same key do not mirror each other (no MMKV listener) — upstream-identical pattern, settings screens edit one key in one place. | Noted. |
| 9 | Info | SubscriptionUpdateService 151 / CoreTestService 205 | The sweep (`removeInvalidServer`) runs OUTSIDE the new refresh lock. Verified benign: sweep removal of a just-committed config whose carried test result was a failure is the DESIGNED post-test cleanup (Run 6 finding #2); index mutations are serialized by mainStorage anyway. | Noted. |

### Upstream comparison result
- `MmkvManager.kt` beyond line ~599: zero hunks. `SubscriptionUpdater.kt`: zero hunks. `OrphanProfileCleaner.kt` is upstream code (verified via the MmkvManager diff — no fork hunk touches it). The Run 8 region contains **no fork regressions and no missing upstream fixes**; the only divergences are Run 7/Run 8's own pool-protection additions.
- Process topology confirmed from the manifest: core services in `:daemon`, test + subscription-update services in `:tasks`, UI in the main process — the cross-process lock is required for exactly the main ↔ :tasks refresh pair.

### Next run starting point (Run 9)
- fmt parsers, parse-roundtrip focus for the 9k-config pool: **`fmt/VmessFmt.kt` (177 lines) + `fmt/VlessFmt.kt` (47 lines)** — field mapping vs upstream (gen/reality/flow), base64/URL decode edges, then Run 10 = Trojan/Hysteria2/SS/Socks/Wireguard fmts. Also queue: `service/SubscriptionUpdateService.kt` (:tasks update loop — the new lock's other participant).

### Release
- **v1.2.29 (versionCode 33)** — cross-process refresh serialization. Commit e4adce8. Release note: the first dispatch fired while the push was still rejected by a transient GitHub 500 — the premature run (building the previous main) was cancelled before its release step and v1.2.29 was re-dispatched on e4adce8.

---

## Run 9 — 2026-11-04

### Files reviewed
- `fmt/VmessFmt.kt` (177 lines) — **byte-identical to upstream**; full line-by-line parse/toUri/parseVmessStd analysis + roundtrip tracing.
- `fmt/VlessFmt.kt` (47 lines) — **byte-identical to upstream**; full review.
- `fmt/FmtBase.kt` (175 lines) — **byte-identical to upstream**; `toUri(config,userInfo,dicQuery)` / `getQueryParam` / `getItemFormQuery` / `getQueryDic` deep review (the roundtrip engine all 8 fmts share).
- `fmt/V2rayNFmt.kt` (73 lines) — **byte-identical to upstream** (bonus: the other parse entry feeding the pool).
- `service/SubscriptionUpdateService.kt` (235 lines) — full review; upstream diff = 8 additive hunks, ALL fork `MaintenanceStatus` integration (beginSession/finish/setSubLabel/beginTestBatch/reportBatchProgress/endTestBatch). Core update loop (semaphore(2), runningTasks accounting, worker lifecycle, START_NOT_STICKY) upstream-identical.
- `handler/MaintenanceStatus.kt` (~130 lines, bonus) — cross-process progress store behind the service's additions.
- Contract tracing: `AngConfigManager.parseConfig` (424-457 per-line try/catch + `configFmtParsers` dispatch table 42-55), `RealPingWorkerService.start` (62-97 Finish-on-cancel contract), `CoreTestService` cancelWorkers (249-259), `Utils.decode` (82-106).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (containment proof) | FmtBase.getQueryParam:43-46 | `it.split("=").let { (k, v) -> ... }` throws `IndexOutOfBoundsException` on any `=`-less query token (`?type=ws&sni`, trailing `&`) — BUT every fmt `parse` runs inside `AngConfigManager.parseConfig`'s per-line `try/catch` (429-456), so a malformed line is dropped and the rest of a 9k-config subscription imports unaffected. Dispatch via `configFmtParsers` (42-55) — distinct scheme keys, no `socks://`/`socks4://` prefix collision (4th char differs). | No action (upstream parity, contained). |
| 2 | Edge (upstream quirk, no action) | VmessFmt.parse:24 | Std-URI detection requires BOTH `?` AND `&`: `vmess://uuid@host:443?type=ws` (single param) falls into the base64 branch → decode fails → line dropped. Raw base64 can never contain `?`/`&` (alphabet), so no false positives into parseVmessStd. | No action (upstream parity, per-line loss only). |
| 3 | Edge (upstream quirk, self-healing) | VlessFmt.parse:28 | `encryption=` (present but empty) → `config.method=""` → VLESS outbound with empty encryption → that config fails to connect. In the pool context the failed real-ping marks it dead → `removeInvalidServer` sweeps it on the next test cycle. Upstream-identical. | No action. |
| 4 | CLEAN (roundtrip) | VmessFmt.toUri + FmtBase.getQueryDic | Traced JSON↔URI roundtrips: TCP `headerType=none` re-parses benign; `security=""` ↔ null (73-75 normalizes non-TLS/REALITY to null); KCP seed/path dual-write consistent; GRPC host/authority overwrite order in toUri:134-135 lands on the same value; `mode` shared by gRPC+XHTTP intentionally (both use query key `mode`); insecure keys precedence (`insecure=0`+`allowInsecure=1` → true) documented upstream; REALITY fields (pbk/sid/spx/pqv) and new ech/fm/pcs roundtrip through `nullIfBlank` guards. `aid` hardcoded "0" on export — upstream dropped alterId entirely (Xray legacy). | No action. |
| 5 | CLEAN (lifecycle) | SubscriptionUpdateService 96-119 | `runningTasks` accounting correct: one increment per START; each batch decrements in its own `finally` AFTER `deferred.await()` resumed (workers removed from `activeWorkers` in the Finish handler BEFORE await resumes), so at the last decrement `activeWorkers` can only contain another batch's workers if that batch is still mid-await — in which case its decrement is still pending. No 0-crossing missed, no double-decrement. `Semaphore(2)` releases via `withPermit` on all paths. | No action. |
| 6 | CLEAN (contract) | RealPingWorkerService 85-97 + both consumers | Cancelled workers never emit `Finish` (91-92, deliberate). `SubscriptionUpdateService` awaits `Finish`-only — but its only cancel trigger is `onDestroy` (serviceJob.cancel kills the awaiter too), so no hang. `CoreTestService` is event-driven (no await) and `cancelWorkers` (249-259) explicitly drops progress entries for the never-Finish case. Both consumers honor the contract. | No action. |
| 7 | Info (cosmetic race) | MaintenanceStatus beginTestBatch/publishTestProgress | `PREF_MAINT_TOTAL` is read-modify-written from the `:tasks` process (beginTestBatch) while `CoreTestService` (main process) writes it absolutely (publishTestProgress) — a concurrent auto-update test + manual bulk test can interleave and show a wrong number on the maintenance card. MMKV MULTI_PROCESS_MODE guarantees visibility, not atomicity. Card-only cosmetic; no connection impact. | Noted (deferred — cosmetic). |
| 8 | Info | SubscriptionUpdateService 176 | `testSubscriptionServers` snapshots `decodeServerList(subId)` before testing; a concurrent refresh (other semaphore permit) may retire those guids mid-test → `encodeServerTestDelayMillis` writes results for retired guids → orphaned aff entries without an index entry, cleaned by `OrphanProfileCleaner` on next backup. Harmless. | Noted. |
| 9 | Info | SubscriptionUpdateService 181-190 | `lateinit var worker` captured by its own `onEvent` lambda before assignment — safe only because `RealPingWorkerService` emits events strictly after `start()` (construction launches coroutines on `scope.launch`, never inline). Verified in worker source. | Noted. |

### Upstream comparison result
- `VmessFmt.kt`, `VlessFmt.kt`, `FmtBase.kt`, `V2rayNFmt.kt`: **zero diff hunks**. `SubscriptionUpdateService.kt`: 8 hunks, all additive fork MaintenanceStatus calls. **No fork regressions, no missing upstream fixes** in the whole parse/roundtrip/update-service flow.
- Parse-side safety net confirmed end-to-end for the 9k pool: `parseConfig` per-line try/catch → `Utils.decode` (standard → URL-safe → padding-trim retry) → per-line drop on any garbage; nothing can abort a batch import.

### Next run starting point (Run 10)
- Remaining fmt parsers, same roundtrip focus: **`fmt/TrojanFmt.kt` (51) + `fmt/Hysteria2Fmt.kt` (72) + `fmt/ShadowsocksFmt.kt` (124)** — SS plugin parsing and base64 userinfo edges are the historical bug magnets; then Run 11 = `fmt/SocksFmt.kt` (57) + `fmt/WireguardFmt.kt` (132) + `fmt/CustomFmt.kt` (26) + `fmt/V2rayNFmt` deep-dive (policy-group ChildItems regex escape path).

### Release
- None this run (no code changes; docs-only commit).

---

## Run 10 — 2026-11-04

### Files reviewed
- `fmt/TrojanFmt.kt` (51 lines) — **byte-identical to upstream**; full parse/toUri review + downstream contract trace (`toOutboundTrojan`: port/password/flow + transport + TLS).
- `fmt/Hysteria2Fmt.kt` (72 lines) — **byte-identical to upstream**; full review + trace of the fork-relevant hysteria2 outbound block (`CoreOutboundBuilder` 483-542: `auth=password`, portHopping/mport interval normalization with 5s floor, obfsPassword→salamander finalmask, brutalUp/Down congestion switch).
- `fmt/ShadowsocksFmt.kt` (124 lines) — **byte-identical to upstream**; deep dive on the two historical bug magnets: SIP002 userinfo handling and the obfs plugin parser, plus the legacy base64 path and `Utils.decode`/`FmtBase.toUri` roundtrip proofs.
- Helper contracts re-verified for this run: `Utils.decode` (never throws: standard → URL-safe → padding-trim retry → `orEmpty()`), `Utils.fixIllegalUrl` (space→%20, pipe→%7C), `ProfileItem` defaults (`security/insecure/serverPort = null`), `getItemFormQuery` security normalization (non-TLS/REALITY → null) + insecure key precedence, `getQueryDic` insecure dual-key emission (TLS only), `FmtBase.toUri` (`encodeURIComponent` on userInfo + remarks; `java.net.URI.getUserInfo()` auto-decodes on re-parse → roundtrip closed).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | Edge (upstream quirk) | TrojanFmt.parse:24 | Portless URI (`trojan://pw@host`) → `uri.port=-1` → `serverPort="-1"`. `"-1".toInt()` parses (no crash) → outbound port -1 → connection fails; in the pool context real-ping fails → `removeInvalidServer` sweeps it. Hysteria2Fmt HAS the guard (`if (uri.port == -1) DEFAULT_PORT`), Trojan doesn't — upstream's own asymmetry. | No action (upstream parity; self-healing in pool; diverging would break future upstream sync). |
| 2 | Edge (upstream quirk, self-healing) | ShadowsocksFmt.parseSip002:46-49 | Userinfo that decodes to a string without `:` → `result.count()==1` → method/password stay null → profile stored → outbound invalid → ping fails → pool sweep. Contained by design. | No action (parity). |
| 3 | CLEAN (no false positive) | ShadowsocksFmt.parseSip002:53 | `plugin?.contains("obfs=http")` checked against real collision candidates: `obfs=tls` ✗, `obfs-host=…` ✗ (needs literal `obfs=` immediately followed by `http`), `v2ray-plugin` ✗. Compliant SIP002 percent-encoded plugin values decode via `getQueryParam` before the check → parse correctly into network=tcp/headerType=http/obfs-host→host/obfs-path→path. Raw unencoded `;` values (SIP002-noncompliant) truncate at the first `=` pair — parity. | No action. |
| 4 | CLEAN (impossible edge) | ShadowsocksFmt.parseLegacy:79 | `str.replace("ss://", "")` replaces ALL occurrences — theoretical mid-payload corruption is IMPOSSIBLE: `:` and `@` are not in the base64 alphabet, so a base64 payload can never contain the literal `ss://`. Prefix stripping is safe. Plaintext portless legacy URIs fail both paths (SIP002: port ≤ 0; legacy regex can't match a leading `@`) → dropped per-line. | No action. |
| 5 | Edge (upstream quirk) | ShadowsocksFmt.toUri:123 | Export passes `dicQuery=null` → plugin/obfs fields (headerType/host/path) are NOT re-emitted in the share URI; share→re-import loses the http-obfs settings (profile in storage keeps them). Upstream-identical for years; fixing would diverge the share format. | Noted (deferred — upstream parity). |
| 6 | CLEAN (roundtrip) | SS/Trojan/Hysteria2 toUri | Proved closed roundtrip: `Utils.encode(pw, removePadding=true)` → `encodeURIComponent` (+→%2B, /→%2F) → `URI.getUserInfo()` decodes back on import; `limit=2` split preserves `:` inside passwords; remarks `#`-encoded/decoded; `security=` empty-string edge (getItemFormQuery normalizes to null, then raw override at TrojanFmt:35/Hysteria2Fmt:36) → "" only from an empty `security=` param — upstream parity. Hysteria2 `obfs-password` without `obfs` param is functionally CORRECT downstream (outbound builder treats non-empty obfsPassword as salamander) and toUri re-emits both params. `insecure=0/1` dual-key precedence roundtrips. | No action. |
| 7 | Contained (known) | getQueryParam via TrojanFmt:32 | Trailing `&` (or any `=`-less token) → IndexOutOfBoundsException → whole LINE dropped by `parseConfig` per-line try/catch (Run 9 #1). | No action (contained). |

### Upstream comparison result
- `TrojanFmt.kt`, `Hysteria2Fmt.kt`, `ShadowsocksFmt.kt`: **zero diff hunks**. No fork regressions, no missing upstream fixes. All behavior edges found are upstream-native quirks, each contained by the per-line parse try/catch or self-healing via the pool's ping→sweep cycle.
- Parse-side safety net now verified across 6 of 9 fmt files (Vmess, Vless, V2rayN, Trojan, Hysteria2, SS): nothing in these parsers can abort a batch import of the 9k pool; worst case is a single dropped line.

### Next run starting point (Run 11)
- Last three fmt parsers, then the parse layer is DONE: **`fmt/WireguardFmt.kt` (132 lines — reserved/preSharedKey/localAddress/mtu parsing is the densest) + `fmt/SocksFmt.kt` (57) + `fmt/CustomFmt.kt` (26)**. After that: `handler/V2rayConfigManager.kt`-adjacent policy-group paths or `handler/SettingsManager.kt` (second pass) — pick per remaining risk at Run 11 start.

### Release
- None this run (no code changes; docs-only commit).

---

## Run 11 — 2026-11-04

### Files reviewed
- `fmt/WireguardFmt.kt` (132 lines) — full review of `parse` (URI path), `parseWireguardConfFile` (INI-style section parser) and `toUri`; downstream trace of the WG outbound block (`CoreOutboundBuilder` 265-293: secretKey/addresses/peers/reserved/remoteDNS/finalmask) + upstream diff.
- `fmt/SocksFmt.kt` (57 lines) — **byte-identical to upstream**; full review + roundtrip proof.
- `fmt/CustomFmt.kt` (26 lines) — **byte-identical to upstream**; full review of the raw-JSON path.
- Helper contracts re-verified: `removeWhiteSpace` (defined on `String?` — null-safe, StringExt.kt:8), `Utils.parseInt` (toIntOrNull ?: default — never throws), `parseWireguardConfFile` containment at AngConfigManager:393-409 (try/catch, ProfileStorageException rethrown), `ProfileItem.getServerAddressAndPort` CUSTOM loopback fallback (ProfileItem.kt:85-88).

### Upstream diff
- `WireguardFmt.kt`: exactly **2 hunks**, both the same deliberate fork default: `remoteDNS = queryParam["dns"] ?: AppConfig.WIREGUARD_LOCAL_REMOTE_DNS` (and the conf-file equivalent) where upstream falls back to `""`. Pre-existing fork behavior (the fork presets WG remote DNS instead of leaving it empty) — same constant Run 1 repaired. NOT a bug; changing it would remove an intentional fork default. **No action.**
- `SocksFmt.kt`, `CustomFmt.kt`: zero hunks.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **Containment upgrade (revisits Run 1 #2)** | CoreConfigManager.kt:39-54, 60-84 | The top-level config-build entries (`getV2rayConfig`, speedtest variant) wrap the ENTIRE build in `try/catch(Exception)` → `ConfigResult(status=false, errorMessage=...)`. So the previously-deferred "convert() throws on malformed port" class (Run 1 #2) — and its new WG sibling below — lands in a GRACEFUL failure (toast / failed test), not a crash. Per-profile real-ping tests fail individually → pool sweep still works. | Re-verified: deferral is safe; no crash path. |
| 2 | Edge (upstream quirk) | CoreOutboundBuilder:288 | WG `reserved` consumed via `map { it.trim().toInt() }` — malformed (`reserved=a,b,c` or overflow) throws NumberFormatException → caught by finding #1's top-level catch → graceful failure. In a user policy group, one malformed member fails the WHOLE group's config build (members convert in one build); in the pool, per-profile tests fail → sweep. Upstream-identical. | Noted (parity; contained by #1). |
| 3 | Edge (upstream quirk) | WireguardFmt.parseWireguardConfFile:90 | IPv6 endpoint (`[2001:db8::1]:51820`) split on first `:` → garbage server/port (`server="[2001"`) → dead config, graceful failure at connect. IPv4 endpoints fine. No `removeSurrounding("[","]")` like the legacy SS regex has. Upstream-identical. | Noted (parity). |
| 4 | CLEAN | WireguardFmt.toUri | `removeWhiteSpace()` is `String?.`-safe; `mtu!=null` skip re-defaults on re-import; `preSharedKey`/`dns`/`reserved` null-guards emit only set fields; `fm` roundtrips via `nullIfBlank`. Parse↔export consistent. | No action. |
| 5 | CLEAN (roundtrip) | SocksFmt | No-auth export encodes `":"` → base64 `"Og"` → re-import decodes to `["",""]` → username/password = "" (harmless = no auth). User+pass roundtrip via limit=2 split. Guards: host empty / port ≤ 0 → null → line dropped. The `"${username}:null"` template edge requires username set + password null — unreachable from UI (both set together) and import (both set together). | No action. |
| 6 | CLEAN | CustomFmt | `fromJson` null-tolerant → server/port null → CUSTOM loopback fallback (`ProfileItem.kt:85-88`) → remarks fall back to timestamp. Raw-JSON import cannot crash (caller try/catch, AngConfigManager:376-391). | No action. |
| 7 | Info | WireguardFmt.parse:23 | WG URI without query (`wireguard://key@host:port`) → rejected (`rawQuery` null check) → line dropped. Portless URI → port "-1" (same class as TrojanFmt, Run 10 #1) → graceful failure via #1. Upstream-identical. | Noted (parity). |
| 8 | Info | AngConfigManager:393 | Conf-file detection requires `startsWith("[Interface]") && contains("[Peer]")` — an [Interface]-only conf is silently not imported (return 0). Upstream-identical. | Noted (parity). |

### Milestone
- **The parse/import layer is now fully reviewed** (all 9 fmt parsers + FmtBase): Vmess, Vless, V2rayN, Trojan, Hysteria2, Shadowsocks, Socks, Wireguard, Custom. Verified end-to-end property: no parser can abort a batch import; worst case is one dropped line; malformed profiles that DO import fail gracefully at build time (top-level catch) and are swept from the pool by the ping cycle.

### Next run starting point (Run 12)
- **`handler/SettingsManager.kt` (682 lines)** — the settings→context bridge that feeds `CoreConfigContextBuilder` (DNS mode, routing rules, per-app proxy); highest-risk remaining unreviewed file. Then Run 13 = `util/HttpUtil.kt` (311, subscription fetch/timeouts) or `dto/V2rayConfig.kt` (470, JSON contract) — pick per risk.

### Release
- None this run (no code changes; docs-only commit).

---

## Run 12 — 2026-11-04

### Files reviewed
- `handler/SettingsManager.kt` (683 lines) — full line-by-line: `initApp` order, fork `seedDefaultSubscription` (URL/interval migrations, single-source migration, chunk-namespace dissolution, seeding/rename), `initRoutingRulesets` (Iran preset seeding + one-shot China→Iran migration), routing rulesets CRUD (`getPresetRoutingRulesets`/`resetRoutingRulesets*`/locked-ruleset preservation), `routingRulesetsBypassLan` (CUSTOM raw-JSON path), `removeSubscriptionWithDefault` (chunk cascade), dynamic SOCKS port (`getSocksPort`/`refreshRuntimeSocksPort`/`generateRandomSocksPort`), DNS getters, `getCurrentVpnInterfaceAddressConfig`, `ensureDefaultSettings`, `migrateHysteria2PinSHA256`, `migrateServerListToSubscriptions`, `ensureDefaultSubscription`.
- `handler/DerafshSourceManager.kt` (33 lines, bonus — the seeding dependency): `coerceIn`/`toIntOrNull` everywhere → corrupted active-source value safely defaults to 0; `sourceUrl` cannot OOB. CLEAN.
- Upstream diff: **140 lines total, 2 hunks** (`initApp` region + `removeSubscriptionWithDefault`) — ALL fork additions (pool seeding/migrations + chunk cascade). Everything else byte-identical to upstream HEAD.
- Infrastructure tracing: dynamic-port consumers (`CoreConfigManager:523` config build, `TProxyService:70` HEV config, `RootProxyManager:115` root tunnel), manifest process topology (`:daemon` core/VPN/TProxy, `:tasks` test/update, `:bg` worker), `CoreVpnService:200` / `TProxyService:73` for the interface-address reader.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (design verified) | SettingsManager:359-400 + LauncherManager:96 | Dynamic SOCKS port lifecycle: `startContextService` refreshes ONCE per service start (anti-fingerprint new-port-per-session); after that `runtimeSocksPort` is stable so config build, TProxy HEV config and root tunnel all read the SAME value. `runtimeSocksPort` is a per-process JVM field and the manifest splits `:daemon`/`:tasks` — each process generates its own port, but this is BENIGN: every port consumer (config builder + tunnel builder) is co-located with the core it configures; no cross-process consumer of the raw port exists. | No action (self-consistent). |
| 2 | Edge (upstream quirk) | SettingsManager:497-499 | `getCurrentVpnInterfaceAddressConfig`: `decodeSettingsString(PREF, "0")?.toInt()` unguarded — a corrupted stored index would throw NumberFormatException into `CoreVpnService.configureNetworkSettings`/`TProxyService.buildConfig`. Value is only written by the settings UI as a valid index; restore-from-backup of a foreign install is the only theoretical injector. Upstream-identical. | Noted (parity; negligible probability). |
| 3 | Info (asymmetry, upstream) | SettingsManager:461-464 | `getVpnDnsServers` has NO empty-fallback (returns empty list if the user's value is all-invalid) while domestic/remote getters fall back to defaults. Consumer is the VPN builder's addDnsServer — an empty list degrades to platform DNS behavior, not a crash. Upstream-identical. | Noted (parity). |
| 4 | Info (fork design) | SettingsManager:99-106 (seed) | `seedDefaultSubscription` renames the built-in subscription row to "Derafsh N" whenever remarks differ — a user rename of the built-in row is overridden on next launch. Deliberate ("keep naming in sync with the switcher"); the row is app-managed, url/interval preserved. | Noted (intended). |
| 5 | CLEAN | initRoutingRulesets 121-148 | Iran migration is safe: fresh installs seed WHITE_IRAN directly (upstream HEAD also ships WHITE_IRAN in RoutingType — verified); existing installs migrate ONLY when the stored rulesets are byte-equal (JSON) to the untouched China preset → user customizations never touched; flag set once outside runCatching (one-shot, idempotent). | No action. |
| 6 | CLEAN | seedDefaultSubscription 44-108 | All four migrations are one-shot flagged; legacy pool removal (`__derafsh_plus1/2/turbo__`) and legacy chunk dissolution run BEFORE the namespaced scheme can create new chunk rows (flag set in the same init) → cannot destroy fresh data; DEFAULT row creation + URL/interval migrations idempotent; DerafshSourceManager clamps every index. | No action. |
| 7 | CLEAN | migrateHysteria2PinSHA256 / migrateServerListToSubscriptions | Guarded by migration flags; unreadable legacy JSON → mark-done (no data to migrate anyway); per-profile type/prefilled checks; `encodeServerList` only on first boot. | No action. |
| 8 | Info (cosmetic, upstream) | SettingsManager:482 | `getRealPingConcurrency` KDoc says "clamped to 1..64" but code clamps `1..128` (doc drift). Upstream-identical. | Noted. |
| 9 | Info | getServerViaRemarks:300-308 | O(n) full-pool decode per exact-remarks lookup (9k configs) — upstream-identical; call sites are per-add duplicate checks. | Noted. |

### Upstream comparison result
- SettingsManager: 2 hunks, both fork pool-management additions; everything else byte-identical. `DerafshSourceManager` is fork-only and clamped. **No fork regressions, no missing upstream fixes.** Upstream already ships `RoutingType.WHITE_IRAN` + `custom_routing_white_iran` asset — the fork's Iran preset is upstream-native, not a fork hack.

### Next run starting point (Run 13)
- **`util/HttpUtil.kt` (311 lines)** — the subscription-fetch layer feeding the 9k pool (timeouts, TLS error handling, proxy-then-direct retry, usage-info parsing). Then Run 14 = `dto/V2rayConfig.kt` (470, JSON contract + `getProxyOutbound`).

### Release
- None this run (no code changes; docs-only commit).

---

## Run 13 — 2026-11-04

### Files reviewed
- `util/HttpUtil.kt` (311 lines) — **byte-identical to upstream**; full line-by-line: `toIdnUrl`/`toIdnDomain` (IDN conversion), `resolveHostToIP` (null-on-IP/failed passthrough contract), `getUrlContent` (simple fetch), `getUrlContentWithUserAgent` (manual ≤3-hop redirect loop, custom headers, embedded basic-auth extraction, preemptive Proxy-Authorization), `buildOkHttpClient` (connect+read timeouts, per-call client, proxy-authenticator anti-loop guard), `resolveLocation` (URI-resolve fallback chain), `downloadToFile` (streamed copy).
- Consumer tracing: `downloadToFile` → `UserAssetViewModel.tryDownload` (temp-file + rename-on-success; **upstream-identical** including the unchecked `renameTo` result); `toIdnUrl` → `AngConfigManager` refresh path (the finding below); `resolveHostToIP` → CoreOutboundBuilder:698 / CoreConfigManager:1146 / CertificateFingerprintManager:69 (both null semantics treated as passthrough).
- Helper contracts: `Utils.isValidUrl` = `Patterns.WEB_URL || Patterns.DOMAIN_NAME || URLUtil.isValidUrl` (accepts SCHEME-LESS URLs); `Utils.isValidSubUrl` = https/http+private-IP checks; SubEditActivity validates with `isValidUrl` before save.

### Finding — REAL fork regression, FIXED, released v1.2.30

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **HIGH (fork regression — lost upstream per-sub error isolation)** | AngConfigManager.updateConfigViaSub (491-511) vs upstream 463-540 | Upstream wraps the WHOLE per-sub body of `updateConfigViaSub` in try/catch → one broken subscription fails alone (`failureCount=1`) and the `updateConfigViaSubAll` fold continues. The fork's chunked refactor (Run 7 era) kept a catch ONLY for the lock-protected mutation phase (updateConfigViaSubChunked 766-769) — the fetch+parse phase (601-631) lost the guard. Concretely: a scheme-less subscription URL (e.g. `example.com/sub`) passes UI save validation (`Patterns.WEB_URL` matches scheme-less) but `HttpUtil.toIdnUrl` → `java.net.URL()` throws MalformedURLException at AngConfigManager:610 BEFORE the `isValidUrl` guard → propagates through `updateConfigViaSub` → aborts the ENTIRE `updateConfigViaSubAll` fold; every LATER subscription stays un-refreshed for the whole cycle AND the failure is swallowed by the outer catch (empty result — user sees nothing). Verified contained: `fetchSubscriptionContent` (both attempts try/catch → ""), `parseBatchProfiles` (whole-body try → emptyList), `Utils.decode` (never throws) — `toIdnUrl` is the only unguarded throw in the phase. | **FIXED** — per-subscription try/catch restored in `updateConfigViaSub` (upstream-faithful: catch Exception → log + failureCount=1). Commit d7a3d4a. Released **v1.2.30 (versionCode 34)**. |
| 2 | CLEAN | HttpUtil all | Redirect loop: manual 3-hop with `followRedirects=false`; `isRedirect` → `resolveLocation` (absolute→relative fallback chain) → null ⇒ IOException (contained); null `request.url` degenerates to "Too many redirects" throw (contained, misleading message only). Custom headers replace case-insensitively via OkHttp `.header()`, per-header IllegalArgumentException swallowed. `Connection: close` prevents keep-alive leakage. Preemptive Proxy-Authorization + challenge-based proxyAuthenticator with anti-407-loop guard. Per-call OkHttpClient (no shared pool — perf note, upstream-identical). | No action (parity). |
| 3 | Edge (upstream quirk) | toIdnUrl:38 | `str.replace(host, asciiHost)` replaces ALL occurrences — a host string repeated in path/query is also converted (same semantic value, harmless in practice); IDN conversion failure modes are upstream-identical. | Noted (parity). |
| 4 | Edge (upstream quirk) | getUrlContentWithUserAgent | `request.url == null` → burns the redirect budget and throws "Too many redirects" (misleading, contained). No read-callTimeout — a trickling server can stretch a fetch (bounded by worker lifecycle). Upstream-identical. | Noted (parity). |

### Upstream comparison result
- `HttpUtil.kt`: zero hunks. The regression was in the fork's refactor of AngConfigManager's refresh path, not in HttpUtil itself. Fix restores exact upstream catch semantics (upstream's own per-sub catch also converts storage failures to failureCount=1; the fork's lock-phase catch already did the same for the mutation phase).

### Release
- **v1.2.30 (versionCode 34)** — commit d7a3d4a. Dispatch HTTP 204 → two runs on the same commit (push run artifact-only by design, dispatch run releases): BOTH `completed success`. Release v1.2.30 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 14)
- **`dto/V2rayConfig.kt` (470 lines)** — the JSON contract all builders serialize into (`getProxyOutbound`, bean defaults, transient/aliased field names vs Xray-core's expectations), then Run 15 = remaining util (`Utils.kt` second pass for `getIpv6Address`/`isPureIpAddress` edge cases) or `handler/WebDavManager.kt` (240, backup/restore path) — pick per risk.


---

## Run 14 — 2026-11-04

### Files reviewed
- `dto/V2rayConfig.kt` (470 lines) — **byte-identical to upstream**; full line-by-line of the JSON contract: V2rayConfig top-level, InboundBean/InSettingsBean/SniffingBean, OutboundBean/OutSettingsBean (the all-protocol union bean), StreamSettingsBean + every settings bean (Tcp/Kcp/Ws/Httpupgrade/Xhttp/Http/Sockopt/Tls+REALITY/Quic/Grpc/Hysteria/FinalMask/Mux), DnsBean/ServersBean (incl. Run 3's `finalQuery`), RoutingBean/RulesBean/BalancerBean/Strategy*, PolicyBean, Observatory/BurstObservatory/Fakedns beans, `getProxyOutbound`/`getAllProxyOutbound`/`getServerAddress`/`getServerPort`/`ensureSockopt`.
- `util/JsonUtil.kt` (119 lines, bonus — the other half of the contract): `gson = Gson()` (plain — **nulls omitted**), `toJsonPretty` (pretty + disableHtmlEscaping + `ToNumberPolicy.LONG_OR_DOUBLE` + custom whole-Double→Long serializer with the "core will fail to start" comment), `parseString`/`parseHeadersToMap` (contained).
- Emission-path tracing: core config built via `JsonUtil.toJsonPretty` (CoreConfigManager:126/165/474) → the null-omission and number-fix apply to everything Xray receives; storage encodes use plain `toJson` (nulls omitted; ProfileItem data-class defaults backfill on decode).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (contract proof) | V2rayConfig.kt + JsonUtil | The all-protocol union `OutSettingsBean` (id/security/encryption/flow/password/method/version/secretKey/peers/reserved/mtu/remoteDNS in ONE class) is safe BECAUSE plain Gson omits null fields: each protocol's outbound JSON contains only its own keys, and Xray's JSON unmarshaler ignores absent fields. Same for the InSettingsBean union and TlsSettingsBean reused as realitySettings. Verified the emission path runs through `toJsonPretty` which also omits nulls. | No action. |
| 2 | CLEAN (field-name parity) | StreamSettingsBean tree | Mixed-case sockopt keys (`TcpNoDelay`, `tcpKeepAliveIdle`, `tcpFastOpen`, `tproxy`, `mark`, `dialerProxy`, `domainStrategy`, `happyEyeballs`) and grpc snake_case (`idle_timeout`, `health_check_timeout`) match Xray-core's JSON keys exactly; TLS keys (`pinnedPeerCertSha256`, `echConfigList`, `verifyPeerCertByName`) and REALITY (`publicKey`/`shortId`/`spiderX`/`mldsa65Verify`) likewise. Only two @SerializedName needed (`User-Agent`, `Accept-Encoding` in TCP request headers). | No action. |
| 3 | CLEAN (number safety) | JsonUtil.toJsonPretty | `Any?`-typed slots (xhttp `extra`, `finalmask`, DNS `servers` list elements, routing `attrs`) go through Gson's Double parse; the custom serializer re-emits whole-number doubles as longs — the documented Xray start-failure on "1.0"-style ints is prevented. LONG_OR_DOUBLE parse keeps big ints exact. | No action. |
| 4 | CLEAN | OutboundBean.getServerAddress/getServerPort 311-325 | WG endpoint split via `substringBeforeLast(":")`/`substringAfterLast(":")` — bracketed IPv6 (`[::1]:51820`), unbracketed IPv6 and host:port all resolve; missing port → `toIntOrNull()` null → contained downstream (Run 11 #6 CUSTOM loopback fallback). Non-WG: `settings?.address as? String` — the `Any?` address (list form for chain outbounds) safely degrades to null. | No action. |
| 5 | CLEAN | getProxyOutbound 454-463 | First outbound whose protocol matches any EConfigType name (case-insensitive) — freedom/blackhole/api/logcat outbounds skipped; null return handled by CustomFmt (Run 11 #6). Order-sensitive only for multi-proxy configs (upstream-identical). `getAllProxyOutbound` filters the same predicate. | No action. |
| 6 | Info | MuxBean default `Mux(false)` | Serialized as `{"enabled":false}` with null concurrencies omitted — mux stays off unless a profile sets it (golden-rule check: contract verified, default untouched). | No action. |
| 7 | Info (edge, upstream) | GrpcSettingsBean.idle_timeout `Int?` | Xray accepts duration strings ("60s") or seconds ints — the builder emits ints (Run 9 roundtrip note). Upstream-identical. | Noted. |

### Upstream comparison result
- `V2rayConfig.kt`: **zero diff hunks**. The fork's DnsBean.ServersBean.finalQuery (Run 3) is upstream's own field now. No fork regressions, no missing upstream fixes in the JSON contract layer.

### Next run starting point (Run 15)
- **`handler/WebDavManager.kt` (240 lines) + the restore path it feeds** (`BackupViewModel`/`SettingsManager` restore → MMKV overwrite) — this is the one write-path that can inject arbitrary MMKV values (the theoretical URL-injector flagged in Run 13 finding #2, and restore-time consistency with the Run 7/8 pool-generation markers). Then Run 16 = `util/Utils.kt` second pass (`getIpv6Address`, `isPureIpAddress`, `isCoreDNSAddress`, `fixIllegalUrl` edge cases) or `handler/AdsManager.kt` — pick per risk.

### Release
- None this run (no code changes; docs-only commit).

---

## Run 15 — 2026-11-04

### Files reviewed
- `handler/WebDavManager.kt` (240 lines) — full line-by-line: init/timeout contract (connect+read+write+call all = timeoutSeconds), `uploadFile`/`downloadFile` (fail-fast security guard before credentials, per-call `.use` on response+streams, catch-all → false), `buildRemoteUrl` (baseUrl trimEnd('/') + remoteBasePath trim('/') + fileName trimStart('/') join), `isSecureRemoteUrl`, `redactUrlCredentials`, `applyAuth`, `ensureRemoteDirs` (segmented MKCOL, best-effort, 201/405/409 accepted).
- **Restore path tracing** (the Run 13 #2 "theoretical URL-injector" write-path): `BackupViewModel` (205) → `restoreViaWebDav`/`restoreConfiguration` → `performRestore` (unzip to `restore_<nanoTime>` → `MMKV.restoreAllFromDirectory` → `makeSetupGroupTab`+`makeRestartService` → finally deleteRecursively) → `BackupActivity.RestoreSuccess → SettingsManager.initApp` (Run 12 region). Backup side: `backupConfigurationToCache` (`MMKV.backupAllToDirectory` → `ZipUtil.zipFromFolder`), share/export/WebDAV callers. Supporting files read in full: `util/ZipUtil.kt` (284), `ui/base/BaseViewModel.kt` (128), `handler/SettingsChangeManager.kt` (58), `BackupActivity.kt` (restore/webdav entries), `MmkvManager.decodeWebDavConfig/encodeWebDavConfig` (fresh MMKV decode, no cache), `dto/entities/WebDavConfig.kt`, `Utils.isIpAddress`/`isIpInCidr`/`ipv4ToLong` + `AppConfig.PRIVATE_IP_LIST` (127/8, 10/8, 172.16/12, 192.168/16, 0/8, 169.254/16, 224/4).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **MEDIUM (bug, upstream-shared — FIXED)** | BackupViewModel 27, 94-99, 134-139 vs restore flow | **Stale `_webDavConfig` StateFlow after restore → wrong WebDAV server.** The flow is loaded once at VM creation and updated only by `saveWebDavConfig` (dialog). A restore that overwrites MMKV with a different WebDAV config does NOT refresh it: for the rest of the session `backupViaWebDav`/`restoreViaWebDav` (and the dialog's `initialConfig`) use the PRE-RESTORE server/credentials — restoring backup-with-config-B then tapping backup uploads the full profile pool to old server A. Reachable in one screen session (restore → same screen → backup). | **FIXED** — both WebDAV entry points now `MmkvManager.decodeWebDavConfig()` fresh + re-sync the StateFlow; `performRestore` refreshes the flow once `restoreAllFromDirectory` returned success. v1.2.31. |
| 2 | **MEDIUM (leak/hygiene, upstream-shared — FIXED)** | BackupViewModel.backupConfigurationToCache 176-186 | **Plaintext MMKV copy left behind by EVERY backup.** `MMKV.backupAllToDirectory(backupDir)` materializes all MMKV files (WebDAV password, sub URLs with tokens, the 9k pool) into `cacheDir/<app>_<timestamp>/`; after `zipFromFolder` only the zip is consumed but the folder is never deleted — unbounded accumulation of plaintext credential copies in cache (9k-pool MMKV is tens of MB per backup). A failed zip also leaves a partial zip file. | **FIXED** — folder `deleteRecursively()` right after zip (both success/failure branches); partial zip deleted when zipping fails. Zip-first consumers verified: all 3 callers use only `ret.second` (the zip path). v1.2.31. |
| 3 | CLEAN (restore-path proof) | ZipUtil.kt (upstream-identical, hardened) | The extractor between a downloaded zip and `restoreAllFromDirectory` is a hardened one: zip-slip guard (`File(root, entry.name).canonicalFile` + prefix check → "resolves outside the destination directory"), archive/entry/total size limits (128 MiB/128 MiB/256 MiB), 32-entry cap, 1000× compression-ratio bomb guard, declared-vs-actual size verification (`copyEntry` throws on mismatch — a truncated WebDAV download cannot pass), duplicate-destination and entry-below-a-file checks, unsupported-method rejection, empty-destination requirement, and rollback (deleteRecursively) on mid-extract failure. Corrupt/truncated/hostile zips are rejected BEFORE MMKV is touched. | No action. |
| 4 | CLEAN | WebDavManager fork hardening (from c8102ac) | `isSecureRemoteUrl` rejects plain-http remote before Basic-auth is ever sent (https passes; http only for localhost/private LAN via `isIpAddress`+`isIpInCidr`, fail-closed for everything else incl. invalid URL); `redactUrlCredentials` masks passwords in every log line incl. MKCOL. `isIpInCidr` math verified (32-bit operands vs wider mask harmless; prefix clamped 0..32). Plain-http to IPv6-literal loopback is rejected (fail-closed, matches KDoc). Upstream has neither guard (sends creds over cleartext http) — fork is strictly safer. | No action (positive divergence). |
| 5 | CLEAN | WebDavManager core ops | Redirect/auth: no redirect following needed (OkHttp default on GET/PUT is fine for DAV here, upstream-identical); `applyAuth` sends `Credentials.basic` only when username non-empty; response/stream `.use` closes everything (no leak); `ensureRemoteDirs` MKCOL best-effort with per-segment catch (405/409 tolerated); `client ?: return false` guards uninitialized use; singleton `cfg`/`client` swap race is theoretical-only (UI-sequential: init on main thread immediately before each op). | No action (parity). |
| 6 | CLEAN (containment proof) | BaseViewModel.launchLoading + performRestore | `launchLoading` has NO catch (finally only) → any throw would crash via unhandled coroutine exception. Verified every restore-path op is internally contained: `ZipUtil.unzipToFolder` catches ALL exceptions → false; MMKV backup/restore are JNI calls returning codes (no throw); `SettingsChangeManager` = AtomicBoolean.set; `deleteRecursively()` is non-throwing. WebDAV ops additionally wrap everything in try/catch → toast. Local `restoreConfiguration` zip comes from SAF copy (BackupActivity). No unguarded throw on the path. | No action (upstream-identical). |
| 7 | Info | Restore trust model | `MMKV.restoreAllFromDirectory` overwrites ALL MMKV values (the "URL-injector" write-path) — but the source is the user's own backup (their WebDAV server or locally picked file); fork's https-enforcement actually shrinks the MITM surface vs upstream. Post-restore consistency: `initApp` (Run 12 migrations are one-shot idempotent and re-guard by byte-compare), `makeRestartService` flag consumed by the service layer, dynamic socks port regenerated at service start (Run 12 lifecycle) — no stale in-memory state found. | Noted (closed the Run 13 #2 theoretical flag). |
| 8 | Info | zipFromFolder | Top-level files only (no recursion) — fine because `backupAllToDirectory` output is flat; `putNextEntry` implicitly closes the previous entry (verified JDK behavior); zip retained after share intentionally (re-share allowed). | Noted. |

### Upstream comparison result
- `BackupViewModel.kt`, `ZipUtil.kt`, `BaseViewModel.kt`, `SettingsChangeManager.kt`: **zero diff hunks** (upstream itself carries the hardened ZipUtil extractor). `WebDavManager.kt`: fork = upstream + 58 lines (isSecureRemoteUrl + redactUrlCredentials + doc comments), from audit commit c8102ac — no missing upstream fixes. Both Run 15 bugs are upstream-shared (not fork regressions); fixed anyway per real-finding rule, with minimal divergence.

### Release
- **v1.2.31 (versionCode 35)** — commit 412a322. Dispatch HTTP 204; runs 41832209535 (push) + 41832244543 (dispatch, releases) on 412a322 — BOTH completed success. Release v1.2.31 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 16)
- **`util/Utils.kt` (498 lines) — second pass** (`getIpv6Address`, `isPureIpAddress`, `isCoreDNSAddress`, `fixIllegalUrl`, remaining decode/parse helpers feeding fmt parsers + DNS handling). Then Run 17 = `handler/AdsManager.kt` (377, house-ads provider — fork ad surfaces) — pick per risk.

---

## Run 16 — 2026-11-04

### Files reviewed
- `util/Utils.kt` (498 lines) — **second pass, full line-by-line**: regexes (IPV4_REGEX/IPV6_REGEX), parseInt, clipboard get/set, decode/tryDecodeBase64/encode, isIpAddress/isPureIpAddress/isIpv4Address/isIpv6Address/isDomainName, isCoreDNSAddress, isValidUrl, openUri, getUuid, decodeURIComponent/encodeURIComponent, readTextFromAssets/userAssetPath, getDeviceIdForXUDPBaseKey, getIpv6Address, getSysLocale, fixIllegalUrl, findRandomFreePort, isValidSubUrl, receiverFlags, isXray, isIpInCidr/ipv4ToLong, formatTimestamp.
- Consumer tracing: `getDeviceIdForXUDPBaseKey` → CoreNativeManager:33 (`Libv2ray.initCoreEnv(assetPath, deviceId)`); `isXray()` → SettingsManager:391 (getHttpPort) + CoreConfigManager:561 (HTTP inbound clone); `findRandomFreePort` → CoreServiceManager:152 (browser dialer); `getHttpPort()` → 6 consumers (CoreVpnService:251, AngConfigManager:559, PerAppProxyViewModel:182, UpdateCheckerManager:34, UserAssetActivity:218, SpeedtestManager:62) → `HttpUtil.buildOkHttpClient:242` = `Proxy.Type.HTTP` @ LOOPBACK:httpPort (HttpUtil re-diffed: byte-identical to upstream).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **CLEAN (critical negative-result proof — DO NOT "fix")** | Utils:460 `isXray()` + CoreConfigManager:561 + SettingsManager:390-392 | `isXray()` returns **FALSE in the fork** (`com.derafsh.vpn` doesn't start with `com.v2ray.ang`; upstream = always TRUE incl. `.fdroid` suffix). The gate therefore flips every branch — and the flipped fork side is **the correct, self-consistent one**: `!isXray()` builds the local HTTP inbound (clone of socks inbound, LOOPBACK listen (CCM:532), auth/udp stripped, port = socks+1) and `getHttpPort()`=socks+1; all 6 consumers fetch via **HTTP CONNECT proxy** at exactly that port (IP-info, update check, geo/asset downloads, per-app proxy view, speedtest, system `setHttpProxy(LOOPBACK, socks+1)` on PREF_APPEND_HTTP_PROXY). Upstream HEAD is the inconsistent side: isXray()=true → NO HTTP inbound built, getHttpPort()=socksPort → its identical consumers point HTTP CONNECT at a SOCKS listener (vestigial multi-core gate). **Changing isXray() to return true for the fork would silently break all 6 features.** | No action — documented as a guard-rail for future runs. |
| 2 | Info (upstream-native quirk) | Utils:362-370 getDeviceIdForXUDPBaseKey | Uses `Settings.Secure.ANDROID_ID` — the settings **KEY constant** ("android_id"), not the per-device value (needs `Settings.Secure.getString(resolver, ANDROID_ID)`). Result: base64("android_id"-padded) — identical for every install; feeds XUDP base key in `initCoreEnv`. Not a secret/security boundary; XUDP session keys derive the same across installs (upstream design). Upstream-identical (whole-file diff = only the receiverFlags hunk). | Noted (parity). |
| 3 | Info (upstream-native quirk) | Utils:31 IPV6_REGEX | Anchoring bug in the alternation: `^A\|B$` — alt-1 unanchored at end, alt-2 unanchored at start → strings with garbage around a valid IPv6 core (e.g. "x1:2:3:4:5:6:7:8", "1:2::3zzz") pass isIpv6Address/isPureIpAddress; over-long hextet counts also accepted. Contained: worst case a malformed "address" profile fails to dial (pool self-heal sweep), getIpv6Address wraps garbage → core rejects, routing edge cases degrade to profile-level failure caught by the top-level config catch (Run 1 #2). IPV4_REGEX accepts leading-zero octets ("010") — ipv4ToLong parses them fine. | Noted (parity). |
| 4 | Edge (upstream-native) | Utils:412-414 findRandomFreePort | TOCTOU: ServerSocket(0) binds→closes, core rebinds later (CoreServiceManager:152 browser dialer address) — port can be stolen in the window. Local-only, browser-dialer-only, best-effort, failure contained at core start. | Noted (parity). |
| 5 | CLEAN | decode/tryDecodeBase64/encode 82-126 | Standard→URL_SAFE→pad-stripped retry chain, never throws ("" on total failure); SS-legacy `replace("ss://","")` safety already proven (Run 10). encode NO_WRAP + optional padding trim. | No action. |
| 6 | CLEAN | decodeURIComponent/encodeURIComponent 289-316 | '+'-strictness (literal '+', not space) is upstream-native now — deliberate + documented in-code; encode maps space→%20. Roundtrip with `URI.getUserInfo()` (Run 10) unaffected. | No action. |
| 7 | CLEAN | receiverFlags:453 (fork hardening, c8102ac) | Always RECEIVER_NOT_EXPORTED vs upstream RECEIVER_EXPORTED on T+ — blocks third-party spoofing of internal broadcasts. Positive divergence. | No action. |
| 8 | CLEAN (misc) | isCoreDNSAddress 228-233, isDomainName 189-194, getClipboard 50-58, fixIllegalUrl 401-404, formatTimestamp 488-497 | isCoreDNSAddress loose prefixes ("httpsfoo") — upstream-identical, inputs are user DNS settings, core-side validation + top-level catch contain. isDomainName = !IP && isValidUrl (scheme-less accepted — Run 13 context). getClipboard null-text NPE caught → "". fixIllegalUrl minimal (space/pipe). formatTimestamp null/non-positive → "". | No action. |

### Upstream comparison result
- `Utils.kt`: upstream + **1 hunk** (receiverFlags hardening, c8102ac). Region re-checks: `HttpUtil.kt` byte-identical (proxy-type proof), SpeedtestManager getRemoteIPInfo region + CoreVpnService configurePlatformFeatures byte-identical. No fork regressions, no missing upstream fixes.

### Next run starting point (Run 17)
- **`handler/AdsManager.kt` (377 lines)** — house-ads provider (fork ad surfaces: provider switch "house"/"admob", placement logic) + its UI consumers. Then Run 18 = remaining `handler/` files or `service/` layer (V2RayServiceManager/VpnService lifecycle) — pick per risk.

### Release
- None this run (no code changes; docs-only commit). v1.2.31 remains latest.

---

## Run 17 — 2026-11-04

### Files reviewed
- `handler/AdsManager.kt` (377 lines, fork-only) — full line-by-line: BuildConfig unit seams, house-mode gate (`ADS_PROVIDER="house"` → Mobile Ads SDK never touched), `init` (main-process guard via `isMainProcess` API-28+ / ActivityThread reflection fallback for minSdk 24), the Tapsell→AdMob→house waterfall for app-open/interstitial/rewarded (frequency cap 180s BEFORE the Tapsell leg, null-out-before-show double-show guard, catch→preload+onClosed everywhere, `buildShowCallback` routes failed-to-show → onDismiss too), preloads (in-flight markers), `onHouseAdDismissed` (reward-then-close order mirroring AdMob, nulls both slots → double-fire safe).
- Supporting files read in full: `handler/TapsellAds.kt` (47 — SDK seam, `isConfigured` = `TAPSELL_APP_KEY.isNotBlank()` → currently blank, leg always falls through), `ui/HouseAdActivity.kt` (35→50), `ui/compose/HouseAds.kt` (DerafshHouseInterstitial: Continue button + LaunchedEffect auto-close timer — "must never trap the user"), MainActivity ad entry points (:148 app-open once/session after splash, :160 interstitial on running-transition with EMPTY onClosed, :299-303 `handleExtendSession` rewarded = {startV2Ray} / closed = {DismissSessionTimeUp}), SessionTimeUpDialog own onClose exit (MainScreen:341), build.gradle ad BuildConfig fields (TAPSELL_APP_KEY="" — why the waterfall always lands on house today).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **MEDIUM (real bug — FIXED, released v1.2.32)** | HouseAdActivity.kt (pre-fix) + AdsManager:58-84 | **Back-press leak → stale house-ad callbacks → delayed auto-connect.** The house ad was dismissed through the Continue button or the auto-close timer ONLY; the system back press finished the activity WITHOUT `onHouseAdDismissed()` → pending `housePendingReward`/`housePendingClosed` survived as stale state. Concrete reachable chain: session-time-up → "extend with ad" → house ad → **back press** (reward `startV2Ray` + `DismissSessionTimeUp` stay pending) → dialog dismissed manually → next cold start: splash app-open ad → house ad → close → `onHouseAdDismissed` fires the STALE reward → **VPN auto-connects with no user intent**. Interstitial's empty onClosed made the common path look harmless. | **FIXED** — `HouseAdActivity.onDestroy` resolves pending callbacks when `isFinishing` (back/finish = the documented house policy "closing the screen always resolves"); rotation/recreation (not finishing) keeps the callbacks with the replacement screen; normal close path double-fires into nulls (no-op). Commit 0c7d020, v1.2.32 (36). |
| 2 | CLEAN | AdsManager waterfall | Cap ordering: checked before Tapsell, set on Tapsell success + house + AdMob before-show (an AdMob show-failure still burns the 3-min budget — minor business edge, note only). Single-flight comment vs code: overlapping show* overwrites pending slots — contained because HouseAdActivity covers the screen (no underlying taps) and double-fire nulls are no-ops; double-launch (rapid double-tap) stacks two house activities, each resolves into nulls. `isMainProcess` reflection fallback documented + correct (`processName == APPLICATION_ID`). init: house mode never touches MobileAds; `initialized` + catch on initialize. | No action. |
| 3 | CLEAN (contract) | TapsellAds.kt | Pure seam: unconfigured key → all three legs return false without touching anything; no SDK dependency added yet. Waterfall "unconfigured leg never blocks" verified end-to-end. | No action. |
| 4 | CLEAN | HouseAds.kt composable | Never traps: Continue button + auto-dismiss timer (LaunchedEffect). Timer firing after a manual close → second onHouseAdDismissed → no-op. | No action. |
| 5 | Info | MainActivity:148-161 | App-open shown once per session after the branded splash; interstitial fires on the running-transition only (never boot/auto-start) — frequency cap makes repeats polite. onClosed={empty} is safe now that pending callbacks can't leak. | Noted. |

### Upstream comparison result
- Fork-only subsystem (upstream 2dust/v2rayNG has no AdsManager/TapsellAds/HouseAdActivity) — no upstream baseline; correctness argued from the app's own documented contracts instead. No core/VPN-path code touched by this subsystem.

### Release
- **v1.2.32 (versionCode 36)** — commit 0c7d020. Dispatch HTTP 204; push + dispatch runs BOTH completed success. Release v1.2.32 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 18)
- **`service/` layer: `V2RayServiceManager.kt` + `CoreVpnService` lifecycle region** (start/stop/ping loop, notification, consumeRestartService/setupGroupTab consumers, TUN bring-up) — the Run 15/16 findings touched its edges (restore restart flag, setHttpProxy) but the file itself is unreviewed. Then Run 19 = remaining `handler/` files (UpdateCheckerManager, AppLocaleManager, ...) or `ui/compose` ad/banner surfaces — pick per risk.

---

## Run 18 — 2026-11-04

### Files reviewed
- **`service/CoreVpnService.kt` (372→385 lines)** — full line-by-line: onCreate (StrictMode permitAll, SoftReference serviceControl), onRevoke, onDestroy (fork-less: upstream now carries the isRunning-guarded interface close), onStartCommand (ensureForeground, system-VPN force-unlock for stuck-lock recovery, START_STICKY/NOT_STICKY split), setupVpnService → configureVpnService (old-interface close, establish()!! NPE contained by catch), configureNetworkSettings (MTU/addresses/ROUTED_IP_LIST bypass-LAN routes, IPv6 fc00::/18 FakeDNS pool, pure-IP DNS filter), configurePlatformFeatures (metered=false, PREF_APPEND_HTTP_PROXY → setHttpProxy(LOOPBACK, getHttpPort()) — the Run 16 HTTP-inbound consumer #1), configurePerAppProxy (self-package handling per mode, fresh decoded set per call), runTun2socks (hev-tun selection + restartCallback plumbing), stopAllService, tryLockStart/unlockStart.
- **`service/TProxyService.kt` (120 lines)** — hev-socks5-tunnel JNI seam: config build (YAML quoting of socks credentials, RW timeout parsing, never-logged config), start/stop JNI try/catch.
- **`service/NetworkMonitor.kt` (119 lines)** — handover detection (requestNetwork NOT default-callback to skip the VPN's own network), onAvailable/onCapabilitiesChanged/onLost → setUnderlyingNetworks + 1s-debounced handover reload, register/unregister idempotence.
- Sibling services re-checked for the same race class: `CoreProxyOnlyService.kt` (no lock, isRunning guard, stopService=stopSelf — not susceptible), `CoreRootService.kt` (setupJob cancelAndJoin already bounded 5s from c8102ac).
- Stop-path threading traced: MSG_STATE_STOP → receiver onReceive (main, serialized); **MSG_STATE_RESTART → goAsync coroutine on Dispatchers.Default** (`CoreServiceManager:559-563`); **session expiry (expireSession) → sessionTimerScope on Dispatchers.Default** (`CoreServiceManager:283`) — both call `serviceControl.stopService()` OFF the main thread.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **MEDIUM (real race, upstream-shared — FIXED, released v1.2.33)** | CoreVpnService.stopAllService (pre-fix 320-361) + CoreServiceManager:559-563/:283 | **Teardown closed the mInterface FIELD after releasing the start lock → concurrent start's fd destroyed.** stopAllService() called unlockStart() at its TOP, then ended with `mInterface.close()` after Thread.sleep(100). Restart/expiry teardown runs on Dispatchers.Default, so a start delivered on the main thread inside the ~100-300ms window (user tap, QS tile, LauncherManager boot start) acquired the freed lock, re-established the VPN interface and RE-ASSIGNED the field; the teardown then closed the NEW session's fd → connected-but-dead VPN. The restart flow's follow-up start (delay 500ms → LauncherManager.startService) was then silently dropped by tryLockStart (held by the new start) → no self-heal until manual disconnect. Same class of stomp on `tun2SocksService` (field nulled after a concurrent start re-assigned it). | **FIXED** — capture `ifaceToClose`/`tproxyToStop` before anything can start; close/stop the captured refs; clear fields only if still pointing at the captured instances; `unlockStart()` moved to the END of teardown (a start during teardown is now rejected and simply retried by the user; the restart flow's 500ms delay > teardown duration so its own start proceeds). System-VPN force-unlock in onStartCommand kept intact (stuck-lock recovery). Commit 26136f4, v1.2.33 (37). |
| 2 | CLEAN (containment) | configureVpnService:183 `establish()!!` | NPE if permission revoked between prepare() and establish() is caught by the surrounding catch(Exception) → stopAllService → return false → onStartCommand stopSelf + START_NOT_STICKY (no restart loop). | No action (upstream parity). |
| 3 | CLEAN | onStartCommand system-VPN force-unlock (77-98) | `intent==null \|\| action==SERVICE_INTERFACE` → unconditional unlock covers: START_STICKY redelivery after process death (fresh instance, fresh lock — unlock harmless), always-on rebind. Residual: force-unlock can release an in-flight teardown's lock → the captured-ref fix (#1) makes that interleave harmless (new start closes the old fd itself; teardown closes its captured already-closed fd). Wedged-session recovery via system redelivery REQUIRES the unconditional unlock — not touched. | No action (guard-rail). |
| 4 | Info (upstream-native) | TProxyService:18-19 | `isRunningProvider`/`restartCallback` constructor params are dead plumbing (never invoked anywhere, upstream-identical) — the hev tunnel has no auto-restart despite the seam suggesting it. Native tunnel is a singleton: TProxyStopService stops THE tunnel regardless of instance, so stop/start ordering at the native boundary remains inherently racy in rapid stop→start (Run 2 #3 parity). | Noted. |
| 5 | Info (upstream-native) | CoreVpnService:33 `isRunning` non-volatile | Written on main (setup) and teardown threads (restart/expiry), read in onDestroy — worst case a stale read makes onDestroy's guard skip a close (harmless; fd also closed by stopAllService). Upstream-identical. | Noted. |
| 6 | CLEAN (positive divergence) | TProxyService:54-59 | Fork removed upstream's config-content log line that printed the socks5 username/password to logcat on every tunnel start; logs path + line count instead. | No action (keep). |
| 7 | CLEAN | NetworkMonitor (all) | Callback fields touched only on the connectivity handler thread; stray post-stop handover contained by reloadCore's isRunning/getService/isReloading guards; unregister cancels the debounce job; ad-hoc IO scope per handover is GC-able after completion. onLost without a new network → networks=null only (no reload) — upstream design. | No action. |

### Upstream comparison result
- `CoreVpnService.kt`: **byte-identical to upstream HEAD pre-fix** (upstream renamed V2RayServiceManager→CoreVpnService; the onDestroy close-guard, ensureForeground, sleep(100) are all upstream code now) — the Run 18 race is upstream-shared. `NetworkMonitor.kt`, `CoreProxyOnlyService.kt`: zero hunks. `TProxyService.kt`: fork = upstream + 1 hunk (log-leak removal, c8102ac). `CoreRootService.kt`: fork + bounded-join (c8102ac). No missing upstream fixes in the service layer.

### Release
- **v1.2.33 (versionCode 37)** — commit 26136f4. Dispatch HTTP 204; push run 41841360348 + dispatch run 41841371621 BOTH completed success. Release v1.2.33 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 19)
- **`handler/` remaining files by risk: `UpdateCheckerManager.kt` + `UpdateManager.kt`** (update flow: signature checks, download/install intents — security-adjacent) or `handler/AngConfigManager.kt` deep second pass (Run 8 covered fmt parsers; the 1000+ line import/parse hub itself). Then Run 20 = `service/CoreTestService.kt`/`RealPingWorkerService.kt`/`SubscriptionUpdateService.kt` (background work: coroutine scopes, worker contracts) — pick per risk.

---

## Run 19 — 2026-11-04

### Files reviewed
- **`handler/UpdateCheckerManager.kt` (132 lines)** — full line-by-line: GitHub releases check (direct-first, then local-HTTP-proxy fallback with socks creds), pre-release vs latest endpoints, tolerant version compare, fdroid-vs-regular asset picker.
- **`handler/UpdateManager.kt` (303→317 lines, fork-only in-app updater)** — full line-by-line: silent check (F-Droid early-return, Downloading/Ready skip, internal catch), state machine (Idle/Available/Downloading/Ready/Failed), downloadUpdate (cache dir wipe, progress, signature gate), installApk (install-time re-verification + FileProvider), parseLatestRelease (draft skip, `-build.N` strip, ABI→universal→first asset ladder), verifyApkSignature (API 28 signingInfo / legacy signatures, SHA-256 vs constant, fail-closed), isNewerVersion.
- Supporting: `ui/checkupdate/CheckUpdateViewModel.kt` (try/catch → toast — the IllegalStateException paths are contained), `AppConfig.RELEASE_CERT_SHA256` (= `2642E7F0…FD79F531`, the official fingerprint), `AppConfig.DERAFSH_UPDATE_API_URL` (`?per_page=1`), build.gradle flavors (`fdroid` flavor HAS `applicationIdSuffix = ".fdroid"` → the contains("fdroid") asset pick is correct), AndroidManifest FileProvider (`${applicationId}.cache` matches the code string) + `res/xml/cache_paths.xml` (`cache-path path="/"` covers cacheDir/update).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **LOW-MEDIUM (TOCTOU race, fork-only — FIXED, released v1.2.34)** | UpdateManager.downloadUpdate (pre-fix 115-121) | **Rapid double-tap started two concurrent downloads on the same file.** The guard `if (_state.value is Downloading) return` ran on the caller thread, but `Downloading(0)` was set asynchronously INSIDE `scope.launch` (IO scheduler) — two invocations in the scheduling window both passed the guard. Both downloads then raced on the same `dest` path, and each run's `dir.listFiles()?.forEach { it.delete() }` deleted the other's partially-written file mid-stream → interleaved/corrupt APK (caught later by the signature gate → Failed, so no security impact, but the update flow breaks exactly when the user is most impatient). | **FIXED** — check-and-set `Downloading(0)` atomically on the caller thread (all callers are UI main-thread callbacks) before launching; duplicate set removed from the coroutine. Commit 6067b81. |
| 2 | **LOW (missing size cap, fork-only — FIXED, released v1.2.34)** | UpdateManager download loop (pre-fix 136-157) | **Unbounded download into cacheDir.** The class comment declares channel-compromise as the threat model for the signature gate ("even if the release channel itself were ever compromised") — but the DOWNLOAD phase itself had no limit: a compromised/redirected `browser_download_url` could stream unbounded bytes into cache (disk exhaustion). ZipUtil enforces 128 MiB/256 MiB budgets for the same scenario class; the updater had none. | **FIXED** — `sizeLimit = minOf(total, 256 MiB)` when the declared size is known (GitHub asset metadata), else the 256 MiB hard cap; the write loop aborts with IllegalStateException once exceeded → outer catch → Failed (same UX as other failures). Mirrors ZipUtil's declared-vs-actual contract. Commit 6067b81. |
| 3 | CLEAN | verifyApkSignature + installApk double gate | Download-time gate + install-time re-verification; `signatureBytes ?: return false` and catch→false = fail-closed on null/corrupt archives (truncated download → getPackageArchiveInfo null → rejected); constant matches the official certificate; FileProvider authority + cache-path verified. A partial file left by a failed download is wiped by the next attempt's dir cleanup (pre-existing semantics kept). | No action. |
| 4 | CLEAN | UpdateCheckerManager (upstream + 1 hunk) | Fork hardening: compareVersions is null/blank/non-numeric-tolerant (upstream's `split("."){toInt()}` throws NumberFormatException on a malformed tag → CheckUpdateViewModel catch → toast, so upstream fails loudly where the fork degrades gracefully). fdroid asset pick correct (applicationIdSuffix ".fdroid"). Proxy fallback only when the direct attempt returns empty — a non-empty block-page body skips the fallback and parses to null → silent hasUpdate=false; self-heals on the next check (upstream-native design, noted). `num1 - num2` cannot overflow (both operands non-negative, ≤ Int.MAX). | No action. |
| 5 | Info | parseLatestRelease asset ladder | ABI match → universal → `assets.first()` fallback: the last resort could pick a wrong-ABI APK (install then rejected by the OS with NO_MATCHING_ABIS); unreachable while the release names follow `Derafsh_<ver>[_-fdroid]_<abi>.apk` (SUPPORTED_ABIS[0] strings match asset names). `filter { c -> c.isDigit() }` in isNewerVersion merges digits across non-digits ("2x3"→"23") — only reachable with self-inflicted malformed tags. | Noted. |

### Upstream comparison result
- `UpdateCheckerManager.kt`: fork = upstream + 1 hunk (version-compare hardening). `UpdateManager.kt`: **fork-only** (upstream has no in-app updater) — correctness argued from the app's own stated threat model + ZipUtil precedent. No missing upstream fixes.

### Release
- **v1.2.34 (versionCode 38)** — commit 6067b81. Dispatch HTTP 204; push run 41843998474 + dispatch run 41844008121 BOTH completed success. Release v1.2.34 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 20)
- **`handler/AngConfigManager.kt` deep second pass** (the import/parse hub — Run 8 covered the fmt/* parsers it delegates to; the hub itself: `importBatchConfig`, share/link dispatch, subscription fetch/merge logic) or `service/CoreTestService.kt` + `RealPingWorkerService.kt` + `SubscriptionUpdateService.kt` (background work: coroutine scopes, worker lifecycle) — pick per risk.

---

## Run 20 — 2026-11-04

### Files reviewed
- **`handler/AngConfigManager.kt` (958 lines, deep second pass — Run 5 covered lines 1-465; this run covered the hub end-to-end)**: share/clipboard/QR paths (shareConfig type dispatch, CUSTOM → raw fallback), `importBatchConfig` (base64 → raw → custom cascade; ProfileStorageException propagation), `parseBatchSubscription`/`parseBatchProfiles` (distinct → reversed → v2rayn-lines split → `v2raynConfigs + configs` composition), `commitProfiles`, `parseCustomConfigServer` (JSON-array → single → WireGuard-conf dispatch, group-preserving), `parseConfig` (scheme-prefix parser map incl. socks4/5 + hy2 aliases, subscription filter regex), `updateConfigViaSubAll`/`updateConfigViaSub` (chunk-prefix skip, per-sub try/catch = Run 13 fix), `fetchSubscriptionContent` (proxy→direct, usage-header capture), **`updateConfigViaSubChunked`** (the whole retire/commit/re-anchor/chunk pipeline), `snapshotFreshTestResults`/`configIdentityKey`, `removeInvalidServer`/`sweepUnhealthyPoolConfigs`, `sortByTestResultsForSub`, `importUrlAsSubscription`, `generateDescription`.
- Contract cross-checks against `MmkvManager`: `decodeServerList`/`saveServerProfiles` append semantics (append=true = keep+add / append=false = rebuild keeping only group-type configs + payload cleanup via findRemovablePayloads), `removeServerProfiles` (removes guids from ALL indexes + selection + payloads), `removeSubscription` → `removeServerViaSubid` (chunk groups fully cleaned → fresh on re-create), `encodeSubscription("")` → fresh uuid, `getSubscriptionId("")` → DEFAULT, `ProfileReplacement.findSelectedReplacement` (null selection → first-profile fallback), upstream `updateConfigViaSub` = `parseConfigViaSub(configText, guid, false)` (replace).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **HIGH (inverted switch — FIXED, released v1.2.35)** | AngConfigManager.updateConfigViaSubChunked:736 (`append = !isBuiltinPool`, shipped since v1.2.28 / aa70c86) | **The v1.2.28 pool-refresh fix inverted its own stated contract.** The aa70c86 commit message: "User subscriptions keep the upstream replace semantics" — but `!isBuiltinPool` gave user subs append=**true**: nothing retires their old configs (previousGeneration=∅ for non-builtin, retire gated `if (isBuiltinPool)`), so every refresh KEPT the stale set and appended a full new one → unbounded duplicate growth, stale servers never removed (upstream: `parseConfigViaSub(..., false)` = replace). Meanwhile the built-in pool got append=**false**: after the explicit generation retire, `saveServerProfiles(append=false)` STILL wholesale-rebuilds the shared default index (keeps only group-type configs) → manually imported configs are dropped from the index and findRemovablePayloads deletes their payloads (unless policy-group-referenced/protected) — **the exact data-loss bug aa70c86's title claims to fix**, contradicting its own inline comment ("never rebuilt wholesale"). Selection side-effect confirmed: a selected manual import is deleted and findSelectedReplacement's first-profile fallback steals the selection onto a random pool config. | **FIXED** — one-word inversion `append = isBuiltinPool`: builtin = explicit-retire-then-**append** (shared index only ever mutated; saveServerProfiles replacedServers=∅ → early return, no payload cleanup beyond the retire), user subs = **replace** (upstream semantics; fork's safer re-anchor + group preservation apply). Chunk groups >0 unaffected (fully removed + re-created fresh each refresh). Fresh-carry of test results and both re-anchor layers verified coherent under the corrected polarity. Commit 3b0e46f, v1.2.35 (39). |
| 2 | CLEAN | parseBatchProfiles order composition | `distinct().reversed()` + per-guid `add(0, …)` insertion in saveServerProfiles reproduces the payload's original order; `v2raynConfigs + configs` matches upstream composition (v2rayN block ends up on top). `lines()` vs upstream `split("\n")` — equivalent for \n and more tolerant of \r\n trailing empty segments. | No action. |
| 3 | CLEAN | parseConfig containment | Per-line try/catch (Run 1 layering); invalid subscription filter regex throws PatternSyntaxException → caught → that line skipped (bad filter = all lines skipped → refresh reports failureCount — visible, contained, upstream-shared design). Scheme map: socks4/socks5 prefixes don't collide with socks:// ("socks4://".startsWith("socks://") is false); hy2/hysteria2 distinct. | No action. |
| 4 | Info (upstream parity) | importUrlAsSubscription:929 | `URI(Utils.fixIllegalUrl(url))` can throw URISyntaxException on exotic input — caught by parseBatchSubscription's whole-batch catch → remaining lines of the same batch abort (one bad URL kills the rest). Upstream shares the shape; low impact (batch import of subscription URLs from clipboard). | Noted. |
| 5 | Info | generateDescription | Single-label hosts produce an empty addr part (" : 443"); IPv4 → "1.2.3.***", IPv6 → "1:2:***", domain → "example.***". Cosmetic only (privacy-motivated masking works). | Noted. |
| 6 | CLEAN | fetchSubscriptionContent / usageInfo | Header capture callback reassigned per attempt; second (direct) attempt only fires on empty body → last successful headers win; usageInfo replaced on every success so a panel that stops sending headers clears the box (comment-verified). | No action. |

### Upstream comparison result
- Upstream `AngConfigManager.kt` (657 lines) fetched: updateConfigViaSub is the non-chunked ancestor of the fork's chunked version — commit call `parseConfigViaSub(configText, it.guid, false)` proves the replace semantics the fork's inverted flag contradicted. Fork-only additions (chunking, pool generation, test-result carry, allowInsecureUrl, per-sub isolation) argued from their own documented contracts. The bug is **fork-introduced in v1.2.28** (not upstream-shared).

### Release
- **v1.2.35 (versionCode 39)** — commit 3b0e46f. Dispatch HTTP 204; CI/APK verification recorded after completion below.

### Next run starting point (Run 21)
- **`service/CoreTestService.kt` (260) + `RealPingWorkerService.kt` (159) + `SubscriptionUpdateService.kt` (235)** — background work trio: coroutine scopes, worker lifecycle, foreground-service contracts. Then Run 22 = remaining `handler/` files (`AppLocaleManager`, `OnboardingManager`, `MaintenanceStatus`, `SpeedtestManager` second pass) or `ui/` ad surfaces — pick per risk.
