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
- **v1.2.35 (versionCode 39)** — commit 3b0e46f. Dispatch HTTP 204; push run 41847267685 + dispatch run 41847277832 BOTH completed success. Release v1.2.35 published with 10 APKs; fdroid universal APK signature verified = `2642E7F0…FD79F531` ✅.

### Next run starting point (Run 21)
- **`service/CoreTestService.kt` (260) + `RealPingWorkerService.kt` (159) + `SubscriptionUpdateService.kt` (235)** — background work trio: coroutine scopes, worker lifecycle, foreground-service contracts. Then Run 22 = remaining `handler/` files (`AppLocaleManager`, `OnboardingManager`, `MaintenanceStatus`, `SpeedtestManager` second pass) or `ui/` ad surfaces — pick per risk.

---

## Run 21 — 2026-11-04

### Files reviewed
- **`service/CoreTestService.kt` (260 lines)** — foreground test orchestrator: startForeground/cancel-action PendingIntent, START command dispatch (null intent → stopSelf(startId) + NOT_STICKY), handleMeasureStart (guids resolution: explicit list → per-sub list → all-server list; late-init worker + containsKey event guard; settled-sums reset only when no worker active), handleWorkerEvent (Progress → per-worker map + session-wide publish + notification + msg2UI; Result → encodeServerTestDelayMillis + msg2UI; Finish → settle batch into AtomicIntegers → auto-remove/sort per settings → finish msg → onWorkerDone → last-worker finish/stopForeground/stopSelf), handleMeasureCancel/cancelWorkers (remove(key,value) atomic per entry, cancel reply per requestId, progress entry dropped), onDestroy (cancel + abandon-if-active + clear + stopForeground).
- **`service/RealPingWorkerService.kt` (159 lines)** — per-batch worker: own fixed-thread-pool dispatcher sized concurrency (×2 for tcping), SupervisorJob scope, per-guid coroutines with running/total AtomicIntegers, TCP pre-check for simple non-h3 protocols, `getV2rayConfig4Speedtest` → `measureOutboundDelay` under the CUSTOM-config global mutex (RealPingExecutionLimiter — serializes full-config JNI probes whose parallel teardown aborts the native probe), Result/Progress gated on scope.isActive (cancelled batches emit nothing), finisher coroutine joinAll → Finish → finally dispatcher.close().
- **`service/SubscriptionUpdateService.kt` (235 lines)** — ":tasks"-process updater: startForeground per start, Semaphore(2) concurrent update tasks, runningTasks refcount + activeWorkers-empty gate for finish/stopSelf, updateSingle (enabled/url guards, forced/periodic update via AngConfigManager.updateConfigViaSub = the Run 20-fixed chunked path, auto test/remove/sort per settings), testSubscriptionServers (batch worker + CompletableDeferred await), onDestroy (MaintenanceStatus.finish safety net + worker cancels + serviceJob.cancel + stopForeground + notification cancel).
- Supporting: **`handler/MaintenanceStatus.kt` (133 lines, fork-only)** — cross-process maintenance-card store over multi-process MMKV settings keys (string-encoded), writer side (:tasks / CoreTestService) and reader side (UI poll).

### Upstream comparison result
- `CoreTestService.kt`, `RealPingWorkerService.kt`, `SubscriptionUpdateService.kt`: fork = upstream + **MaintenanceStatus progress-card integration only** (workerProgress/settled sums/publish/abandon/finish wiring + Progress/Finish events extended with completedInBatch/batchTotal). Zero behavioral divergence elsewhere; no missing upstream fixes. `MaintenanceStatus.kt` is fork-only.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (contract proof) | CoreTestService event guard | The `lateinit worker` + `activeWorkers.containsKey(worker)` double-guard means a CANCELLED worker (entry removed by cancelWorkers) can never deliver Result/Progress/Finish into the service — no stale results, no finish double-fire; worker entries are removed atomically via `ConcurrentHashMap.remove(key, value)` keyed by requestId, so a newer UI request's reply is never sent to an old cancel. `scope.isActive` in the worker independently gates the same events. | No action. |
| 2 | CLEAN | Worker lifecycle | Dispatcher closed in the finisher's finally on BOTH complete and cancel paths (joinAll throws CancellationException on cancel → close still runs); SupervisorJob contains per-guid failures; TCP pre-check's `serverPort.toInt()` is guarded by `toIntOrNull() != null`; CUSTOM configs serialized globally because parallel teardown aborts the native probe (in-code rationale). | No action. |
| 3 | CLEAN | SubscriptionUpdateService refcounting | `runningTasks` incremented before launch; finish/stopSelf only when `decrementAndGet()==0 && activeWorkers.isEmpty()` — concurrent Semaphore permits cannot stop the service under a still-running task; new START after stopSelf re-arms startForeground. onDestroy cancels workers (cancelled → no Finish) but the awaiter lives in the cancelled serviceScope, so no deferred hang. | No action. |
| 4 | Info (cosmetic, fork-only) | CoreTestService:153 | `publishTestProgress(0, guidsList.size)` on worker start is UNCONDITIONAL — an overlapping second batch blips the maintenance bar to 0/N2 once, contradicting the in-code comment "overlapping workers … never move the bar backwards"; the next per-server event re-publishes the correct settled+running sum. Requires two test triggers within seconds; purely cosmetic, self-corrects. The correct pattern already exists one function below (`publishMaintenanceTestProgress`). | Noted (below release bar). |
| 5 | Info (cosmetic, fork-only) | MaintenanceStatus writer side | `doneBase` @Volatile assumes sequential batches ("Test batches run sequentially" comment) — true per task, but Semaphore(2) admits two concurrent tasks whose reportBatchProgress/endTestBatch interleave → transient non-monotonic/below-true progress; beginTestBatch's read-modify-write on TOTAL can lose one increment. Both are display-only; session end still publishes done=total. Same rare double-trigger premise as #4. | Noted. |
| 6 | Info (upstream parity) | RealPingWorkerService alpn asymmetry | startRealPing skips the TCP pre-check when alpn *starts with* h3; startTcping skips when *all* alpn entries are h3 — different leniency for the same field (upstream-identical, byte-preserved in the fork). Both are pre-filter heuristics; worst case one extra 1s connect attempt. | Noted. |
| 7 | Info | SubscriptionUpdateService cancel path | MSG_SUB_UPDATE_CANCEL stops foreground + self but does NOT cancel in-flight update coroutines (serviceJob.cancel happens in onDestroy; blocking fetch/JNI work inside the coroutine runs to completion or dies at the next suspension). Matches the service's START_NOT_STICKY one-shot design; no user-visible hang (maintenance card safety-netted in onDestroy). | Noted. |

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 22)
- **Remaining `handler/` files by risk: `SpeedtestManager.kt` (second pass — the socket/TLS probe and getRemoteIPInfo paths feed real-ping) + `AppLocaleManager.kt` / `OnboardingManager.kt` / `IntegrityManager.kt` / `CertificateFingerprintManager.kt`** (small, security-adjacent). Then Run 23 = `ui/` ad surfaces (`ui/compose/HouseAds.kt` second pass, banners) or `helper/` (MessageHelper/NotificationHelper) — pick per risk.

---

## Run 22 — 2026-11-04

### Files reviewed
- **`handler/SpeedtestManager.kt` (94 lines, deep second pass)** — `socketConnectTime` (unconnected Socket + connect timeout, measured window INCLUDES DNS resolution because `start` is captured before the `InetSocketAddress` ctor resolves; finally-close guarded by `isClosed` + inner try), `getRemoteIPInfo` (PREF_IP_API_URL → `AppConfig.IP_API_URL` fallback, `httpPort == 0` early-out, HttpUtil fetch, `JsonUtil.fromJsonSafe` null-tolerant parse, ip ladder ip→clientIp→ip_addr→query and country ladder country_code→country→countryCode→location.country_code).
- **`handler/CertificateFingerprintManager.kt` (79 lines)** — `fetchForManualFill` (HYSTERIA2 → `Libv2ray.fetchQuicCertSha256`, else TLS path), `buildRequest` (server required, `toIntOrNull>0 ?: DEFAULT_PORT`, `resolveDialAddress` prefers resolved IP with domain fallback, `inferServerName` drops pure-IP SNI), `fetch` (catches `UnsatisfiedLinkError` for libv2ray builds missing the JNI API + generic Exception → null).
- **`handler/IntegrityManager.kt` (182 lines, fork-only — anti-tamper, advisory by design)** — `check` (per-check try/catch, cache), `signatureMatches`/`getSigningCertSha256` (API 28 signingInfo / legacy GET_SIGNATURES, SHA-256 vs `AppConfig.RELEASE_CERT_SHA256`, fail-closed), `isRooted` (su paths + `which su` exec), `isHookingFrameworkPresent` (Xposed-family packages, Frida port 27042 + staging paths), `isDebuggerAttached` (TracerPid).
- Contract cross-checks: callers of `socketConnectTime` = RealPingWorkerService startRealPing/startTcping (worker fixed-pool dispatcher, 1000ms override, `toIntOrNull` guard precedes `toInt` on an immutable decoded profile); caller of `getRemoteIPInfo` = CoreServiceManager:416 on `connectionTestScope` (SupervisorJob + Dispatchers.IO) and only after a successful delay measure; caller of `fetchForManualFill` = BaseServerActivity:327 wrapped in `withContext(Dispatchers.IO)` with `isFetchingCert` UI gate; caller of `IntegrityManager.check` = MainActivity.onCreate:132 (main thread, one-shot).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | SpeedtestManager both paths | Resource-safe: socket closed in finally (isClosed + inner catch); all exceptions → -1; blocking connect is not interruptible on scope cancel but is bounded by the 1s caller timeout and runs on worker-pool threads. getRemoteIPInfo: every failure path returns null (no throw), parse + HTTP both null-tolerant; ladder reads are null-safe. The `UnknownHostException` catch in socketConnectTime is technically dead (InetSocketAddress ctor returns an unresolved address instead of throwing; connect on it hits the generic Exception catch) — harmless. `httpPort == 0` is vestigial (getHttpPort = getSocksPort() + isXray() flip, never 0 with default 10808) but upstream-identical. | No action. |
| 2 | CLEAN | CertificateFingerprintManager (all) | Null-chain complete: buildRequest null → null; fetch exceptions (incl. UnsatisfiedLinkError) → null; result gated on `error.isBlank()` + non-blank sha256. `resolveDialAddress` falls back to the domain when resolution fails (JNI probe does its own resolution). Caller is IO-dispatched with a button-disable gate (no double-fetch). | No action. |
| 3 | CLEAN (fail-closed verified) | IntegrityManager signatureMatches | getSigningCertSha256 null → `return false` → SIGNATURE flag; exception inside the signature try → SIGNATURE flag explicitly. Constant matches the official fingerprint (same `RELEASE_CERT_SHA256` Run 19 verified for the updater). Legacy path firstOrNull matches the updater's pattern. | No action. |
| 4 | Info | IntegrityManager.check:43 cache | Check-then-act on `cached` — two threads could compute twice; the computation is deterministic and the result set is identical, so last-write-wins is benign. Caller today is main-thread-only. | Noted. |
| 5 | Info | IntegrityManager.isRooted:125-128 | `exec("which su")` has no waitFor timeout and no stderr drain; stdout is drained before waitFor (no stdout deadlock) and `which` never writes near the 64KB stderr pipe bound, so a hang is not realistic. Runs on the main thread in onCreate — realistic worst case (fork+exec ~10-50ms + 150ms localhost socket + 5 binder getPackageInfo) is far below ANR thresholds for a one-shot advisory check. No timeout is the correct-cause-of-no-bug tradeoff, not a defect. | Noted. |
| 6 | Info | IntegrityManager.isDebuggerAttached:176 | `toIntOrNull() != 0` flags a MALFORMED TracerPid value (null != 0 → true) — spurious HOOKING flag on a non-numeric line, unreachable on stock kernels (TracerPid is always `\t<digits>`); advisory-only impact. | Noted. |
| 7 | Info (ledger correction) | handler/ listing | `OnboardingManager.kt` listed in the Run 21 tentative plan does not exist under `handler/` (onboarding lives as `ui/OnboardingActivity`). Replaced by the actual remaining small files in future plans. | Noted. |

### Upstream comparison result
- `SpeedtestManager.kt`: **byte-identical to upstream HEAD**. `CertificateFingerprintManager.kt`: **byte-identical to upstream HEAD**. `IntegrityManager.kt`: **404 upstream — fork-only** (anti-tamper layer documented in-class as advisory/monetization-hardening). No missing upstream fixes in the reviewed set.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 23)
- **`handler/SubscriptionUpdater.kt` (189 lines — periodic/one-shot subscription scheduling feeding the Run 20-fixed chunked refresh path) + `handler/SubscriptionInfoParser.kt` (121 lines — subscription header/usage parsing)** as one logical flow, then the leftover small handlers (`AppLocaleManager`, `SettingsChangeManager`, `OrphanProfileCleaner`, `ProfileReplacement` re-check) if budget allows. Then Run 24 = `ui/` ad surfaces (`ui/compose/HouseAds.kt` second pass, banner providers) or `NotificationManager.kt` — pick per risk.

---

## Run 23 — 2026-11-04

### Files reviewed
- **`handler/SubscriptionUpdater.kt` (189 lines)** — WorkManager periodic auto-update scheduler: `sync` (KEEP on startup/boot, REPLACE only on forceReschedule), `syncOne` (REPLACE — save/manual paths), `cancelOne` (RemoteWorkManager cancel), `updateLastUpdatedAndReschedule` (timestamp write + reschedule — the rapid-loop guard), `scheduleOne` (interval floor at SUBSCRIPTION_MIN_INTERVAL_MINUTES, initialDelay = lastUpdated+interval−now with 5s REPLACE floor, NetworkType.CONNECTED constraint, per-sub unique name), `UpdateTask.doWork` (subId guard → timer reset → `startForegroundService`-delivered MSG_SUB_UPDATE_START → success).
- **`handler/SubscriptionInfoParser.kt` (121 lines)** — subscription panel header parsing: `parse` (all-null → null UI), `withWebPageFallback` (sub URL as info-page fallback), `sanitizeLink` (http/https/tg allowlist, host required for non-tg, 2048 cap, runCatching URI), `isTelegramLink`, `parseUserInfoFields` (semicolon split, key≥1 char, Long-or-finite-Double values, negative→0), `normalizeExpire` (≥1e11 = ms → /1000), `decodeTitle` (base64: prefix → URLDecoder-%-fallback → raw; 100-char cap).
- Contract cross-checks: `MessageHelper.sendMsg2SubscriptionService` uses `ContextCompat.startForegroundService` for START (matches SubscriptionUpdateService's per-start startForeground, Run 21) and `stopService` for CANCEL; deletion site `SettingsManager.removeSubscriptionWithDefault:330` calls `cancelOne(subid)` AND cancels every overflow chunk group (`DERAFSH_POOL_CHUNK_PREFIX` + guid) — the fork's chunk groups die with their parent subscription; `sync` callers (BootReceiver:60, MainRepository:253) default KEEP; `syncOne` caller (SubEditActivity:97) REPLACE; `lastUpdated` writers: AngConfigManager:773 (=now on successful refresh) and :721 (=−1 on fresh chunk re-create → next scheduleOne computes delay 0 → immediate first run).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | scheduleOne delay math | `initialDelay = max(0, lastUpdated + intervalMillis − now)`; lastUpdated≤0 → 0 (immediate); REPLACE floor 5s prevents the save→instant-fire→reschedule loop the comment warns about; KEEP policy correctly ignores delay (WorkManager keeps the existing period). | No action. |
| 2 | Info (self-healing) | sync filter vs scheduleOne url guard | A subscription flipped to autoUpdate=false is dropped by sync's filter so sync never cancels it — but its next periodic fire calls `updateLastUpdatedAndReschedule` → `syncOne` → `scheduleOne` sees `!autoUpdate` → `cancelOne` (self-cancels one cycle late). Same for autoUpdate=true with a CLEARED url: scheduleOne skips WITHOUT cancel (log only) — the orphan task keeps firing each interval but the service-side `updateSingle` url guard (Run 21) makes each fire a no-op. Upstream-identical design; bounded waste, no user impact. | Noted. |
| 3 | Info (design) | UpdateTask timer reset at start | `updateLastUpdatedAndReschedule` stamps lastUpdated BEFORE the actual fetch and never returns retry — a failed refresh still waits a full interval (no WorkManager backoff). Deliberate anti-loop tradeoff per the in-code comment; manual refresh path (SubscriptionsViewModel:148 → service → AngConfigManager:773) updates the timestamp on success for the NEXT reschedule. | Noted. |
| 4 | Info | scheduleOne interval arithmetic | `intervalMinutes * 60 * 1000L` multiplies Int×Int before widening — overflows only above ~35.7M minutes (≈68 years) of user-set interval, where the delay math is meaningless anyway; WorkManager's own interval uses long math correctly. Upstream-identical. | Noted. |
| 5 | CLEAN | SubscriptionInfoParser (all) | Fail-closed link sanitizer (scheme allowlist + host requirement + parse-failure → null); expire normalization boundary (1e11 s = year 5138) safely separates seconds from ms epochs; userinfo parser handles floats (`1e12` → toLong), negatives (→0), empty keys (skipped); title decode never throws (URLDecoder runCatching → original, base64 via Utils::decode's own tolerance); empty result → null so the UI shows nothing rather than a zeroed card. `parse` returns the ORIGINAL link text (not URI-normalized) — fine for ACTION_VIEW. | No action. |
| 6 | Info (positive) | removeSubscriptionWithDefault | The fork correctly extends the upstream deletion contract to overflow chunk groups (cancel + remove per chunk) — without it, deleting a parent subscription would orphan its chunk tasks AND their subscription records. | No action (keep). |

### Upstream comparison result
- `SubscriptionUpdater.kt`: **byte-identical to upstream HEAD**. `SubscriptionInfoParser.kt`: **byte-identical to upstream HEAD**. The only fork-side logic in this flow lives OUTSIDE these two files (`removeSubscriptionWithDefault` chunk-group cleanup — verified correct). No missing upstream fixes.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 24)
- **`handler/NotificationManager.kt` (298 lines — notification channels/actions, PendingIntent immutability, action routing) + `helper/MessageHelper.kt` (the sendMsg2* cross-process message family — startForegroundService contracts, receiver intents)** as one logical flow. Then Run 25 = `ui/` ad surfaces (`ui/compose/HouseAds.kt` second pass, AdsManager banner providers, TapsellAds) — pick per risk.

---

## Run 24 — 2026-11-04

### Files reviewed
- **`handler/NotificationManager.kt` (298 lines)** — foreground notification + speed ticker: `showNotification` (FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT PendingIntents, stop/restart broadcasts pinned to ANG_PACKAGE, channel via NotificationHelper IMPORTANCE_LOW + VISIBILITY_PRIVATE, `startForeground(1, …)`), `ensureForeground` (duplicate-startForegroundService contract fulfillment, Run 18 call site), `cancelNotification` (stopForeground REMOVE + builder/job/manager reset), `startSpeedNotification`/`stopSpeedNotification` (3s loop on Dispatchers.IO, job stored/cancellable), `updateSpeedNotificationOnce` (interval-skip guard, delta→speed division by ≥3.0s, zero-speed suppression `!zeroSpeed || !lastZeroSpeed`, TAG_DIRECT vs non-blocked accumulation), `appendSpeedString` (cosmetic tab alignment), `updateNotification` (icon switch at 3000B threshold, BigTextStyle, notify).
- **`helper/MessageHelper.kt` (169 lines)** — the sendMsg2* family: `sendMsg2Service` / `sendMsg2UI` (package-scoped broadcasts), `sendMsg2ServiceForResult` (ordered broadcast, initial RESULT_CANCELED → onResult(false) when no daemon handles it, try/catch → false), `sendMsg2TestService` (START → startForegroundService on O+ matching CoreTestService's per-start startForeground [Run 21]; CANCEL → stopService with do-not-wake comment; START failure → sends MSG_MEASURE_CONFIG_CANCEL to UI), `sendMsg2SubscriptionService` (same shape; CANCEL → stopService), `messageIntent` (package always set, key/content extras).
- Contract cross-check: `CoreServiceManager.queryAllOutboundTrafficStats` (354-380) — the `isRunning()` early-return ("stats manager gone once the core stops") is upstream-identical (upstream 286-288); payload parse is fully defensive (blank/short/non-numeric entries skipped). The speed loop's delta-per-query contract is established by the identical upstream division.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | showNotification PendingIntent set | All three PendingIntents FLAG_IMMUTABLE; broadcast intents carry `package` = ANG_PACKAGE (cannot be intercepted by other apps); request codes distinct (0/1/2) so stop and restart don't collide under FLAG_UPDATE_CURRENT. `startForeground(id, mBuilder?.build())` — builder is non-null by construction (assigned the line above); the `?.` is vestigial, not a null-notification path. | No action. |
| 2 | CLEAN (positive divergence) | showNotification:96-99 + stopSpeedNotification:152 | Fork = upstream + 2 hunks: (a) **brand-only notification** — upstream publishes `currentConfig?.remarks` (the server name/IP) as the notification title; the fork deliberately shows app name + "Connected" so lockscreen/shade never leaks server identity (matches VISIBILITY_PRIVATE channel). (b) stopSpeedNotification resets text to "Connected" instead of upstream's empty string. Both intentional improvements; `currentConfig` param retained for caller-signature parity. | No action (keep). |
| 3 | CLEAN | Speed loop lifecycle | Job stored → cancelNotification/stopSpeedNotification cancel it; in-flight iteration after cancel is contained (updateNotification no-ops when mBuilder==null; next isActive check exits); showNotification resets lastQueryTime so the first post-show round is skipped, not mis-divided; zero-speed suppression updates exactly one final 0.00 round then goes silent. | No action. |
| 4 | Info (upstream parity) | startSpeedNotification:52 | `CoroutineScope(Dispatchers.IO).launch` — a fresh scope per start whose only handle is the Job (cancel works, scope leak is none — GC'd with the job). An exception thrown inside the loop (JNI queryStats) would be uncaught → crash; upstream-shared shape and the JNI call returns a plain string (no realistic throw path), further guarded by the isRunning() empty-list return. | Noted. |
| 5 | Info (upstream parity) | lastQueryTime | Long written from main (showNotification) and the IO loop without @Volatile — theoretical torn read on 32-bit only; worst case one skipped/double-counted speed round, self-corrects. Upstream-identical. | Noted. |
| 6 | CLEAN | MessageHelper (all) | Ordered-broadcast result receiver uses the platform form correctly (initial code RESULT_CANCELED = "no daemon" reaching MainRepository's MeasureDelayCancelled path); every send wrapped in try/catch; test-service START failure actively closes the UI wait with MSG_MEASURE_CONFIG_CANCEL; the foreground-service wake contracts match the audited per-start startForeground in both ":tasks" services (Run 21). Serializable extras cross-process (Intent parcels them) — in production since v1.2.25. | No action. |

### Upstream comparison result
- `MessageHelper.kt`: **byte-identical to upstream HEAD**. `NotificationManager.kt`: fork = upstream + **2 intentional hunks** (privacy brand-only content; non-empty text reset) — no missing upstream fixes. `CoreServiceManager.queryAllOutboundTrafficStats` guard is upstream-identical.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 25)
- **`ui/` ad surfaces**: `ui/compose/HouseAds.kt` (second pass), `handler/AdsManager.kt` banner/interstitial providers, `handler/TapsellAds.kt` (47 lines) — the monetization flow as one logical sweep (Ad lifecycle leaks, thread-safety of ad callbacks, session-gating logic). Then Run 26 = leftover small handlers (`AppLocaleManager`, `SettingsChangeManager`, `OrphanProfileCleaner`, `ProfileReplacement` re-check) or `ui/` remaining viewmodels — pick per risk.

---

## Run 25 — 2026-11-04

### Files reviewed (monetization sweep — all fork-only, no upstream parity safety net)
- **`handler/AdsManager.kt` (377 lines)** — full line-by-line: init (house-mode SDK refusal + main-process gate + fail-closed reflection fallback), `isMainProcess` (API 28 getProcessName / ActivityThread reflection below, runCatching → null → "not main"), waterfall showAppOpen/showInterstitial/showRewarded (Tapsell → AdMob → house, every failure path falls through with onClosed), INTERSTITIAL_MIN_INTERVAL_MS=180s elapsedRealtime cap, house single-flight pending reward/close @Volatile pair + `onHouseAdDismissed` (consume-on-first-call: reward then closed, each try/catch), buildShowCallback (onDismissed AND onFailedToShow both route onDismiss — caller flow always continues), preload trio (isLoading markers + loaded-instance guard, all callers main-thread).
- **`ui/HouseAdActivity.kt` (45 lines)** — hosts DerafshHouseInterstitial; onClosed = onHouseAdDismissed + finish; onDestroy(isFinishing) fires onHouseAdDismissed again for back-press/other finishing destroys (in-code rationale: stale callbacks must not fire on a LATER house ad); rotation (isFinishing=false) preserves pending for the recreation.
- **`ui/compose/HouseAds.kt` (154 lines, second pass)** — DerafshHouseBanner (static, a11y description, opens APP_URL) + DerafshHouseInterstitial (Continue button + 4s LaunchedEffect auto-dismiss — "must never trap the user").
- **`ui/compose/BannerAd.kt` (119 lines)** — house-mode short-circuit; AdView remembered per adaptiveWidthDp, DisposableEffect loadAd/destroy, AdListener writes snapshot state (main thread per SDK), failure → house banner fallback (slot never empty while failing), adaptive-size try/catch → 320×50.
- **`handler/TapsellAds.kt` (47 lines)` — stub SDK seam: isConfigured = TAPSELL_APP_KEY.isNotBlank() (false today) → every leg returns false → waterfall falls through. No logic to fail.
- Call sites: all four triggers live in MainActivity (131 init onCreate, 148 showAppOpen, 160 showInterstitial connect-flow, 299 showRewarded extend-session) — UI process, main thread only.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | HouseAdActivity double-fire | Continue/auto-dismiss path invokes onHouseAdDismissed then finish() → onDestroy(isFinishing) invokes it AGAIN — second call sees both pendings already consumed (nulled) → no-op. Back-press (no onClosed) is resolved by the onDestroy branch. The stale-callback hazard documented in-file (backed-out reward auto-connecting on a later ad) is correctly closed. | No action. |
| 2 | CLEAN | Waterfall fall-through | Every unconfigured/unloaded/failed leg continues to the next and ultimately calls onClosed — no path leaves the caller (connect/extend-session flow) hung. Frequency cap path also calls onClosed immediately. onFailedToShowFullScreenContent routes to the same onDismiss as success. | No action. |
| 3 | CLEAN | Instance-clear discipline | Interstitial/rewarded: nulled BEFORE show (second entry cannot double-show) with identity-cleared catches. AppOpen: NOT nulled before show — a second sequential show() on the same instance throws inside the same frame, caught → `if (appOpenAd === ad) appOpenAd = null` + reload: behavior correct (contained), just asymmetric with the other two formats. | Noted (below release bar; no user-visible defect). |
| 4 | Info (unreachable) | house single-flight overwrite | A second house trigger while HouseAdActivity is foreground would overwrite housePendingReward/Closed (dropping the first reward). Unreachable: all triggers are MainActivity user actions and MainActivity is covered/stopped while the house ad shows; the 4s auto-dismiss bounds the window. | Noted. |
| 5 | Info (SDK contract) | BannerAd no-callback edge | If the SDK never fires onAdLoaded/onFailedToLoad (network hang), the slot stays collapsed (no house fallback) — state machine has no timeout by design; self-resolves on recomposition/width change. Not actionable without a timeout timer (over-engineering for a collapsed zero-height slot). | Noted. |
| 6 | CLEAN | init process gate | isMainProcess reflection failure on <API 28 → null → "not main" → SDK skipped — fail-closed in the safe direction (no ads rather than SDK in :daemon/:tasks/:bg). init is idempotent (initialized flag); house mode returns before ANY SDK touch. Preload check-then-act on @Volatile is race-free because every caller is main-thread. | No action. |

### Upstream comparison result
- `AdsManager.kt`, `HouseAds.kt`, `BannerAd.kt`, `TapsellAds.kt`: **404 upstream — fork-only** (monetization is Derafsh-specific; upstream v2rayNG has none). Correctness argued from the in-code contracts (never-block-user waterfall, always-resolve pending callbacks, no-crash guarantee). No upstream fixes to miss.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 26)
- **Leftover small handlers as one closing sweep: `AppLocaleManager.kt` (140), `SettingsChangeManager.kt` (58), `OrphanProfileCleaner.kt` (53), `ProfileReplacement.kt` (76 re-check)** — after this the entire `handler/` + `service/` + `core/` + `fmt/` surface is covered. Then Run 27 = remaining `ui/` viewmodels or a core second pass on any file the interim diff-with-upstream flags — pick per risk.

---

## Run 26 — 2026-11-04

### Files reviewed (closing sweep — handler/ now fully covered)
- **`handler/AppLocaleManager.kt` (140 lines)** — legacy MMKV language pref ↔ Android per-app locale two-way sync: `initialize` (API 33 prepareMigration / compat path), `onActivityCreated` (completeMigration / AppCompat read-back sync), `setApplicationLanguage` (persist + AppCompatDelegate), `localizedContext` (non-activity contexts during the one-time upgrade window fall back to the still-current MMKV value via createConfigurationContext), `Api33.prepareMigration` (one-time PREF_APP_LOCALE_MIGRATED; framework locales win, else legacy non-AUTO pushed to LocaleManager), `Api33.completeMigration` (Android's required one-time AppCompat handoff after Activity.onCreate, then continuous MMKV←framework sync).
- **`enums/Language.kt` (37 lines, cross-check)** — 3 entries (AUTO/ENGLISH/PERSIAN); `locale` null only for AUTO; `fromCode` unknown → AUTO; `fromLanguageTag` non-en/fa → AUTO.
- **`handler/SettingsChangeManager.kt` (58 lines)** — AtomicBoolean flags with CAS consume (`consumeRestartService`/`consumeSetupGroupTab`), `uiOnlyKeys` set (PREF_LANGUAGE etc. → tab refresh only, no core restart).
- **`handler/OrphanProfileCleaner.kt` (53 lines, internal, pure)** — fail-closed orphan classification: undecodable group index → null (no cleanup), empty profile set → empty, null subscriptionId payload → preserved, selected server → kept, unknown groupId → kept (conservative).
- **`handler/ProfileReplacement.kt` (76 lines re-check, internal, pure)** — deterministic re-anchor ladder (remarks-full → remarks-only → server+port+password → server+port → server → first) + `findRemovablePayloads` fail-closed (null cross-group refs → emptySet; protected/replacement/cross-referenced all excluded). `isSameText` null/blank → false.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | Api33.prepareMigration:115 | `requireNotNull(legacyLanguage.locale)` cannot throw: the branch is guarded by `legacyLanguage != Language.AUTO` and Language.locale is null ONLY for AUTO (empty languageTag). `fromCode` maps unknown codes → AUTO, so a corrupt MMKV value degrades to AUTO (no migration), never a crash. | No action. |
| 2 | CLEAN | Migration both directions | Framework locales take precedence when present (system-settings changes win), legacy MMKV only seeds when the framework record is empty; the migration flag makes the handoff exactly-once; post-migration every onActivityCreated re-syncs MMKV ← framework so the legacy pref (read by localizedContext and the pre-13 paths) never goes stale. On 13+ AppCompat.setApplicationLocales writes through to the framework, so in-app selection stays consistent. PREF_LANGUAGE lands in uiOnlyKeys → language change correctly does NOT restart the VPN. | No action. |
| 3 | CLEAN | SettingsChangeManager (all) | CAS consume = exactly-one-consumer semantics; flags are set-from-anywhere/consumed-on-main — a lost set between set and consume is impossible (CAS from true). This is the Run 15/16-repaired contract (restore restart flag) in its final shape. | No action. |
| 4 | CLEAN | OrphanProfileCleaner + ProfileReplacement | Both pure, both fail-closed in the correct direction (cleanup/refcount decisions return "do nothing" on uncertainty). The re-anchor ladder is heuristic BY DESIGN (best-effort selection restore after a replace); worst case the selection lands on the first profile — documented in Run 20 #1's fix context. No null-unsafe path, no mutation. | No action. |

### Upstream comparison result
- **All four files byte-identical to upstream HEAD** (`AppLocaleManager`, `SettingsChangeManager`, `OrphanProfileCleaner`, `ProfileReplacement`). With this run the **entire `handler/` package is covered** (19/19 files across Runs 5, 8, 13, 14, 19–26). No missing upstream fixes.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 27)
- **`ui/` viewmodels — the last uncovered layer with real logic: `ui/subscription/SubSettingViewModel` / `ui/profile/ProfileViewModel` etc. (coroutine-heavy surfaces: settings IO, MMKV writes, activity-scoped scopes)** — pick the 2-3 largest per risk. Then Run 28 = upstream-drift re-scan: re-diff the three fork-fixed files (`CoreVpnService.kt`, `UpdateManager.kt`, `AngConfigManager.kt`) + the upstream-changed natives against upstream HEAD to catch post-v1.2.25 upstream fixes worth adopting.

---

## Run 27 — 2026-11-04

### Files reviewed (ui/ viewmodels, trio 1 of 2)
- **`ui/backup/BackupViewModel.kt` (224 lines)** — WebDAV config StateFlow + events; `cleanupProfileStorage` (IO + fail-closed "skipped" toast surfaced to the user when a group index is undecodable), `shareBackup`/`prepareBackupForUri` (backup-to-cache → event), `backupViaWebDav`/`restoreViaWebDav` (fresh config decode per call, temp file deleted in finally on both paths), `backupConfigurationToCache` (MMKV.backupAllToDirectory → zip → **plaintext MMKV folder + partial zip deleted immediately** — credentials note in-code), `performRestore` (withContext(IO), nanoTime-unique restore dir, ZipUtil unzip [128/256MiB budgets audited Run 6], restoreAllFromDirectory, WebDAV cache re-sync on success, makeSetupGroupTab + makeRestartService, finally deleteRecursively).
- **`ui/perappproxy/PerAppProxyViewModel.kt` (266 lines)` — blacklist StateFlow (persisted on change), master switches (persist-on-change), `loadApps` (pre-set loading flag before launch, CancellationException rethrow, IO hop for PackageManager scan), Collator sort (selected-first → non-system-first → name), bulk actions (select-all/invert/auto-import → `enablePerAppProxyAndRestart`), `selectProxyAppAuto` (direct fetch → proxied fallback, same pattern as fetchSubscriptionContent), `applyProxyAppList` (assets fallback, AppSelection.fromProxyList on Default, CancellationException rethrow).
- **`ui/base/BaseViewModel.kt` (128 lines)` — viewModelScope contract: launchLoading (finally-resets isLoading), event Channel (RENDEZVOUS, send suspends until collected — cancelled with viewModelScope on clear), localizedContext via AppLocaleManager (Run 26-verified).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (positive divergences ×3) | BackupViewModel vs upstream | Fork = upstream + 3 hunks, all improvements: (a) `backupViaWebDav`/`restoreViaWebDav` decode the WebDAV config FRESH from MMKV instead of the cached StateFlow (a restore overwrites the saved config after caching — the stale value would upload/restore with wrong credentials), (b) the plaintext MMKV backup folder (credentials included) is deleted immediately after zipping + partial zip removed on failure — upstream leaves the folder in cacheDir indefinitely, (c) `_webDavConfig` re-synced after a successful restore so the dialog reflects restored values. | No action (keep). |
| 2 | Info (upstream-shared) | backupConfigurationToCache call sites | The local backup path (MMKV native copy + folder zip + deleteRecursively) runs on the Main dispatcher inside launchLoading — no IO hop, unlike `performRestore` which is explicitly IO. Upstream has the identical shape (diff confirms zero hunks in shareBackup/prepareBackupForUri/backupViaWebDav). Bounded impact: MMKV stores are small (native bulk copy) and the zip is of a few small files; worst case a short jank frame on slow flash. The heavy legs (WebDAV upload/download, unzip/restore) ARE on IO. | Noted. |
| 3 | CLEAN | BackupViewModel temp hygiene | Every temp artifact (backup zip on WebDAV path, downloaded restore zip, restore folder) is deleted in finally — no cache accumulation even on failure; unique names (timestamp/nanoTime) prevent concurrent-run collisions. Restore of an untrusted zip is bounded by ZipUtil's budgets (Run 6). | No action. |
| 4 | CLEAN | PerAppProxyViewModel (all) | The master-switch persists without makeRestartService — upstream-identical (byte-equal diff); the bulk paths DO set the flag; both are coherent with upstream's restart contract (SettingsActivity path covers the switch). Loading flag set synchronously BEFORE launch (no TOCTOU window, unlike the Run 19 TOCTOU class); CancellationException correctly rethrown in both coroutine bodies; fromProxyList/asset read properly dispatched (Default/IO). | No action. |
| 5 | Info (benign) | BaseViewModel.launchLoading | Not serialized — two concurrent launchLoading calls toggle the shared isLoading independently (spinner can drop while the second task still runs, then re-set false again). All current callers are user-action-gated. RENDEZVOUS Channel send suspends if no collector — freed by viewModelScope cancellation on VM clear. Upstream-identical. | Noted. |

### Upstream comparison result
- `PerAppProxyViewModel.kt`, `BaseViewModel.kt`: **byte-identical to upstream HEAD**. `BackupViewModel.kt`: fork = upstream + **3 intentional hardening hunks** (stale-config fix, plaintext-artifact hygiene, post-restore re-sync). No missing upstream fixes.

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 28)
- **`ui/main/MainViewModel.kt` (1373 lines) — dedicated deep pass, part 1**: service-state collection, message/receiver wiring, start/stop/restart flows, and the sub-refresh entry points (everything up to and including the import/QR/clipboard flows). Run 29 = part 2 (remaining flows + settings/speedtest consumers) + full upstream diff of the file. Then Run 30 = upstream-drift re-scan of the three fork-fixed files (CoreVpnService/UpdateManager/AngConfigManager) against upstream HEAD.

---

## Run 28 — 2026-11-04

### Files reviewed
- **`ui/main/MainViewModel.kt` (1374 lines) — dedicated deep pass, PART 1 (lines 1-800)**: init (cache clear + setupGroupTab on IO), `collectServiceEvents`/`handleServiceEvent` (all 12 event types), `queueTestResult`/`flushPendingTestResults` (pending-map + chained flush jobs), group flow maps (`serversForGroup` computeIfAbsent, `allServerRows` flatMapLatest combine), `setupGroupTab` (3-job cancellation, cache clear, group pruning, radial preload), `loadGroup`/`buildServersCache` (per-group mutex + cacheMutex double-checked pattern), `applyKeywordFilter`/`buildServerRows`/`resolveSelectedGroup`/`radialPreloadOrder`, `initialize`/`runStartupMaintenance` (4h auto-refresh + startup test pass), `importBatchConfig`/`importConfigViaSub`/`switchDerafshSourceAsync`/`exportAllAsync` (IO-dispatched with toasts).
- Cross-check: **`extension/ToastExt.kt`** — `runOnMain` (Looper check → mainHandler.post), `dispatchMessage` uses applicationContext; Snackbar-first routing.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (contract proof) | toast-from-IO pattern | Every business action calls `toast/toastSuccess/toastError` INSIDE `withContext(ioDispatcher)` — safe because ToastExt's `runOnMain` posts to the main looper when off-main (no "Can't toast on a thread that has not called Looper.prepare()" crash) and uses applicationContext (no activity leak). Resolves the only main-thread-crash candidate in the whole ViewModel. | No action. |
| 2 | CLEAN | handleServiceEvent request-id guards | Every Measure* event is guarded (`testRequests.completeCurrent(id)`, `bulk?.takeIf { it.id == event.requestId }`), so a stale/cancelled batch can never write into the new one; the single sequential collector means `pendingTestResults` (plain LinkedHashMap) is only touched on the collector's context; flush takes cacheMutex and re-checks the id after each suspension. MeasureConfigFinish chains flushes via `scheduledFlush?.cancelAndJoin()` before writing — no overlapping flush jobs. | No action. |
| 3 | CLEAN | setupGroupTab lifecycle | Cancels setup/preload/selected-group jobs first (no stale group write-back); prunes groupUiFlows/groupServerFlows/groupLoadMutexes for invalid ids (per-key atomic removal); `initialPageReady` completed in finally — `initialize()` can never await forever even when setup throws. Radial preload runs on `limitedParallelism(1)` with 32ms gaps — bounded, ordered, cancellable; radial order enumeration provably duplicate-free (right=sel+d ≠ left=sel−d for d≥1). | No action. |
| 4 | CLEAN | loadGroup double-checked locking | Per-group Mutex serializes loads; cache hit read under cacheMutex; heavy MMKV decode happens OUTSIDE cacheMutex (only the pointer swap holds it); `ensureActive()` before the write-back. Import/refresh paths pass forceRefresh=true to bypass the cache. | No action. |
| 5 | Info (deliberate) | runStartupMaintenance:447 | `setLastAutoMaintenance(now)` runs even when the auto refresh FAILED (catch logs and falls through) — a broken source won't be re-hammered on every app open for 4h; manual refresh remains available and the next window retries. Coherent with MaintenanceStatus.abandon() on cancellation (Run 21 contract). | Noted. |
| 6 | CLEAN | switchDerafshSourceAsync | Reuses the single DEFAULT_SUBSCRIPTION_ID → the update goes through the Run 20-fixed chunked path with builtin-pool append semantics (manual imports preserved, pool replaced in place, chunk groups unaffected); fresh test-results carried; the test pass only runs when pendingTestGuids is non-empty and only beginSession then (card never hangs in "updating" with nothing to test — same guard as runStartupMaintenance). | No action. |
| 7 | CLEAN | applyKeywordFilter | User keyword compiled as Regex with PatternSyntaxException → unfiltered fallback (graceful); matchesPattern combines regex + plain-contains (Run audits verified the extension). Filter applied on the UI snapshot, not persisted state. | No action. |
| 8 | CLEAN | exportAllAsync group/filter semantics | Empty group + empty keyword → full pool export; otherwise the CURRENTLY VISIBLE (filtered) list — matches the UI affordance; StateFlow value reads are thread-safe from the IO context. | No action. |

### Upstream comparison result
- Deferred to Run 29 (part 2 covers the remaining ~574 lines and the FULL upstream diff of MainViewModel — the file is heavily fork-modified: radial preload, source switcher, maintenance session, test-result streaming are all Derafsh additions).

### Release
- None this run (no code changes; docs-only commit). v1.2.35 remains latest.

### Next run starting point (Run 29)
- **`ui/main/MainViewModel.kt` PART 2 (lines 801-1374)**: bulk operations (removeAll/removeDuplicate/removeInvalid/sortByTestResults), group switching/reload, server move/persistence, the bulk-test engine (testAllRealPing/pendingTestGuids/cancel paths), fastest-server pick (FastestPick deferred, freshness window), locate/session flows — plus the FULL upstream diff of MainViewModel.

---

## Run 29 — 2026-11-04

### Files reviewed (MainViewModel part 2 + full upstream diff of the file)
- **`ui/main/MainViewModel.kt` PART 2 (lines 801-1374)**: `removeAllServerAsync`/`removeDuplicateServerAsync`/`removeInvalidServerAsync(+Internal)`/`sortByTestResultsAsync(+Internal)`, `subscriptionIdChanged`/`reloadServerList`/`reloadAllGroups`/`filterConfig`, `updateSelectedGuid`/`refreshSelectedGuid`/`removeServerAndRefresh`, `moveServer` (per-group chained persistence jobs), test engine (`cancelAllPing`, `testAllRealPing(onlyTcp/allGroups/skipTested)`, `pendingTestGuids`, `cancelPendingTestResults`, `testCurrentServerRealPing`, `onTestsFinished`), fastest-pick (`pickFastestServer`, `kickAndAwaitFastestTest`, `pickBestGuid`), `triggerLocateSelectedServer`/`consumeLocateTarget`, start-pending phase (`markStartRequested`/`clearStartPending`), `updateRunningState`, `onCleared`, Factory.
- Cross-checks: `extension/ListExt.kt` (`moveItem` bounds-checked), `dto/entities/ProfileItem.duplicateIdentity` (returns a volatile-fields-zeroed ProfileItem copy → `HashSet<ProfileItem>` dedupe type-correct), `ui/main/MainTestRequests.kt`, `ui/main/MainRepository.kt` (thin delegation; `close()` CAS-guarded, unregister only — MMKV stays valid), `MainDataSource.kt`, `handler/MmkvManager.removeInvalidServer` (removes ONLY `testDelayMillis < 0`; never-tested 0 kept — matches the sweep comment), `handler/AngConfigManager.sweepUnhealthyPoolConfigs` (try/catch fail-safe, builtin pool + chunk prefixes only, empty chunk tabs pruned), `handler/AngConfigManager.sortByTestResultsForSub` (stable sort, failed configs sink to MAX_VALUE).
- **Full upstream diff of MainViewModel.kt** (upstream HEAD `ui/main/MainViewModel.kt`, 969 lines vs fork 1374): part-2 surface byte-identical to upstream for `removeAll*/removeDuplicate*/removeInvalid*/sortByTestResults*/subscriptionIdChanged/reloadServerList/reloadAllGroups/filterConfig/updateSelectedGuid/refreshSelectedGuid/removeServerAndRefresh/moveServer/cancelAllPing/resetTestStatus/cancelPendingTestResults/testCurrentServerRealPing/triggerLocateSelectedServer/consumeLocateTarget/onCleared`. Fork deltas confined to: `testAllRealPing` (allGroups/skipTested/pool expansion + message shaping), `onTestsFinished` (sweep branch + toast + setupGroupTab fallback), `updateRunningState` (+`clearStartPending()`), the whole fastest-pick trio, start-pending phase, session dialogs — all deliberate Derafsh features.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | **BUG (crash-capable, fork-introduced)** — FIXED | `MainViewModel` `pendingTestResults` (old line 132; clear at 1128 vs put 251 / toMap 268) | `pendingTestResults` was a plain `LinkedHashMap`. Upstream touches it ONLY from the main thread (UI actions + event collector), so its plain map was safe BY REACHABILITY. The fork added three IO-thread entries that reach the same state: `runStartupMaintenance`→`testAllRealPing`→`cancelAllPing`→`cancelPendingTestResults` (preloadDispatcher, line 455), `switchDerafshSourceAsync`→`testAllRealPing` (ioDispatcher, line 757), and `kickAndAwaitFastestTest` (ioDispatcher, line 1153). Concurrent IO `clear()` against the main-thread collector's `toMap()` iteration → `ConcurrentModificationException` → uncaught inside `viewModelScope.launch` → process crash. Reachable: user switches source while the startup auto-test is streaming results. | **FIXED in v1.2.36**: map is now `ConcurrentHashMap` (no ordering dependency anywhere: put/isNotEmpty/toMap/clear only). |
| 2 | **BUG (contract violation, fork-introduced)** — FIXED | `MainTestRequests.kt:9-36` + callers on IO (`MainViewModel` 1187-1196, 1213-1214 via `kickAndAwaitFastestTest`; 1063 via IO-path `testAllRealPing`) | `MainTestRequests` documented "Main-thread request ownership" with plain vars — an invariant that held upstream ONLY because nothing reached it off-main. The fork's fastest-pick (`beginBulk`/`cancelBulk`/`isTesting` from ioDispatcher) and IO-path `testAllRealPing` (`beginBulk` from preload/io) broke it: non-volatile cross-thread read/write → stale visibility could drop `MeasureConfigSuccess/Finish` events (results lost), stick the Testing badge, or force the pick into its 10s-timeout fallback. | **FIXED in v1.2.36**: fields are `AtomicReference`s; `completeBulk` is a CAS loop with identical semantics (a mismatched late FINISH still never clears a newer request); `completeCurrent` is a CAS; `bulk` is now a `val` getter (no external assignment existed). |
| 3 | BUG (minor, same family) — FIXED | `MainViewModel` `bulkTestJob` (old line 135; read at 1186 from IO, writes at 1006/1080 on main) | Stale read in `kickAndAwaitFastestTest` → an in-flight manual test's job not cancelled when a pick starts → two concurrent tests (orphan's events dropped by id guards; wasted traffic; recovers when the orphan finishes). | **FIXED in v1.2.36**: `@Volatile`. |
| 4 | Info (same family) — FIXED | `MainViewModel` `lastFullTestFinishAt` (write on main 1250, read on IO 1180), `testResultFlushJob` (assigned on main, cancelled from IO via `cancelPendingTestResults`) | Stale reads → fresh-window miss (unnecessary re-test) / missed flush-job cancel (benign: surviving loop drains empty and exits; bulk-id guards hold). | **FIXED in v1.2.36**: `@Volatile` on both. |
| 5 | CLEAN (upstream-identical) | 801-1001, 1269-1374 | All bulk ops, group switching, moveServer (chained per-group persistence serialization is correct; `moveItem` bounds-checked; rows 1:1 with servers), start-pending phase, `updateRunningState`, `onCleared` (viewModelScope cancellation covers the untracked one-shot launches), Factory. | No action. |
| 6 | Info (upstream-shared) | `sortByTestResultsInternal` 899-905 | With the "All" tab selected, `subs = decodeSubsList()` — the manually-added bucket (subscriptionId "") is never sorted. Upstream-identical grouping design. | Noted. |
| 7 | Info (upstream-shared) | `triggerLocateSelectedServer` 1274-1283 | Locate completing after the user switched groups reverts the newer selection (persisted too); narrow window, recoverable by re-tap. Upstream-identical — not fork-introduced. | Noted. |
| 8 | CLEAN | `onTestsFinished` + `sweepUnhealthyPoolConfigs` | Sweep is try/catch fail-safe (returns 0), restricted to the builtin pool + chunk prefixes, removes only delay<0 (never-tested kept), prunes empty chunk tabs; `refreshSelectedGuid` before `setupGroupTab`; `reloadAllGroups` fallback when nothing removed. | No action. |
| 9 | CLEAN | `pickBestGuid` 1230-1244 | Positive-delay-only winners; secure-first ordering (`allowInsecure` never displaces a verified winner — Derafsh security policy); fallback chain selection → first-valid → null. `pickFastestServer` delivers on Main; VM-clear cancellation simply drops the callback. | No action. |
| 10 | CLEAN | `testAllRealPing` request/message shaping 1060-1073 | `allGroups && !skipTested` → empty sub + empty guids (service expands decodeAllServerList); `skipTested` → explicit pending guids (service tests exactly those); tab pass → subscriptionId + guids only when keyword-filtered — all three shapes match the CoreTestService contract (Run 21). Double-start races covered: the old job's `withContext` throws CancellationException before `sendMsg2TestService`, an already-sent test is stopped by the ordered cancel message. | No action. |

### Upstream comparison result
- Part-2 surface byte-identical to upstream HEAD except the deliberate fork features listed above. `MainTestRequests.kt` was byte-identical upstream (hence upstream's main-thread confinement held); the fork's IO entry points — not upstream — created the races fixed this run.

### Release
- **v1.2.36 (versionCode 40)** — commit `c52baae`, pushed to main, `build.yml` dispatched (`release_tag: v1.2.36`, HTTP 204). Files: `MainTestRequests.kt` (atomics), `MainViewModel.kt` (ConcurrentHashMap + 3× @Volatile + protocol comment), `build.gradle.kts` bump, `changelogs/40.txt` en-US + fa-IR.

### Next run starting point (Run 30)
- **Upstream-drift re-scan (planned since Run 26)**: re-diff the three fork-fixed files (`service/CoreVpnService.kt`, `handler/UpdateManager.kt`, `handler/AngConfigManager.kt`) plus the upstream-changed natives against upstream HEAD to catch post-v1.2.25 upstream fixes worth adopting; also re-diff `ui/main/MainTestRequests.kt` (now fork-divergent by design after this run's atomics fix) and `ui/main/MainViewModel.kt` against upstream to keep the delta ledger current.

---

## Run 30 — 2026-11-05

### Files reviewed (upstream-drift re-scan — planned since Run 26)
- Re-diffed every fork-divergent file named in the plan against **upstream 2dust/v2rayNG HEAD** (raw fetch, 2026-11-04 ~16:20 UTC) to catch post-fork upstream fixes worth adopting:
- **`service/CoreVpnService.kt`** (upstream 372 lines): diff = **exactly the v1.2.33 teardown fix** (capture `ifaceToClose`/`tproxyToStop` before `unlockStart()`, conditional `tun2SocksService` null-out via reference equality, `unlockStart()` moved after teardown completion). Zero other hunks → **zero upstream drift**; nothing upstream changed in this file that the fork lacks.
- **`handler/AngConfigManager.kt`** (upstream 657 vs fork 930, 7 hunks): every hunk maps to a documented fork feature — `isGroupType` import + share-format extension (`CUSTOM -> decodeServerRaw`, `else -> description`), `parseBatchProfiles` extraction (pool chunking), `updateConfigViaSubAll(onSubStart)` + chunk-prefix filter, the chunked importer refactor with the per-sub try/catch containment fix (v1.2.35, carries its own "upstream parity" rationale comment), `sweepUnhealthyPoolConfigs`, and the removal of upstream's private `parseConfigViaSub` — **verified its base64-fallback ladder (`Utils.decode` → raw → `parseCustomConfigServer`) is inlined verbatim at fork lines 188-198 plus a new `parseBatchSubscription` leg**, so no behavior was lost by the removal. Zero unaccounted hunks → **zero upstream drift**.
- **`handler/UpdateManager.kt`**: 404 on upstream (upstream `handler/` has no in-app updater — only `UpdateCheckerManager.kt`); confirmed **fork-only** exactly as already recorded in Run 19 ("correctness argued from the app's own stated threat model"). No upstream comparison possible → no drift possible; contracts audited line-by-line in Run 19 (v1.2.34).
- **`ui/main/MainTestRequests.kt`** (upstream 37 vs fork 46): diff = **exactly the v1.2.36 atomics fix** (AtomicReference fields, CAS `completeBulk` loop, CAS `completeCurrent`, `val bulk` getter, updated protocol comment). Ledger current.
- **`ui/main/MainViewModel.kt`** (upstream 969 vs fork 1374, 23 hunks): all 23 hunks classified — imports (SubscriptionItem/DerafshSourceManager/MaintenanceStatus/MmkvManager + 5 kotlinx additions, 1 upstream import dropped), start-pending phase + connectPhase + source-switcher + session-deadline state, **v1.2.36 fixes** (ConcurrentHashMap + 3× @Volatile + protocol comment), persistent-test-results init change, fastestPick deferred wiring in MeasureConfigFinish/Cancelled, SessionExpired/SessionExtended events, visible-group cache freshness in `flushPendingTestResults`, removal of upstream's `formatStatus`/`formatConnectionTestResult` (fork's connectPhase UI replaces the status-label flow; no dangling references — CI builds), `allServerRows` aggregate flow (Run 28), session/source actions, `runStartupMaintenance` (Run 28), `switchDerafshSourceAsync`, `testAllRealPing` allGroups/skipTested expansion + per-group cache reset loop, `pendingTestGuids`, the fastest-pick trio, `onTestsFinished` sweep branch, `dismissSessionTimeUp`/`clearStartPending`, companion constants. **Zero unaccounted hunks → zero upstream drift.**

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (drift scan result) | all 5 files vs upstream HEAD | **No post-fork upstream fix was missed.** Upstream HEAD has not changed any region the fork shares since the individual audits (Run 18-29): CoreVpnService shows only the v1.2.33 hunks, MainTestRequests only the v1.2.36 hunks, MainViewModel/AngConfigManager only documented Derafsh features. Nothing to adopt. | No action. |
| 2 | CLEAN (ledger verification) | `parseConfigViaSub` removal | The fork deleted upstream's private parse ladder helper; call-site check proves the ladder is inlined at the fork's `updateConfigViaSub` (lines 188-198: decode → raw → custom, plus `parseBatchSubscription`) — behavior preserved, no drift. | No action. |
| 3 | Info (upstream-shared, unchanged) | `updateConfigViaSubAll` | Upstream's fold with `enabled` filter and outer catch is preserved in the fork under the chunk-prefix filter + `onSubStart` callback; containment per-sub stays (v1.2.35). Upstream-identical where fork doesn't extend. | Noted. |
| 4 | CLEAN (v1.2.36 release closure) | CI + signature | Run 29's dispatch verified post-hoc this run: `build.yml` runs 65/66 for `c52baae` both **completed success**; release v1.2.36 published by github-actions[bot] with all 10 APKs; **`tools/verify_sig.py` on the published `Derafsh_1.2.36_arm64-v8a.apk` → cert sha256 `2642E7F0…FD79:F531` = expected fingerprint** (scheme v2). Run 67 (docs commit 1094c63) builds independently, non-gating. | No action. |

### Upstream comparison result
- **Zero upstream drift across all five files.** The fork's entire delta vs upstream HEAD = documented intentional features + the three released fixes (v1.2.33/34/35) + the v1.2.36 atomics. No upstream fix missing; no fork regression introduced. ("Upstream-changed natives" from the Run 29 plan resolved as: UpdateManager is fork-only — no upstream side; all other divergent files re-diffed clean.)

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest, CI-success + signature-verified.

### Next run starting point (Run 31)
- **Remaining ui/ viewmodels (7 small files, 53-153 lines each — closes the ui/ layer):** start with the 3 largest/riskiest: `ui/subscription/SubscriptionsViewModel.kt` (153 — subscription CRUD, URL handling), `ui/apppicker/AppPickerViewModel.kt` (147 — PackageManager scanning), `ui/userasset/UserAssetViewModel.kt` (143 — geo-asset download/IO). Diff each against upstream raw. Run 32 = final sweep of the 4 tiny ones (`logcat/LogcatViewModel` 99, `settings/SettingsViewModel` 73, `checkupdate/CheckUpdateViewModel` 53, `routing/RoutingSettingsViewModel` 51) — after which every Kotlin file with real logic in the app has been audited line-by-line at least once.

---

## Run 31 — 2026-11-05

### Files reviewed (ui/ viewmodels, trio 2 of 2 — the 3 largest remaining)
- **`ui/subscription/SubscriptionsViewModel.kt` (153 lines)** — `subscriptions` mutable snapshot + `_subsFlow`; `remove` (storage cleanup via `SettingsManager.removeSubscriptionWithDefault` + `makeSetupGroupTab` BEFORE the flow update — order correct; MMKV source audited in the handler sweep), `update` (index-guarded cache replace + encode), `shareQRCode`/`dismissQRCode` (prior job cancelled first; QR built on `Dispatchers.Default`; toast after `withContext` returns to Main — safe), `move` (`moveItem` bounds-checked [Run 29] + `encodeSubsList` persistence + `makeSetupGroupTab`), `updateSubscriptions` settings gate (autoTest → background service path; updateOnly → local path), `updateSubscriptionsOnly` (`launchLoading` + `withContext(IO)` on `AngConfigManager.updateConfigViaSubAll` [Run 30: chunk-filtered], toast triage, `makeSetupGroupTab` on configCount>0, `reload()`; CancellationException rethrown), `updateSubscriptionsMore` (enabled+url filter, `MessageHelper` ordered broadcast [Run 24: RESULT_CANCELED=no-daemon path verified], chunk groups excluded).
- **`ui/apppicker/AppPickerViewModel.kt` (147 lines)** — `initialize` one-shot guard; `loadApps` (cache-hit relocalization+resort path; `isAppListLoading` set synchronously on main BEFORE launch — no TOCTOU window, same pattern Run 27 praised in PerAppProxyViewModel; `applicationContext` + localized string resolved on main before the IO hop; IO = `AppManagerUtil.loadNetworkAppList` + UNIDENTIFIED pseudo-item + Collator sort; CancellationException rethrow; `finally` resets the flag), `filterApps`/`toggleApp`/`selectAll`/`invertSelection` (pure StateFlow set ops on main; `AppSelection.invert` verified Run 27), `getSelectedPackages` (sorted() → deterministic persistence), `sortApps` (selected-snapshot-first → non-system-first → Collator; all keys consistent per comparator invocation).
- **`ui/userasset/UserAssetViewModel.kt` (143 lines) + `ui/userasset/UserAssetActivity.kt` (271 lines)** — VM: `reload` (cancel-prior + `Dispatchers.IO`; MMKV decode + `listFiles` on IO; **`ensureActive()` before the state write = stale-write guard** — a cancelled reload can never clobber the newer reload's state), `buildAssetList` (builtin rows get synthetic `builtin:` guids + `locked=true`; geoip-only-cn-private.dat URL force-overridden to the canonical source — documented intentional), `downloadGeoFiles` (per-asset port ladder `httpPort → 0` = proxy-then-direct, first success wins; failures collected), `tryDownload` (fixed `_temp` sibling + `renameTo`). Activity: `downloadGeoFiles()` sets `isLoadingState` synchronously, `refreshData().join()` then `withContext(Dispatchers.IO)` for the blocking download loop (threading correct); `onRemoveAsset` deletes the file + MMKV row + `initAssets()`.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | Info (upstream-shared) | UserAssetActivity:297 (`IconButton(onClick = onDownloadClick)`) + Components.kt AppTopBar | The download IconButton is NOT disabled while `isLoading` (AppTopBar only shows a LinearProgressIndicator): a rapid double-tap starts two concurrent `downloadGeoFiles` runs → both write the SAME fixed `<name>_temp` path via two OkHttp OutputStreams → interleaved bytes → a corrupt geo file can replace a good one (self-heals on the next successful download; routing rules degrade until then). **All three files (Activity, VM, Components.kt) are byte-identical to upstream HEAD — the race is upstream-native design, not fork-introduced.** A one-line `enabled = !isLoading` or a VM-side CAS guard would close it, but per the audit bar (fix only fork regressions; avoid gratuitous upstream divergence) it is recorded, not patched. | Noted. |
| 2 | Info (upstream-shared) | UserAssetViewModel.tryDownload:129 | `targetTemp.renameTo(target)` return value ignored — a failed rename still counts as success (target keeps the OLD file; temp lingers); and on download failure the temp file is not deleted (hygiene; next success overwrites). Upstream-identical (VM byte-identical). | Noted. |
| 3 | Info (upstream-shared) | SubscriptionsViewModel.shareQRCode:66-73 | Cancellation window: if `dismissQRCode()` runs after job1's `withContext` completed but before its `_qrCode.value = bitmap` line, the stale bitmap lands after the clear (dialog could briefly show a QR for a just-dismissed URL). Narrow, upstream-identical (diff shows zero hunks in this function), self-corrects on the next share. | Noted. |
| 4 | Info (benign, upstream-shared) | SubscriptionsViewModel.updateSubscriptionsMore:151 | The "job scheduled" toast fires even when `subIds` is empty (nothing was scheduled). Upstream-identical shape. | Noted. |
| 5 | CLEAN (fork hunk, verified consistent) | SubscriptionsViewModel.updateSubscriptionsMore:139-144 | Fork = upstream + **1 hunk**: chunk groups (`DERAFSH_POOL_CHUNK_PREFIX`) excluded from the background-service update list — mirrors the guard inside `AngConfigManager.updateConfigViaSub` (Run 30): chunk tabs are never fetched directly anywhere. Belt-and-braces consistent. | No action (keep). |
| 6 | CLEAN (positive pattern) | UserAssetViewModel.reload:47 | `ensureActive()` immediately before `_uiState.value = …` — the exact stale-write guard class the audit has been enforcing; cancel-prior + guard makes rapid reloads safe. | No action. |

### Upstream comparison result
- `AppPickerViewModel.kt`, `UserAssetViewModel.kt`, `UserAssetActivity.kt`: **byte-identical to upstream HEAD** (0 hunks each). `SubscriptionsViewModel.kt`: fork = upstream + **1 hunk** (chunk-group exclusion). No missing upstream fixes; no fork regression.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 32)
- **Final ui/ sweep — the 4 tiny viewmodels (53-99 lines):** `ui/logcat/LogcatViewModel.kt` (99 — logcat streaming/IO), `ui/settings/SettingsViewModel.kt` (73), `ui/checkupdate/CheckUpdateViewModel.kt` (53 — update-flow UI, security-adjacent), `ui/routing/RoutingSettingsViewModel.kt` (51). Diff each against upstream raw. **After Run 32, every Kotlin file with real logic in the app has been audited line-by-line at least once** — subsequent runs can move to periodic full-repo re-scans (git-diff-driven: audit whatever changed since the last scan, fork commits AND upstream drift).

---

## Run 32 — 2026-11-05

### Files reviewed (final ui/ sweep — the 4 tiny viewmodels; **ui/ viewmodels layer now 11/11 complete**)
- **`ui/logcat/LogcatViewModel.kt` (99 lines)** — `loadLogcat` (`logcat -d -v time -s GoLog,<pkg>,AndroidRuntime,System.err` dump; buffered `readLines` closed via `use{}`; `.reversed()` = newest-first UI order), `copyLogcat` (filtered join → clipboard → toast), `clearLogcat` (`logcat -c` + `waitFor()`), `filter`/`applyFilter` (main-confined plain vars), `logEntries` derived flow (`map(::createLogcatEntries)` under **`flowOn(Dispatchers.Default)`** — the heavy key-building is off-main; `createLogcatEntries` reads only its parameter; occurrence keys counted oldest-first so keys are stable across filter changes — documented in-code).
- **`ui/checkupdate/CheckUpdateViewModel.kt` (53 lines)** — pre-release toggle persists immediately; `checkForUpdates` → `UpdateCheckerManager.checkForUpdate` (suspend, internal IO + fork's version-compare hardening [Run 22 ledger]) → dialog state or "already latest" toast; catch-all → toastError.
- **`ui/settings/SettingsViewModel.kt` (73 lines)** — `refreshSystemVpnSettingsAvailability` (`resolveActivity` correctly on IO — PackageManager query can be slow); `checkAndRequestRoot` (`RootManager.refresh()` on IO, callback + toast on main after `withContext`); observatory validators (regex / `toIntOrNull` with `> 0` guard, pure).
- **`ui/routing/RoutingSettingsViewModel.kt` (51 lines)** — `reload` (self-heals legacy id-less rulesets: assigns UUID + persists per-index immediately), `update` (bounds-guarded), `move` (`moveItem` bounds-checked [Run 29] + bulk `encodeRoutingRulesets`).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | Info (upstream-shared) | LogcatViewModel.loadLogcat:42 / clearLogcat:65 | Blocking `readLines()` (dump can span hundreds of ms on large buffers) and `logcat -c` `waitFor()` run on the MAIN dispatcher inside `launchLoading` — a jank frame, not a crash; `-d` guarantees the child exits so the missing `process.destroy()` is inert. Upstream-identical (file byte-identical). | Noted. |
| 2 | Info (upstream-shared) | CheckUpdateViewModel.checkForUpdates:43 | `catch (e: Exception)` also swallows CancellationException on VM clear — no rethrow; harmless here (scope is being torn down; toast is applicationContext-safe via ToastExt) but a deviation from the rethrow discipline used elsewhere. Upstream-identical. | Noted. |
| 3 | Info (dead code, upstream-shared) | RoutingSettingsViewModel.reload:24,29 | `needsSave` is assigned but never read (Kotlin warning-grade dead variable; the per-index `saveRoutingRuleset` calls already do the work). Upstream-identical. | Noted. |
| 4 | CLEAN | all 4 files | No fork hunks anywhere: **all four byte-identical to upstream HEAD** (diff exit 0 ×4). Main-confined state everywhere; IO legs properly dispatched; StateFlow writes thread-safe; no null-unsafety, no resource leak (streams closed), no protocol surface. | No action. |

### Upstream comparison result
- **LogcatViewModel / SettingsViewModel / CheckUpdateViewModel / RoutingSettingsViewModel: all byte-identical to upstream HEAD.** No missing upstream fixes; no fork regression. (Note: `ui/routing/` — the Run 31 plan's `ui/routingsettings/` path guess was corrected to `ui/routing/`.)

### Milestone
- **ui/ viewmodels 11/11 complete** (Backup, PerAppProxy, Base [Run 27]; Main part 1+2 [Runs 28-29]; Subscriptions, AppPicker, UserAsset [Run 31]; Logcat, Settings, CheckUpdate, Routing [this run]) — on top of the already-complete `core/`, `service/`, `handler/` (19/19), `fmt/`, `utils/`, and monetization layers.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 33)
- **`ui/server/BaseServerActivity.kt` (510 lines) — the last protocol-adjacent surface with real logic**: profile edit form → `ProfileItem` field mapping (the closest remaining surface to config correctness: missing/wrong field mapping here = broken outbound), persistence + `ProfileEditorResult`/`ProxyChainMembers` contracts, cert/`fetchForManualFill` flow (call-site already cross-checked Run 22: BaseServerActivity:327 under `withContext(Dispatchers.IO)` + UI gate). Diff against upstream raw. Run 34 = `ui/main/MainActivity.kt` (396) full pass; Run 35+ = per-protocol editors (`server/Server*Activity.kt`, 49-506 lines, mostly form→ProfileItem over the BaseServerActivity contract) batched small-first, then switch to git-diff-driven periodic re-scans (fork commits since last scan + upstream drift of the fixed files).

---

## Run 33 — 2026-11-05

### Files reviewed (protocol-adjacent editor hub — `ui/server/`)
- **`ui/server/BaseServerActivity.kt` (510 lines)** — abstract editor base for every per-protocol activity: `onCreate` (`initialConfig = decodeServerConfig(editGuid) ?: ProfileItem.create(type)`), `CommonBasicFields`/`CommonNetworkFields` (network-switched header/mode dropdowns with `size > 1` guard; GRPC→authority/serviceName vs rest→host/path field routing matches xray semantics; KCP seed/mtu/tti instead of path; xhttpExtra + finalMask + browserDialer WS/XHTTP-only), `CommonStreamSecurityFields` (TLS: allowInsecure/alpn/ECH/verifyPeerCertByName/pinnedCA256 + **fetch-cert button: address/port pre-guards, `buildProfileItem()` temp, `scope.launch` + `isFetchingCert` flag with `finally` reset + button disabled while fetching [Run 22 already verified the IO hop + fingerprint manager fail-closed], REALITY: publicKey/shortId/spiderX/mldsa65Verify), `validateBasicConfig` (remarks/address/port; HYSTERIA2 port-exempt), `validateCommonConfig` (password required for VMESS/VLESS/TROJAN/SS/HYSTERIA2 — SOCKS/HTTP/WIREGUARD correctly exempt; TROJAN requires streamSecurity; xhttpExtra/finalMask JSON-parse-gated), `saveServer` (validate ladder → `toProfileItem(initialConfig)` → description → subscriptionId fallback → `encodeServerConfig` → `finishSaved(savedGuid, isRunning)`), `ServerEditorScaffold` (delete button hidden for running/selected server; `deleteServer` double-guard: empty guid OR selected guid → not-allowed toast, defense in depth over the UI gating).
- **`ui/server/ProfileEditorResult.kt` (56 lines)** — pure result-intent contract (`finishSaved` carries `restartService = isRunning`; `finishDeleted` always false).
- **`ui/server/ProxyChainMembers.kt` (14 lines)** — pure helper `withoutProxyChainMember` (resolves the member's position at confirmation; `index < 0` → identity pair).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | BaseServerActivity (all) | Validation ladder is ordered correctly (basic → common → protocol) so state error flags are always set before the first early return; the cert-fetch coroutine's `finally` resets `isFetchingCert` on every path (throw/cancel/success) and `fetchForManualFill` itself is fail-closed (Run 22); delete-of-running-server is blocked twice (UI + `deleteServer` guard); Hysteria2's port exemption is deliberate (UDP form). No null-unsafety, no main-thread IO, no resource leak. | No action. |
| 2 | CLEAN (contracts) | ProfileEditorResult / ProxyChainMembers | Result-intent extras are constants + pure functions; proxy-chain member removal is index-guarded and resolves at confirmation (no stale-index race). | No action. |

### Upstream comparison result
- **`BaseServerActivity.kt`, `ProfileEditorResult.kt`, `ProxyChainMembers.kt`: all byte-identical to upstream HEAD** (diff exit 0 ×3). No fork hunks, no missing upstream fixes.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 34)
- **`ui/main/MainActivity.kt` (396 lines) full pass** — receivers/permission flow/connect entry (`startV2Ray` path), ad entry points already known from Runs 19/22/25 (:131 integrity, :148 appOpen, :160 interstitial, :299 rewarded), activity-result handling of the `ProfileEditorResult` contract (this run's file) — plus `ui/main/MainScreen.kt` wiring spot-checks where MainActivity delegates. Diff against upstream raw. Run 35 = per-protocol editors batch 1: `server/Server*Activity.kt` small files (`ServerSocks`/`ServerHttp`/`ServerTrojan`/`ServerShadowsocks`/`ServerVmess`/`ServerVless`/`ServerHysteria2`/`ServerWireguard`, 49-109 lines each, all form→ProfileItem over this run's verified BaseServerActivity contract). Run 36 = the two big editors (`ServerCustomConfigActivity` 506, `ServerProxyChainActivity` 367, `ServerGroupActivity` 325, `ServerUiState` 258). Then git-diff-driven periodic mode.

---

## Run 34 — 2026-11-05

### Files reviewed (MainActivity full pass + navigation-curation cross-check)
- **`ui/main/MainActivity.kt` (396 lines vs upstream 294, 7 hunks — ALL deliberate Derafsh features, verified line-by-line)**: (1) imports (+AdsManager/IntegrityManager/OnboardingManager/UpdateManager/splash; −7 navigation activities); (2) companion (`wasAppOpenShownThisSession` static — app-open ad once per PROCESS, cold open only; SPLASH_HOLD 3000ms/EXIT 450ms brand constants; `splashVisible`); (3) onCreate — `installSplashScreen` + branded exit animation, **onboarding gate BEFORE everything** (`needsOnboarding` → OnboardingActivity + finish + return — no ad/maintenance/UI runs before consent), `AdsManager.init`, `checkAppIntegrity()` (Run 22 fail-closed, toast-only), Initialize, POST_NOTIFICATIONS, silent `UpdateManager.checkForUpdate()` (Run 19 state machine), splash-hold coroutine (delay → splashVisible=false → app-open once), **interstitial edge-collector** (`wasRunning` initialized BEFORE collect → no missed/mis-fired edge; recreation-while-connected starts wasRunning=true → no fire; boot/auto-start running at first observation → no fire — matches the "never on boot" comment; lifecycleScope cancels on destroy; callback `{ }` empty — Run 25 verified the pending-callback hygiene); (4) `MainAction.ExtendSessionWithAd → handleExtendSession`; (5) `navigateTo` curated to Settings/About; (6) handleFabAction comment (interstitial post-connect — tunnel never ad-gated); (7) `startV2Ray` — auto-pick branch (`markStartRequested(TestingServers)` → `pickFastestServer` [Run 29: callback on Main, VM-scoped → activity destroy drops it, no stale start] → null → classic toast / else `updateSelectedGuid` + `markTunnelStarting` + `startService(this, guid)`); manual branch unchanged + `markStartRequested(EstablishingTunnel)`; ACCESS_LOCAL_NETWORK check retained in both; `handleExtendSession` (rewarded → startV2Ray, closed → DismissSessionTimeUp — Run 25 double-consume pattern).
- **Navigation-curation cross-check**: fork's `MainContract.MainDestination` = {Settings, About} — the 7 upstream destinations removed from BOTH the enum and `navigateTo` (compiles coherently). The 7 screens (SubSetting/PerAppProxy/Routing/Logcat/CheckUpdate/Backup/UserAsset **+ their ViewModels audited Runs 27/31/32**) have **zero launch sites** in fork Kotlin — **deliberate product curation, documented in-code** (`SettingsScreen` KDoc: "Curated settings screen for Derafsh's mainstream audience. Everything technical… intentionally hidden… subscription updates and real-delay tests run automatically every 4 hours, and connect picks the fastest server"). Subscription management is replaced by the built-in source switcher (Run 28/30). All 7 dormant activities remain manifest-declared with **`android:exported="false"`** → inert, no external attack surface; ViewModels stay exercised through the code paths that still exist (e.g. PerAppProxy from assets fallback, SubscriptionsViewModel logic shared with the chunked importer).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN (deliberate, verified) | MainActivity.onCreate interstitial collector | Edge detection is correct: `wasRunning` snapshotted before `collect`, transitions only on false→true, activity recreation can't double-fire, lifecycleScope contains the collector. Never blocks the tunnel (post-connect). | No action. |
| 2 | CLEAN (deliberate, verified) | startV2Ray auto-pick branch | Pick callback on Main with VM scope; failure → honest toast; `updateSelectedGuid` before `startService` so the card shows the winner; both branches keep the permission request and the start-pending phase. | No action. |
| 3 | Info (deliberate design; recorded) | navigateTo + manifest | 7 upstream destinations unreachable from the fork UI (curated product) — screens/VMs remain compiled and manifest-declared with exported=false. If the product ever re-exposes them, the audited ViewModels are ready; until then the surface is inert. | Noted. |
| 4 | Info (deliberate) | companion `wasAppOpenShownThisSession` | Static var survives activity recreation within the process (app-open ad truly once per session) — main-thread-only access (onCreate + lifecycleScope main), no race. | Noted. |

### Upstream comparison result
- Fork = upstream + 7 hunks, every one a documented Derafsh feature (splash/onboarding/integrity/silent-update/ad choreography/curated navigation/connect-phase start). No fork-introduced bug; no missing upstream fix.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 35)
- **Per-protocol editors batch 1 (8 small files, 49-109 lines each, all form→ProfileItem over Run 33's verified BaseServerActivity contract):** `ServerSocksActivity` (49), `ServerHttpActivity` (49), `ServerTrojanActivity` (64), `ServerShadowsocksActivity` (68), `ServerVmessActivity` (72), `ServerVlessActivity` (77), `ServerHysteria2Activity` (109), `ServerWireguardActivity` (84) — diff each against upstream raw, focus on protocol field mapping (the outbound-JSON correctness surface). Run 36 = the big editors: `ServerUiState.kt` (258 — `toProfileItem` field mapping hub), `ServerCustomConfigActivity` (506), `ServerProxyChainActivity` (367), `ServerGroupActivity` (325). Then git-diff-driven periodic mode.

---

## Run 35 — 2026-11-05

### Files reviewed (per-protocol editors batch 1 — 8 small files over Run 33's verified BaseServerActivity contract)
- **`ServerVlessActivity.kt` (77)** — id/encryption/flow fields; `rememberSaveable(ServerUiState.Saver)` survives rotation; validateProtocolConfig re-checks password (redundant with validateCommonConfig's VLESS branch — harmless).
- **`ServerHysteria2Activity.kt` (109)** — id/obfs/portHopping(+interval)/bandwidth/allowInsecure/sni/ech/pinnedCA256/finalMask; **validateProtocolConfig auto-fills `security = TLS` when blank** (Hysteria2 is TLS-always — side-effectful validation, upstream-identical semantics); no CommonNetworkFields/StreamSecurity (UDP/QUIC protocol — matches Run 33's port-exemption finding).
- **`ServerSocksActivity.kt` (49) / `ServerHttpActivity.kt` (49)** — plain proxy forms, no transport/stream-security UI (correct for SOCKS/HTTP).
- **`ServerTrojanActivity.kt` (64) / `ServerShadowsocksActivity.kt` (68) / `ServerVmessActivity.kt` (72)** — full CommonNetworkFields + CommonStreamSecurityFields composition; SS validate = true (password via common layer), Trojan/Vmess protocol checks delegate to the common password gate.
- **`ServerWireguardActivity.kt` (84)** — fork = upstream + **1 cosmetic hunk**: remote-DNS field's `supportingText` hint removed together with the `server_lab_remote_dns_hint` string resource (verified absent in ALL `res/values*/strings.xml` — coherent removal; the `AppConfig.WIREGUARD_LOCAL_REMOTE_DNS` constant itself remains for real config use). No functional difference.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | all 8 editors | Composition per protocol is correct (SOCKS/HTTP bare, TROJAN/SS/VMESS full transport stack, HYSTERIA2/WIREGUARD own fields); every editor defers validation/persistence to the Run 33-verified BaseServerActivity ladder; rotation-safe via `ServerUiState.Saver`; these screens are dormant in the curated UI (Run 34) but remain compiled and correct. | No action. |
| 2 | Info (deliberate) | ServerWireguardActivity:80 | Fork-only cosmetic hint removal (resource-coherent, zero functional impact). | Noted. |

### Upstream comparison result
- **7 of 8 byte-identical to upstream HEAD** (Socks/Http/Trojan/Shadowsocks/Vmess/Vless/Hysteria2). `ServerWireguardActivity` = upstream + 1 cosmetic hunk (hint removal). No fork-introduced bug; no missing upstream fix.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 36)
- **The mapping hub + big editors:** `server/ServerUiState.kt` (258 lines — **`toProfileItem` is the form→ProfileItem field-mapping hub every editor funnels through; the closest remaining surface to outbound-JSON correctness**) first, then `ServerGroupActivity.kt` (325), `ServerProxyChainActivity.kt` (367), `ServerCustomConfigActivity.kt` (506) — diff each against upstream raw. After Run 36 the `ui/` layer is fully covered → **switch to git-diff-driven periodic mode**: each run audits (a) fork commits since the last audit scan (`git log`), (b) upstream drift re-check of the evergreen files (CoreVpnService/UpdateManager/AngConfigManager/MainViewModel + any fork-divergent file), (c) one stale audit-area rotation for depth.

---

## Run 36 — 2026-11-05

### Files reviewed (mapping hub + big editors — closes the `ui/server` package and the whole `ui/` layer)
- **`server/ServerUiState.kt` (258 lines — the form→ProfileItem mapping hub)**: constructor defaults (`reserved="0,0,0"`, WG locals, `headerType="none"`, TCP default); `toProfileItem` (L122-189) verified as a **correct type-gated field router** — VLESS `encryption`→`method` (L138) with exact inverse in `fromProfileItem` (L203, round-trip consistent); `publicKey` dual-use (WireGuard peer key vs REALITY `publicKeyReality`) gated on `isWireguard`/`streamSecurity == REALITY` (L144-148, upstream ProfileItem single-field convention); Hysteria2 fields all gated `isHysteria2`; `nullIfBlank()` hygiene on mode/xhttpMode/serviceName/authority/xhttpExtra/finalMask/seed; `kcpMtu/kcpTti/mtu` via `toIntOrNull()` (null-safe); `browserDialerMode` gated to WS/XHTTP (L172-176); Saver (L249-256) round-trips through `toProfileItem`→JSON→`fromProfileItem` — type-safe across rotation/process death, transient `isFetchingCert` correctly NOT persisted.
- **`ServerGroupActivity.kt` (325 lines, POLICYGROUP editor)**: `isRunning` triple-guard (extra + guid nonempty + selected server); `populateSubscriptionSpinner()` ordered before `initialSubIndex` computation; `initialSubIndex` via `indexOf(...).let { if (it >= 0) it else 0 }` bounds-safe; `initialTestOutbounds` defaults true for new configs and forces true when the strategy lacks observatory support; save: remarks gate → decode-or-create → persist → `finishSaved(guid=savedGuid, restartService=isRunning)` (Run 33 contract); delete: double guard (UI `showDelete = editGuid.isNotEmpty() && !isRunning` + `deleteServer` selected-server block) — same pattern as Run 33; composable resolves dropdown display-strings back to indices with `indexOf(...).coerceAtLeast(0)` (locale-change edge = silent fallback to index 0, upstream-identical behavior).
- **`ServerProxyChainActivity.kt` (367 lines, PROXYCHAIN editor)**: member suggestions exclude CUSTOM/POLICYGROUP/PROXYCHAIN (no nested complex types at suggestion level); save validates in order: remarks → no blank members (`size != members.size` after trim/filter) → ≥2 members → **every member re-resolved via `getServerViaRemarks` at save time and rejected if null or `isComplexType()`** (fail-closed, no stale member references); chain persisted as comma-joined remarks (upstream design — name-based membership, ambiguity on duplicate remarks is upstream-shared); reorderable list uses **parallel UUID `memberKeys`** — add/remove/reorder always mutate both lists together, `moveItem` return checked, `indexOf` == -1 guarded; per-member delete for blank members is immediate via Run 33-verified `withoutProxyChainMember` (key-based → no stale-index race), non-blank members get a confirm dialog keyed by memberKey (not index); `itemsIndexed` key = `memberKeys[index]` — parallel invariant holds on every mutation path.
- **`ServerCustomConfigActivity.kt` (506 lines, raw-JSON editor)**: save is **fail-closed** — `CustomFmt.parse(content)` in try/catch; parse failure → toast (with cause detail when available) → return false → **nothing persisted**; on success: config metadata updated, `encodeServerConfig` returns `savedGuid`, then `encodeServerRaw(savedGuid, content)` — raw keyed to the correct (possibly fresh) guid, order correct; `finishSaved` contract; editor UX: cursor auto-scroll via `snapshotFlow`+`collectLatest` (stale animations cancelled), fully bounds-checked (`textLen==0 || cursor<0 || cursor>textLen` early return, `coerceIn(0,maxValue)`/`coerceAtLeast(0)`/`coerceAtMost(maxValue)` on all scroll targets); line-number canvas derives from measured `TextLayoutResult`; `hasInitializedCursor` `rememberSaveable` prevents re-editing selection after process restore.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | CLEAN | ServerUiState.toProfileItem | Type-gated field routing is internally consistent with its exact inverse (`fromProfileItem`); VLESS encryption↔method and REALITY/WG publicKey dual-use round-trip correctly through the Saver. | No action. |
| 2 | CLEAN | all three big editors | Same guard pattern as Run 33 (double delete-guard, contract-conformant `finishSaved`); proxy-chain membership re-validated at save; custom-config parse failure persists nothing. | No action. |
| 3 | Info (upstream-shared) | ServerProxyChainActivity.saveServer:120-133 | Chain membership is remarks-name-based (`getServerViaRemarks`) — duplicate remarks across profiles resolve to the first match. Inherent upstream design of the chain format (comma-joined names), not a fork deviation. | Noted. |

### Upstream comparison result
- **All 4 files byte-identical to upstream HEAD** (1,456 lines total; diff exit 0 ×4). Zero fork hunks in the entire `ui/server` editor package beyond Run 35's single cosmetic hint removal. No fork-introduced bug; no missing upstream fix.

### Coverage milestone
- **`ui/server` package: COMPLETE** (Run 33 contract layer + Run 35 batch-1 editors + Run 36 hub/big editors). **`ui/` layer: COMPLETE** (activities, viewmodels 11/11, server editors, compose helpers). Remaining un-audited code: none in `ui/`; periodic mode now the primary instrument.

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 37) — git-diff-driven periodic mode begins
- **(a) New-code scan**: `git log --oneline` since the last code-bearing commit (v1.2.36 / Run 29) — expect only audit docs commits; any NEW code commit gets a full line-by-line review before anything else.
- **(b) Evergreen drift re-check vs upstream raw HEAD**: `service/CoreVpnService.kt`, `handler/UpdateManager.kt`, `handler/AngConfigManager.kt`, `ui/main/MainViewModel.kt` (the 4 files where fork deltas live) — confirm the documented Derafsh hunks are still the ONLY deltas and no upstream fix landed that the fork lacks.
- **(c) Depth rotation**: re-verify one audited area end-to-end — start with `fmt/` package (SubscriptionFmt/UserAssetFmt/etc., the parse surface feeding Run 30's subscription ladder) or `dto/entities/ProfileItem.kt` (the field hub this run's mapping writes into).

---

## Run 37 — 2026-11-05 (first git-diff-driven periodic-mode run)

### (a) New-code scan — PASS
- `git log c52baae..HEAD` (c52baae = v1.2.36 code fix, the last code-bearing commit): **7 commits, every one docs-only** (`docs/core-audit.md` is the sole touched path in all). Zero unreviewed code in the tree. Local tags: `checkpoint-v1.2.25` only (releases are CI-tagged remotely; untouched).

### (b) Evergreen drift re-check — PASS, zero drift in BOTH directions
- Method: re-fetched all 4 evergreen files from upstream raw HEAD + proved fork-side immutability with `git diff 5258752..HEAD -- V2rayNG/app/src/main/java/` (Run 30's verified scan commit) → **EMPTY** (no fork code change since the line-by-line verified diff).
- Upstream sizes today == Run 30's recorded sizes exactly: `CoreVpnService` 372 / `AngConfigManager` 657 / `MainViewModel` 969 / `UpdateManager` **404 (still fork-only)** → upstream has not modified any evergreen file since Run 30; the fork cannot be missing an upstream fix.
- Deltas are therefore still exactly the verified documented set: CoreVpnService = v1.2.33 teardown-lock hunk cluster (all 4 raw-diff ranges inside L324-373, one logical change); AngConfigManager/MainViewModel = documented Derafsh feature hunks (raw diff headers 30/40 = finer diff granularity than Run 30's logical-hunk count of 7/23 — same content, proven by both-sides-immutability).

### (c) Depth rotation: `fmt/` package re-verification (10 files, 908 lines) — PASS
- **9 of 10 byte-identical to upstream HEAD**: CustomFmt, FmtBase, Hysteria2Fmt, ShadowsocksFmt, SocksFmt, TrojanFmt, V2rayNFmt, VlessFmt, VmessFmt. Upstream has not touched the parse surface at all since the fork point (any upstream change would have shown as a diff).
- **`WireguardFmt.kt`: exactly its 2 documented Run-10 hunks** (L35 + L86): fork falls back to `AppConfig.WIREGUARD_LOCAL_REMOTE_DNS` (`"1.1.1.1,1.0.0.1,2606:4700:4700::1111,2606:4700:4700::1001"`, the constant Run 1 repaired) where upstream falls back to `""` — deliberate fork default, unchanged since `c8102ac`, Run 10 verdict stands ("NOT a bug; changing it would remove an intentional fork default").

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | PASS (no drift) | evergreen ×4 + fmt ×10 | Both-side immutability proof: fork code frozen since Run 30's verified diff; upstream evergreen + fmt files unchanged since the fork's verified state. The only fork deltas remain the documented feature hunks + WireguardFmt's 2 deliberate defaults. | No action. |

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 38) — periodic mode, rotation continues
- **(a)+(b) unchanged**: new-code scan since c52baae + evergreen drift re-check (now including `fmt/WireguardFmt.kt` in the evergreen set — 5 files total).
- **(c) Depth rotation**: `dto/entities/` package batch diff vs upstream (ProfileItem.kt — the field hub every writer (ServerUiState.toProfileItem, all Fmt parsers, AngConfigManager) funnels through — plus sibling entities), then re-read ProfileItem's volatile-field/copy semantics against Run 29's dedupe finding.

---

## Run 38 — 2026-11-05 (periodic mode)

### (a) New-code scan — PASS
- `git log c52baae..HEAD` non-docs paths: EMPTY (only audit docs commits). Fork code also unchanged since Run 37's scan (`git diff 73d57e2..HEAD -- V2rayNG/app/src/main/java/` empty).

### (b) Evergreen drift re-check (5 files) — PASS, signature unchanged
- Re-fetched upstream raw HEAD: `CoreVpnService` 372 (4 raw hunks) / `UpdateManager` **404 (still fork-only)** / `AngConfigManager` 657 (30 raw hunks) / `MainViewModel` 969 (40 raw hunks) / `WireguardFmt` 132 (2 raw hunks) — **identical signature to Run 37's verified state**; with fork side proven frozen, deltas remain exactly the documented set. Zero drift.

### (c) Depth rotation: `dto/entities/` batch (10 files, 221 lines) + ProfileItem re-read — PASS
- **9 of 10 byte-identical to upstream HEAD**: AssetUrlCache, AssetUrlItem, ProfileItem, RulesetItem, ServersCache, SubscriptionCache, SubscriptionItem, SubscriptionUsageInfo, WebDavConfig.
- **`ServerAffiliationInfo.kt`: the single documented Derafsh hunk** — adds `testedAt: Long = 0L` + explanatory KDoc to upstream's `data class ServerAffiliationInfo(var testDelayMillis: Long = 0L)`. This is the storage side of the test-freshness feature (`DERAFSH_TEST_FRESH_MS`), already documented in the MmkvManager run ("`encodeServerTestResult`+`testedAt` (Derafsh)", commit `c70e40d`). Deliberate feature, not a bug.
- **`ProfileItem.kt` (113) re-read against its two verified consumers**: `duplicateIdentity()` (L105-112) neutralizes exactly the 5 non-connection metadata fields via `copy()` — data-class `equals`/`hashCode` on the copy therefore covers configType + every connection-affecting field (policyGroup*/proxyChainProfiles correctly INCLUDED — groups differing in behavior are not duplicates); copy is never mutated after insertion into Run 29's dedupe `HashSet` (KDoc contract "caller must not modify" honored). `getServerAddressAndPort()` (L84-89): CUSTOM-empty-server → loopback:PORT_SOCKS display, bare-IPv6 bracketing via `Utils.getIpv6Address`, port always present post-validation. `copy()`-preservation is what lets Run 36's editors keep `subscriptionId`/`policyGroup*`/`proxyChainProfiles` while overwriting form fields. `@Deprecated pinSHA256` retained for legacy-JSON round-trip. No null-unsafety found beyond upstream-shared interpolation edge (`serverPort` null → "null" display only pre-validation).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | PASS (no drift) | evergreen ×5 | Same verified signature as Run 37; both sides frozen. | No action. |
| 2 | CLEAN (deliberate) | ServerAffiliationInfo.kt:3-14 | `testedAt` freshness field = documented Derafsh feature (commit `c70e40d`); upstream byte-diff is exactly this one hunk. | No action. |
| 3 | CLEAN (contracts) | ProfileItem.kt:105-112 | Dedupe identity copy semantics correct; consumer contracts (Run 29 dedupe, Run 36 editors' copy-preservation) hold. | No action. |

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 39) — periodic mode, rotation continues
- **(a)+(b) unchanged**: new-code scan since c52baae + evergreen drift x5.
- **(c) Depth rotation**: **`core/` package batch re-verification** — the task's priority surface: `CoreOutboundBuilder.kt` (outbound JSON correctness: VLESS/Trojan/SS/Hysteria2/TUIC, Reality, XTLS-Vision, headers), `CoreConfigContextBuilder.kt`, `CoreServiceManager.kt`, `LauncherManager.kt`, `CoreConfigManager.kt`, `CoreNativeManager.kt` — batch diff each vs upstream raw HEAD, then spot re-read the Reality/XTLS field-emission paths (where Run 2's DNS-leak fix and Run 5's WG outbound block live).

---

## Run 39 — 2026-11-05 (periodic mode; core/ depth rotation — the task's priority surface)

### (a) New-code scan — PASS
- `git log c52baae..HEAD` non-docs paths: EMPTY. Fork code frozen since Run 38's scan (`git diff ef6883a..HEAD -- V2rayNG/app/src/main/java/` empty).

### (b) Evergreen drift re-check (5 files) — PASS
- Signature identical to Run 37/38: CoreVpnService 372 (4) / UpdateManager **404 (fork-only)** / AngConfigManager 657 (30) / MainViewModel 969 (40) / WireguardFmt 132 (2). Zero drift.

### (c) Depth rotation: `core/` package (6 files) — PASS, every delta documented
- **Byte-identical to upstream HEAD (3)**: `CoreConfigContextBuilder.kt` (273), `LauncherManager.kt` (141), `CoreNativeManager.kt` (99).
- **`CoreOutboundBuilder.kt` (upstream 711): 2 raw hunks, both cosmetic** — (1) the fork's 5-line comment documenting the v1.2.26 WG remote-DNS split fix; (2) whitespace-only (`contains(":") }` vs `contains(":")}`). **All protocol-JSON emission code is upstream-identical.**
- **`CoreServiceManager.kt` (upstream 521): 6 raw hunks, 1 logical feature** — the complete Derafsh **session timer** (`sessionTimerScope`/`sessionTimerJob` :daemon-scoped; `armSessionTimer` on core-loop start, `cancelSessionTimer` on manual stop + cancel-comment, full arm/expire/cancel block): 30-min deadline persisted via `PREF_SESSION_DEADLINE` for UI countdown, rewarded-ad re-arm opens a fresh window, `isReload` handover intentionally untouched, expiry notifies UI BEFORE `serviceControl.stopService()` teardown (no dialog/broadcast race), CancellationException rethrown, every failure logged not thrown. The exact feature Run 25/34 verified from the UI side — now re-verified from the daemon side.
- **`CoreConfigManager.kt` (upstream 1321): 5 raw hunks, 2 logical features** — (1) `buildDnsIranModeFromRoutingRules` (Iran counterpart of CN expectIPs mode: direct-routed `geosite:category-ir`/`domain:ir` resolved by domestic servers with `expectIPs geoip:ir`, `skipFallback=true`, graceful empty fallback) — documented Derafsh Iran feature; (2) the two "(sync upstream #6376)" comments marking the v1.2.27 DNS-leak fix (registration order + finalQuery) — comments only.
- **Reality emission spot re-read** (`populateTlsSettings` L564-603): pin-supersedes-insecure (L566 `allowInsecure = insecure==true && pinnedCA256 blank`); SNI ladder profile→sniExt(domain)→server(domain)→sniExt; security `nullIfBlank` + early return (no TLS block without security); one `TlsSettingsBean` shared by TLS/REALITY with per-field `nullIfBlank` (Gson omits nulls → Reality keys absent from plain-TLS JSON — Run 8 contract); TLS/REALITY **mutual exclusion with explicit nulling** (L592-598); auto-fragment only when `finalMask` absent (L600-602, finalMask parse fail-safe keeps previous fragment).
- **XTLS-Vision/mux spot re-read** (L58-79): XHTTP network disables mux; VLESS with non-empty flow (Vision) forces `mux.concurrency = -1` (Xray requirement — Vision unmultiplexed); mux pref parse failure → catch → `return false` (fail-closed).

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | PASS (no drift) | core/ ×6 + evergreen ×5 | 3 core files byte-identical; the other 3 carry only documented Derafsh features (session timer, Iran DNS) + fix comments + 1 whitespace diff. Protocol-JSON paths upstream-identical. | No action. |
| 2 | CLEAN (re-read) | CoreOutboundBuilder L564-603, L58-79 | Reality/XTLS emission semantics re-verified: pin-supersedes-insecure, mutual TLS/REALITY exclusion, Vision-mux conflict handled per Xray spec. | No action. |

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 40) — periodic mode, rotation continues
- **(a)+(b) unchanged**: new-code scan since c52baae + evergreen drift x5.
- **(c) Depth rotation**: `handler/` second-pass rotation — `MmkvManager.kt` + `SettingsManager.kt` batch diff vs upstream raw HEAD (the storage/settings layer under everything; MmkvManager's documented 29 Derafsh diff lines and SettingsManager's IO-hop paths), then spot re-read `encodeServerConfig`/`decodeServerConfig` round-trip against the Run 36 editors' persistence path.

---

## Run 40 — 2026-11-05 (periodic mode; handler/ storage-layer rotation)

### (a) New-code scan — PASS
- `git log c52baae..HEAD` non-docs paths: EMPTY. Fork code frozen since Run 39's scan.

### (b) Evergreen drift re-check (5 files) — PASS
- Signature identical to Run 37/38/39: CoreVpnService 372 (4) / UpdateManager **404 (fork-only)** / AngConfigManager 657 (30) / MainViewModel 969 (40) / WireguardFmt 132 (2). Zero drift.

### (c) Depth rotation: `MmkvManager.kt` (fork 1175 / upstream 1053) + `SettingsManager.kt` (fork 682 / upstream 582) — PASS, every delta documented
- **MmkvManager (13 raw hunks, all documented Derafsh features, zero missing upstream content)**: (1) cross-process `withSubscriptionUpdateLock` — dedicated MULTI_PROCESS MMKV instance used purely for its file lock (stores nothing), serializing :tasks/WorkManager vs main-process refresh mutation phases; KDoc documents non-nesting + never-under-profileIndexLock invariants; (2) `KEY_POOL_GENERATION` + `decodePoolGeneration`/`encodePoolGeneration` — pool-generation guid sets for exact retirement on refresh; (3) `commitProfiles` reorder — fork computes `serverList` + `replacementSelection` BEFORE the commit with the **selection-preservation guard** (`previousSelection in serverList → skip findSelectedReplacement`) preventing silent re-anchor onto an unrelated first-profile (v1.2.35 re-anchor-guard family); (4) `serverRawStorage.remove(guid)` additions in the removal paths (raw payloads die with profiles); (5) `removeServerProfiles(guids)` — subscription-unscoped retirement used by pool refresh; (6) `testedAt` stamping — `encodeServerTestDelayMillis` → `encodeServerTestResult(guid, result, testedAt)` + aff writes (the Run 38 ServerAffiliationInfo feature's storage side).
- **SettingsManager (6 raw hunks, all documented)**: (1) `seedDefaultSubscription` replaces upstream's commented-out `ensureDefaultSubscription()` — one-time seeding of the built-in Derafsh source subscription + 3 migrations (legacy URL → active source, 6h→4h cadence, single-source pool dissolution, chunk-namespacing dissolution) — the documented built-in source switcher (Runs 28/30/34); (2) Iran whitelist preset seeding on fresh installs + one-time China→Iran preset migration (only when user never customized) — documented Iran routing feature; (3) chunk-group cleanup on subscription delete (`DERAFSH_POOL_CHUNK_PREFIX + subid_` children cancelled + removed with their parent) — pairs with the Run 30/31 belt-and-braces chunk guards.
- **encodeServerConfig/decodeServerConfig round-trip re-read (L285-332)**: `decodeServerConfig` = blank-guid/json → null, `fromJsonSafe` (malformed JSON → null, never throws) — feeds the Run 36 decode-or-create editors safely. `encodeServerConfig` = `guid.ifBlank { Utils.getUuid() }` **returns the same key written** (fresh guid for new profiles — the exact contract ServerCustomConfig/Group/ProxyChain editors' `savedGuid` rely on); whole mutation under `withProfileIndexLock`; `requireStorageWrite` on both payload encode and index publish (MMKV failure throws, never silently drops); `getSubscriptionId` anchors no-sub profiles to the default subscription (never orphaned); index add idempotent, new servers at position 0, auto-select when selection empty. Round-trip with Run 33/36 editor contracts verified end-to-end.

### Findings

| # | Severity | Location | Finding | Action |
|---|---|---|---|---|
| 1 | PASS (no drift) | handler/ ×2 + evergreen ×5 | 19 raw hunks across both files, every one a documented Derafsh feature or its storage side; no upstream-only content anywhere in either diff. | No action. |
| 2 | CLEAN (round-trip) | MmkvManager L285-332 | encode/decode contract fail-safe on every path (null-safe reads, verified writes, lock-serialized index, guid-ifBlank-fresh-uuid, default-sub anchoring, auto-select). | No action. |

### Release
- None this run (no code changes; docs-only commit). v1.2.36 (versionCode 40) remains latest.

### Next run starting point (Run 41) — periodic mode, rotation continues
- **(a)+(b) unchanged**: new-code scan since c52baae + evergreen drift x5.
- **(c) Depth rotation**: `utils/` + `extension/` batch re-verification (Utils.kt / JsonUtil.kt / MessageHelper / ListExt / nullIfBlank / ProfileReplacement.kt — the shared primitives every audited layer calls; ProfileReplacement's findSelectedReplacement was re-anchored by the v1.2.35 fixes and deserves a fresh diff), then spot re-read `Utils.parse`/base64 decode ladder feeding Run 30's subscription parse path.
