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
