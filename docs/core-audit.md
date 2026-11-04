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
