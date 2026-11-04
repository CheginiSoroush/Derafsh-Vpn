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
