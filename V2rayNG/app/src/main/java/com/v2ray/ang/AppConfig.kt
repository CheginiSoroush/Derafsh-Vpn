package com.v2ray.ang


object AppConfig {

    /** The application's package name. */
    const val ANG_PACKAGE = BuildConfig.APPLICATION_ID
    const val TAG = BuildConfig.APPLICATION_ID

    /** Directory names used in the app's file system. */
    const val DIR_ASSETS = "assets"

    const val WEBDAV_BACKUP_DIR = "backups"
    const val WEBDAV_BACKUP_FILE_NAME = "backup_ng.zip"

    // WebDAV automatic daily backup (WorkManager periodic task)
    const val WEBDAV_AUTO_BACKUP_TASK_NAME = "WEBDAV_AUTO_BACKUP"
    const val PREF_WEBDAV_AUTO_BACKUP = "PREF_WEBDAV_AUTO_BACKUP"
    const val PREF_WEBDAV_LAST_AUTO_BACKUP = "PREF_WEBDAV_LAST_AUTO_BACKUP"
    const val PREF_WEBDAV_LAST_AUTO_BACKUP_SUCCESS = "PREF_WEBDAV_LAST_AUTO_BACKUP_SUCCESS"

    /** Legacy configuration keys. */
    const val ANG_CONFIG = "ang_config"

    // Default subscription ID for ungrouped servers
    const val DEFAULT_SUBSCRIPTION_ID = "__default_subscription__"

    /** Preferences mapped to MMKV storage. */
    const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
    const val PREF_ROUTE_ONLY_ENABLED = "pref_route_only_enabled"
    const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
    const val PREF_PER_APP_PROXY_SET = "pref_per_app_proxy_set"
    const val PREF_BYPASS_APPS = "pref_bypass_apps"
    const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
    const val PREF_FAKE_DNS_ENABLED = "pref_fake_dns_enabled"
    const val PREF_APPEND_HTTP_PROXY = "pref_append_http_proxy"
    const val PREF_LOCAL_DNS_PORT = "pref_local_dns_port"
    const val PREF_VPN_DNS = "pref_vpn_dns"
    const val PREF_VPN_BYPASS_LAN = "pref_vpn_bypass_lan"
    const val PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX = "pref_vpn_interface_address_config_index"
    const val PREF_VPN_MTU = "pref_vpn_mtu"
    const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
    const val PREF_ROUTING_RULESET = "pref_routing_ruleset"
    const val PREF_MUX_ENABLED = "pref_mux_enabled"
    const val PREF_MUX_CONCURRENCY = "pref_mux_concurrency"
    const val PREF_MUX_XUDP_CONCURRENCY = "pref_mux_xudp_concurrency"
    const val PREF_MUX_XUDP_QUIC = "pref_mux_xudp_quic"
    const val PREF_FRAGMENT_ENABLED = "pref_fragment_enabled"
    const val PREF_FRAGMENT_PACKETS = "pref_fragment_packets"
    const val PREF_FRAGMENT_LENGTH = "pref_fragment_length"
    const val PREF_FRAGMENT_INTERVAL = "pref_fragment_interval"
    const val PREF_FRAGMENT_MAXSPLIT = "pref_fragment_maxsplit"
    const val PREF_OBSERVATORY_LEAST_PING_INTERVAL = "pref_observatory_least_ping_interval"
    const val PREF_OBSERVATORY_LEAST_LOAD_INTERVAL = "pref_observatory_least_load_interval"
    const val PREF_OBSERVATORY_LEAST_LOAD_METHOD = "pref_observatory_least_load_method"
    const val PREF_OBSERVATORY_LEAST_LOAD_SAMPLING = "pref_observatory_least_load_sampling"
    const val PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT = "pref_observatory_least_load_timeout"
    const val SUBSCRIPTION_UPDATE_TASK_NAME = "subscription_updater"
    const val SUBSCRIPTION_MIN_INTERVAL_MINUTES = 15L
    const val PREF_SPEED_ENABLED = "pref_speed_enabled"
    const val PREF_CONFIRM_REMOVE = "pref_confirm_remove"
    const val PREF_DOUBLE_COLUMN_DISPLAY = "pref_double_column_display"
    const val PREF_GROUP_ALL_DISPLAY = "pref_group_all_display"
    const val PREF_LANGUAGE = "pref_language"
    const val PREF_APP_LOCALE_MIGRATED = "pref_app_locale_migrated"
    const val PREF_UI_MODE_NIGHT = "pref_ui_mode_night"
    const val PREF_DYNAMIC_COLOR = "pref_dynamic_color"
    const val PREF_IPV6_ENABLED = "pref_ipv6_enabled"
    const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
    const val PREF_ENABLE_LOCAL_PROXY = "pref_enable_local_proxy"
    const val PREF_SOCKS_PORT = "pref_socks_port"
    const val PREF_DYNAMIC_SOCKS_PORT = "pref_dynamic_socks_port"
    const val PREF_SOCKS_USERNAME = "pref_socks_username"
    const val PREF_SOCKS_PASSWORD = "pref_socks_password"
    const val PREF_SOCKS_ENABLE_UDP = "pref_socks_enable_udp"
    const val PREF_REMOTE_DNS = "pref_remote_dns"
    const val PREF_DOMESTIC_DNS = "pref_domestic_dns"
    const val PREF_DNS_HOSTS = "pref_dns_hosts"
    const val PREF_DELAY_TEST_URL = "pref_delay_test_url"
    const val PREF_IP_API_URL = "pref_ip_api_url"
    const val PREF_LOGLEVEL = "pref_core_loglevel"
    const val PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD = "pref_outbound_domain_resolve_method"
    const val PREF_MODE = "pref_mode"
    const val PREF_ROOT_MODE_ENABLE = "pref_root_mode_enabled"
    const val PREF_ROOT_LAN_SHARING = "pref_root_lan_sharing"
    const val PREF_IS_BOOTED = "pref_is_booted"
    const val PREF_CHECK_UPDATE_PRE_RELEASE = "pref_check_update_pre_release"
    const val PREF_GEO_FILES_SOURCES = "pref_geo_files_sources"
    const val PREF_USE_HEV_TUNNEL = "pref_use_hev_tunnel_v2"
    const val PREF_HEV_TUNNEL_LOGLEVEL = "pref_hev_tunnel_loglevel"
    const val PREF_HEV_TUNNEL_RW_TIMEOUT = "pref_hev_tunnel_rw_timeout_v2"
    const val PREF_UPDATE_SUBSCRIPTION = "pref_update_subscription"
    const val PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION = "pref_auto_test_after_update_subscription"
    const val PREF_AUTO_REMOVE_INVALID_AFTER_TEST = "pref_auto_remove_invalid_after_test"
    const val PREF_AUTO_SORT_AFTER_TEST = "pref_auto_sort_after_test"
    const val PREF_REAL_PING_CONCURRENCY = "pref_real_ping_concurrency"

    /** Derafsh monetization keys. */
    // One-tap connect tests every profile and picks the fastest one when enabled (no UI toggle).
    const val PREF_AUTO_PICK_FASTEST = "pref_auto_pick_fastest"
    // Daemon-enforced session deadline persisted as epoch millis (0 = no active session), so the
    // UI process can render the countdown straight from multiprocess MMKV.
    const val PREF_SESSION_DEADLINE = "pref_session_deadline"

    /** Derafsh stale-profile tracking: consecutive real-delay test failures per GUID (JSON map,
     *  capped) and the derived set of stale GUIDs. Mark-only — nothing is ever deleted here. */
    const val PREF_GUID_FAIL_STREAK = "pref_guid_fail_streak"
    const val PREF_STALE_GUID_SET = "pref_stale_guid_set"
    // Server-picker display filter only: show (default) or hide stale rows. Never removes data.
    const val PREF_SHOW_STALE_PROFILES = "pref_show_stale_profiles"

    /**
     * Derafsh auto-maintenance: silent subscription refresh cadence (4 hours). The real-delay
     * speed test itself runs on EVERY app open and is announced on the main-screen card.
     */
    const val PREF_LAST_AUTO_MAINTENANCE = "pref_last_auto_maintenance"
    const val AUTO_MAINTENANCE_INTERVAL_MS = 4L * 60 * 60 * 1000

    /** Derafsh session limit enforced by the daemon process. 30 minutes keeps the
     *  rewarded-ad extension a natural, frequent part of the monetization loop. */
    const val SESSION_DURATION_MS = 30L * 60L * 1000L

    /** Derafsh first-launch onboarding: intro carousel + privacy-policy consent.
     *  Consent is re-collected automatically whenever the stored script version falls
     *  behind OnboardingManager.CONSENT_VERSION. */
    const val PREF_ONBOARDING_DONE = "pref_onboarding_done"
    const val PREF_POLICY_CONSENT_VERSION = "pref_policy_consent_version"

    /** Derafsh premium license (LicenseManager; premium-design.md §6). */
    // License API base URL — production Cloudflare Worker behind the custom domain
    // imsoroush.top (workers.dev is filtered in Iran). Contract: premium-design.md §5
    // (/health, /v1/activate, /v1/status, /v1/sub/<code>?device=).
    const val LICENSE_API_BASE = "https://imsoroush.top"
    // Max offline window after the last successful server confirmation inside which premium
    // stays unlocked. Reference time is max(device time, last server-confirmed time), so a
    // backward clock shift cannot extend a license; a forward shift can only lock it early.
    const val LICENSE_GRACE_MS = 72L * 60 * 60 * 1000
    // Periodic server re-validation cadence (design §7.3); also the staleness threshold for
    // the "running on grace" UI hint in the license screens.
    const val LICENSE_REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000
    // Cached Device ID (canonical, dash-less): SHA-256(ANDROID_ID).take(16) hex upper-cased.
    const val PREF_DERAFSH_DEVICE_ID = "pref_derafsh_device_id"
    // Stored license: user-entered code (canonical), server expiry, last server-confirmed
    // time (epoch millis) and the device the license is bound to.
    const val PREF_LICENSE_CODE = "pref_license_code"
    const val PREF_LICENSE_EXPIRES = "pref_license_expires"
    const val PREF_LICENSE_LAST_OK = "pref_license_last_ok"
    const val PREF_LICENSE_DEVICE = "pref_license_device"
    // Telegram bot that sells subscriptions and issues activation codes (@PDerafshVpnbot,
    // live webhook on the license worker). The UI hides the bot entry points while blank.
    const val DERAFSH_BOT_URL = "https://t.me/PDerafshVpnbot"
    // Renewal reminder (task 7): once the remaining license window drops below this
    // threshold, the :bg worker posts a renewal notification and the main screen shows
    // a renewal banner; both clear again as soon as the license is renewed or revoked.
    const val LICENSE_REMIND_MS = 3L * 24 * 60 * 60 * 1000
    // Unique periodic-work name for the LicenseReminder WorkManager task (24h cadence;
    // also performs the design §7.3 background re-validation of the stored license).
    const val LICENSE_REMINDER_TASK_NAME = "derafsh_license_reminder"

    /** Cache keys. */
    const val CACHE_SUBSCRIPTION_ID = "cache_subscription_id"

    /** Protocol identifiers. */
    const val PROTOCOL_FREEDOM = "freedom"

    /** Broadcast actions. */
    const val BROADCAST_ACTION_SERVICE = "$ANG_PACKAGE.action.service"
    const val BROADCAST_ACTION_ACTIVITY = "$ANG_PACKAGE.action.activity"
    const val BROADCAST_ACTION_WIDGET_CLICK = "$ANG_PACKAGE.action.widget.click"

    /** Tasker extras. */
    const val TASKER_EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val TASKER_EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val TASKER_EXTRA_BUNDLE_SWITCH = "tasker_extra_bundle_switch"
    const val TASKER_EXTRA_BUNDLE_GUID = "tasker_extra_bundle_guid"
    const val TASKER_DEFAULT_GUID = "Default"

    /** Tags for different proxy modes. */
    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"
    const val TAG_FRAGMENT = "fragment"
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"
    const val TAG_BALANCER = "balancer-main"
    const val TAG_BALANCER_PRE = "balancer"

    /** Network-related constants. */
    const val UPLINK = "uplink"
    const val DOWNLINK = "downlink"

    /** URLs for various resources. */
    const val GITHUB_URL = "https://github.com"
    const val GITHUB_RAW_URL = "https://raw.githubusercontent.com"
    const val GITHUB_DOWNLOAD_URL = "$GITHUB_URL/%s/releases/latest/download"
    const val ANDROID_PACKAGE_NAME_LIST_URL = "$GITHUB_RAW_URL/2dust/androidpackagenamelist/master/proxy.txt"
    const val APP_URL = "$GITHUB_URL/CheginiSoroush/Derafsh-Vpn"
    const val APP_API_URL = "https://api.github.com/repos/CheginiSoroush/Derafsh-Vpn/releases"
    const val APP_ISSUES_URL = "$APP_URL/issues"
    const val APP_WIKI_MODE = "$APP_URL/wiki/Mode"
    const val APP_PRIVACY_POLICY = "$GITHUB_RAW_URL/CheginiSoroush/Derafsh-Vpn/main/PRIVACY.md"
    const val APP_PROMOTION_URL = "aHR0cHM6Ly9naXRodWIuY29tL1Nvcm91c2hjcS9EZXJhZnNoLVZwbg=="
    const val TG_CHANNEL_URL = "https://github.com/CheginiSoroush/Derafsh-Vpn"
    const val DEV_PROFILE_URL = "$GITHUB_URL/CheginiSoroush"

    /** Official release signing certificate SHA-256, used by IntegrityManager (anti-tamper).
     *  Must match the release keystore (derafsh-release.p12) — verify with: python3 tools/verify_sig.py <apk> */
    const val RELEASE_CERT_SHA256 = "2642E7F0EE52AD5F179D756D5F58CDF7DF5B08577301BD13C8D05382FD79F531"

    /** Latest release metadata used by the in-app auto-updater (first entry = newest, includes prereleases). */
    const val DERAFSH_UPDATE_API_URL = "$APP_API_URL?per_page=1"

    /** Rate-limit-proof self-update fallback (used when the API above fails, e.g. the
     *  60 req/hour/IP unauthenticated quota exhausted behind carrier-grade NAT): the
     *  releases atom feed for the newest tag, then the per-tag expanded_assets fragment
     *  for APK download paths. Both live on github.com and carry no API quota. */
    const val DERAFSH_UPDATE_FEED_URL = "$APP_URL/releases.atom"
    const val DERAFSH_UPDATE_ASSETS_URL = "$APP_URL/releases/expanded_assets"

    /**
     * Legacy single built-in source (primary of the built-in sources below; also used for
     * migration of old installs).
     */
    const val DERAFSH_SUB_URL = "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt"

    /** Previous built-in sub URL; existing installs are migrated from it on startup. */
    const val DERAFSH_LEGACY_SUB_URL = "https://raw.githubusercontent.com/CheginiSoroush/Derafsh-Vpn/sub/sub.txt"
    const val PREF_DEFAULT_SUB_SEEDED = "pref_default_sub_seeded"

    /** Persisted index (as string) of the active built-in source in [DERAFSH_SOURCE_URLS]. */
    const val PREF_DERAFSH_ACTIVE_SOURCE = "pref_derafsh_active_source"

    /** "1" while the built-in subscription points at the locked premium pool (task 9). */
    const val PREF_DERAFSH_PREMIUM_SOURCE = "pref_derafsh_premium_source"

    /** Max configs per built-in pool group; bigger sources split into "(k)" overflow groups. */
    const val DERAFSH_POOL_CHUNK_SIZE = 200

    /** subId prefix of the overflow chunk groups carved out of any subscription with more
     *  than [DERAFSH_POOL_CHUNK_SIZE] configs; group ids are namespaced per primary
     *  subscription (see [AngConfigManager.chunkGroupId]). */
    const val DERAFSH_POOL_CHUNK_PREFIX = "__derafsh_chunk_"

    /** One-time migration: dissolve overflow chunk groups created with the old, non-
     *  namespaced chunk ids so they are rebuilt under the per-primary naming. */
    const val PREF_DERAFSH_CHUNK_NAMESPACED = "pref_derafsh_chunk_namespaced"

    /** How long a stored speed-test result counts as fresh. Auto test passes (startup,
     *  source switch, subscription refresh) skip configs whose result is younger than
     *  this window and only test never-tested or stale ones, so switching sources or
     *  reopening the app never re-tests the whole pool from scratch. */
    const val DERAFSH_TEST_FRESH_MS = 12L * 60 * 60 * 1000

    /** One-time migration: collapse the old multi-pool seeding into the single active source. */
    const val PREF_DERAFSH_SINGLE_SOURCE_MIGRATED = "pref_derafsh_single_source_migrated"

    /** One-time migration: China-whitelist routing preset → Iran whitelist. */
    const val PREF_ROUTING_IRAN_MIGRATED = "pref_routing_iran_migrated"

    /**
     * Maintenance status (subscription refresh + real-ping speed test) published by the
     * ":tasks" process (SubscriptionUpdateService) through the multi-process MMKV store
     * and polled by the main screen. Values are plain strings/ints encoded as strings.
     */
    const val PREF_MAINT_PHASE = "pref_maint_phase"
    const val PREF_MAINT_SUB_LABEL = "pref_maint_sub_label"
    const val PREF_MAINT_DONE = "pref_maint_done"
    const val PREF_MAINT_TOTAL = "pref_maint_total"
    const val PREF_MAINT_DONE_AT = "pref_maint_done_at"
    const val MAINT_PHASE_IDLE = "idle"
    const val MAINT_PHASE_UPDATING = "updating"
    const val MAINT_PHASE_TESTING = "testing"
    const val MAINT_PHASE_DONE = "done"

    /**
     * Built-in source switcher pool: free public config feeds, all frequently refreshed
     * (≈15 min upstream) and friendly to Iranian networks. Presented to the user as neutral
     * numbered entries ("Derafsh 1", "Derafsh 2", …) — never by URL. Exactly ONE entry (the
     * active source) is materialized as a subscription and auto-updated every 4 hours;
     * switching rewrites the built-in subscription's URL and refreshes immediately.
     * Every URL below was validated live (HTTP 200 + ≥15 parseable configs) before shipping.
     *
     * Round-3 expansion (v1.2.37): +175 sources aggregated from the 10ium free-sub-link
     * directory (https://10ium.github.io/free-sub-link/) — Xray-format feeds only (plain URI
     * lists + base64). Each was validated live before shipping: HTTP 200, ≥5 parseable
     * configs, ≤3 MB payload and ≤8000 configs per pool (the chunk mechanism keeps big-pool
     * imports bounded). Clash/Mihomo YAML feeds, DNS-only configs and DNS lists from the same
     * page are intentionally NOT included — the Xray core cannot parse them; dead links
     * (HTTP 404/empty pools at validation time) are excluded too. Entries are appended AFTER
     * the pre-existing ones so persisted active-source indices stay stable across upgrades.
     */
    val DERAFSH_SOURCE_URLS: List<String> = listOf(
        DERAFSH_SUB_URL, // 0xRadikal "top100" — health-checked top 100 (default pool)
        "https://raw.githubusercontent.com/MhdiTaheri/V2rayCollector_Py/HEAD/sub/Mix/mix.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/HEAD/server.txt",
        "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/HEAD/subscriptions/v2ray/all_sub.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/HEAD/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/mrvcoder/V2rayCollector/HEAD/mixed_iran.txt",
        "https://raw.githubusercontent.com/Farid-Karimi/Config-Collector/HEAD/mixed_iran.txt",
        "https://raw.githubusercontent.com/youfoundamin/V2rayCollector/HEAD/mixed_iran.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub1.txt",
        "https://raw.githubusercontent.com/itsyebekhe/PSG/HEAD/subscriptions/xray/mix",
        "https://raw.githubusercontent.com/wuqb2i4f/xray-config-toolkit/HEAD/output/base64/mix",
        "https://raw.githubusercontent.com/F0rc3Run/F0rc3Run/HEAD/Best-Results/sub.txt",
        "https://raw.githubusercontent.com/icho53/TelegramV2rayCollector/HEAD/sub/mix",
        "https://raw.githubusercontent.com/hamedp-71/Sub_Checker_Creator/HEAD/final.txt",
        "https://raw.githubusercontent.com/hans-thomas/v2ray-subscription/HEAD/servers.txt",
        // Round-2 pool expansion: each URL re-validated live (HTTP 200 + ≥15 parseable
        // configs, ≤2000 configs and ≤600 KB per pool to keep switch-time imports light).
        "https://raw.githubusercontent.com/MhdiTaheri/V2rayCollector/HEAD/sub/mix",
        "https://raw.githubusercontent.com/hamedp-71/Sub_Checker_Creator/HEAD/vless.txt",
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Splitted-By-Protocol/vmess.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Splitted-By-Protocol/trojan.txt",
        // ---- Round-3: 10ium free-sub-link aggregation (indices 20+; validated live, see above) ----
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/normal/ss", // [10ium:HiNVPN ss] 135cfg/20KB
        "https://raw.githubusercontent.com/10ium/multi-proxy-config-fetcher/refs/heads/main/configs/proxy_configs.txt", // [10ium:config fetcher] 319cfg/95KB
        "https://raw.githubusercontent.com/10ium/V2RayAggregator/refs/heads/master/Eternity.txt", // [10ium:Aggregator] 200cfg/34KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Normal/reality", // [10ium:V2Hub reality] 529cfg/153KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/normal/trojan", // [10ium:HiNVPN trojan] 91cfg/25KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/merged", // [10ium:V2Hub merged] 1895cfg/503KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/normal/vmess", // [10ium:HiNVPN vmess] 51cfg/17KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/normal/vless", // [10ium:HiNVPN vless] 467cfg/140KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Normal/vmess", // [10ium:V2Hub vmess] 116cfg/48KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Normal/shadowsocks", // [10ium:V2Hub shadowsocks] 271cfg/38KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Normal/trojan", // [10ium:V2Hub trojan] 338cfg/79KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Normal/vless", // [10ium:V2Hub vless] 1170cfg/336KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/normal/mix", // [10ium:HiNVPN mix] 732cfg/201KB
        "https://raw.githubusercontent.com/10ium/V2rayCollectorLite/main/ss_iran.txt", // [10ium:CollectorLite ss] 581cfg/63KB
        "https://raw.githubusercontent.com/10ium/V2rayCollector/main/ss_iran.txt", // [10ium:Collector ss] 2365cfg/340KB
        "https://raw.githubusercontent.com/roosterkid/openproxylist/refs/heads/main/V2RAY_RAW.txt", // [10ium:roosterkid] 150cfg/34KB
        "https://raw.githubusercontent.com/10ium/V2rayCollectorLite/main/trojan_iran.txt", // [10ium:CollectorLite trojan] 703cfg/166KB
        "https://raw.githubusercontent.com/10ium/V2rayCollectorLite/main/mixed_iran.txt", // [10ium:CollectorLite mixed] 4367cfg/1020KB
        "https://raw.githubusercontent.com/10ium/V2rayCollectorLite/main/vmess_iran.txt", // [10ium:CollectorLite vmess] 284cfg/95KB
        "https://raw.githubusercontent.com/10ium/V2rayCollectorLite/main/vless_iran.txt", // [10ium:CollectorLite vless] 2275cfg/546KB
        "https://raw.githubusercontent.com/maimengmeng/mysub/refs/heads/main/valid_content.txt", // [10ium:maimengmeng valid content] 1483cfg/233KB
        "https://raw.githubusercontent.com/10ium/V2rayCollector/main/vmess_iran.txt", // [10ium:Collector vmess] 3408cfg/1341KB
        "https://raw.githubusercontent.com/10ium/V2rayCollector/main/trojan_iran.txt", // [10ium:Collector trojan] 2802cfg/930KB
        "https://raw.githubusercontent.com/Rayan-Config/C-Sub/refs/heads/main/configs/proxy.txt", // [10ium:Rayan Config] 47cfg/10KB
        "https://raw.githubusercontent.com/10ium/V2rayCollector/main/mixed_iran.txt", // [10ium:Collector mixed] 3957cfg/1431KB
        "https://raw.githubusercontent.com/maimengmeng/mysub/refs/heads/main/valid_content_all.txt", // [10ium:maimengmeng valid content all] 2398cfg/482KB
        "https://raw.githubusercontent.com/darkvpnapp/safar724/refs/heads/main/api", // [10ium:darkvpnapp safar724] 8cfg/2KB
        "https://raw.githubusercontent.com/darkvpnapp/IRDevs/refs/heads/main/index.html", // [10ium:darkvpnapp IRDevs] 8cfg/2KB
        "https://raw.githubusercontent.com/darkvpnapp/cslab/refs/heads/main/index.html", // [10ium:darkvpnapp cslab] 8cfg/2KB
        "https://raw.githubusercontent.com/darkvpnapp/CloudflarePlus/refs/heads/main/cdn", // [10ium:darkvpnapp CloudflarePlus cdn] 8cfg/2KB
        "https://raw.githubusercontent.com/darkvpnapp/Cloud2/refs/heads/main/aparatapi", // [10ium:darkvpnapp Cloud2] 9cfg/2KB
        "https://raw.githubusercontent.com/code3-dev/v-data/refs/heads/main/vip", // [10ium:code3 dev vip] 89cfg/19KB
        "https://raw.githubusercontent.com/Created-By/Telegram-Eag1e_YT/refs/heads/main/%40Eag1e_YT", // [10ium:Telegram Eag1e YT] 99cfg/22KB
        "https://raw.githubusercontent.com/arshiacomplus/v2rayExtractor/refs/heads/main/ss.html", // [10ium:arshiacomplus ss] 64cfg/6KB
        "https://raw.githubusercontent.com/arshiacomplus/v2rayExtractor/refs/heads/main/vmess.html", // [10ium:arshiacomplus vmess] 12cfg/4KB
        "https://raw.githubusercontent.com/arshiacomplus/v2rayExtractor/refs/heads/main/trojan.html", // [10ium:arshiacomplus trojan] 5cfg/1KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_proxy_configs.txt", // [10ium:config fetcher] 319cfg/127KB
        "https://raw.githubusercontent.com/arshiacomplus/v2rayExtractor/refs/heads/main/mix/sub.html", // [10ium:arshiacomplus mix] 332cfg/62KB
        "https://raw.githubusercontent.com/arshiacomplus/v2rayExtractor/refs/heads/main/vless.html", // [10ium:arshiacomplus vless] 177cfg/39KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/base64/ss", // [10ium:HiN VPN ss] 135cfg/27KB
        "https://github.com/4n0nymou3/multi-proxy-config-fetcher/raw/refs/heads/main/configs/proxy_configs.txt", // [10ium:4n0nymou3 config fetcher] 155cfg/28KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/base64/trojan", // [10ium:HiN VPN trojan] 91cfg/33KB
        "https://raw.githubusercontent.com/mahsanet/MahsaFreeConfig/refs/heads/main/mci/sub_1.txt", // [10ium:MahsaFreeConfig mci 1] 12cfg/1KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/base64/vless", // [10ium:HiN VPN vless] 467cfg/186KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/base64/mix", // [10ium:HiN VPN mix] 732cfg/268KB
        "https://raw.githubusercontent.com/10ium/HiN-VPN/main/subscription/base64/vmess", // [10ium:HiN VPN vmess] 51cfg/22KB
        "https://raw.githubusercontent.com/mahsanet/MahsaFreeConfig/refs/heads/main/mtn/sub_1.txt", // [10ium:MahsaFreeConfig mtn 1] 6cfg/1KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/merged_base64", // [10ium:V2Hub merged] 1895cfg/671KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Base64/vmess", // [10ium:V2Hub vmess] 116cfg/64KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Base64/vless", // [10ium:V2Hub vless] 1170cfg/448KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Base64/reality", // [10ium:V2Hub reality] 529cfg/204KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Base64/trojan", // [10ium:V2Hub trojan] 338cfg/105KB
        "https://raw.githubusercontent.com/10ium/V2Hub3/main/Split/Base64/shadowsocks", // [10ium:V2Hub shadowsocks] 271cfg/51KB
        "https://raw.githubusercontent.com/10ium/V2RayAggregator/refs/heads/master/Eternity", // [10ium:Aggregator] 200cfg/46KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_ss_iran.txt", // [10ium:CollectorLite ss] 563cfg/81KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_mixed_iran.txt", // [10ium:CollectorLite mixed] 4412cfg/1367KB
        "https://raw.githubusercontent.com/arshiacomplus/robinhood-v1-v2-v3ray/refs/heads/main/conf.txt", // [10ium:arshiacomplus robinhood] 9cfg/0KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_vless_iran.txt", // [10ium:CollectorLite vless] 2251cfg/730KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium-V2rayCollector-trojan.txt", // [10ium:Collector trojan] 2802cfg/1240KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium-V2rayCollector-mixed.txt", // [10ium:Collector mixed] 3957cfg/1908KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_vmess_iran.txt", // [10ium:CollectorLite vmess] 274cfg/124KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium_trojan_iran.txt", // [10ium:CollectorLite trojan] 724cfg/227KB
        "https://raw.githubusercontent.com/10ium/V2ray-Config/main/Splitted-By-Protocol/trojan.txt", // [10ium:V2ray Config trojan] 3586cfg/1111KB
        "https://raw.githubusercontent.com/10ium/V2ray-Config/main/Splitted-By-Protocol/vmess.txt", // [10ium:V2ray Config vmess] 2229cfg/1120KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium-V2rayCollector-ss.txt", // [10ium:Collector ss] 2365cfg/454KB
        "https://raw.githubusercontent.com/10ium/V2ray-Config/main/Splitted-By-Protocol/ss.txt", // [10ium:V2ray Config ss] 3254cfg/634KB
        "https://raw.githubusercontent.com/10ium/base64-encoder/main/encoded/10ium-V2rayCollector-vmess.txt", // [10ium:Collector vmess] 3408cfg/1788KB
        "https://raw.githubusercontent.com/10ium/V2ray-Config/main/Splitted-By-Protocol/hysteria2.txt", // [10ium:V2ray Config hysteria2] 39cfg/11KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/refs/heads/main/shuffled_outputs/rayan_proxy.txt", // [10ium:rayan proxy] 39cfg/15KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/refs/heads/main/shuffled_outputs/roosterkid_v2ray.txt", // [10ium:roosterkid] 150cfg/43KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/refs/heads/main/shuffled_outputs/maimengmeng.txt", // [10ium:maimengmeng] 500cfg/105KB
        "https://shadowmere.xyz/api/b64sub", // [10ium:shadowmere.xyz] 241cfg/37KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_Config_vmess_cloudflare.txt", // [10ium:10ium V2ray Config_vmess cloudflare] 31cfg/229KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_Config_All_cloudflare.txt", // [10ium:10ium V2ray Config_All cloudflare] 1490cfg/492KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_Config_vless_cloudflare.txt", // [10ium:10ium V2ray Config_vless cloudflare] 1344cfg/2677KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_Config_trojan_cloudflare.txt", // [10ium:10ium V2ray Config_trojan cloudflare] 162cfg/43KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_HiNVPN_mix_cloudflare.txt", // [10ium:10ium V2ray HiNVPN mix cloudflare] 31cfg/10KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_fetcher_cloudflare.txt", // [10ium:10ium fetcher cloudflare.txt] 8cfg/4KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2ray_Config_ss_cloudflare.txt", // [10ium:10ium V2ray Config_ss cloudflare] 59cfg/15KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_telegram_configs_collector_cloudflare.txt", // [10ium:10ium telegram configs collector cloudflare] 303cfg/94KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_V2Hub_merged_cloudflare.txt", // [10ium:10ium V2Hub merged cloudflare] 122cfg/43KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_CollectorLite_Config_mixed_cloudflare.txt", // [10ium:10ium CollectorLite Config mixed cloudflare] 225cfg/63KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/10ium_Collector_mixed_cloudflare.txt", // [10ium:10ium Collector mixed cloudflare] 143cfg/48KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/maimengmeng_cloudflare.txt", // [10ium:maimengmeng cloudflare] 27cfg/9KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/mahsanet_mci2_cloudflare.txt", // [10ium:mahsanet mci2 cloudflare] 8cfg/2KB
        "https://raw.githubusercontent.com/vpnclashfa-backup/SubConfigShuffler/main/shuffled_outputs/mahsanet_mtn2_cloudflare.txt", // [10ium:mahsanet mtn2 cloudflare] 7cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/hysteria", // [10ium:Hysteria - Mixed] 18cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/hysteria", // [10ium:Hysteria - Links] 18cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/tuic", // [10ium:Tuic - Mixed] 5cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/tuic", // [10ium:Tuic - Links] 5cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/reality", // [10ium:Reality - Mixed] 767cfg/258KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/reality", // [10ium:Reality - Links] 867cfg/295KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/protocols/reality", // [10ium:Reality - Telegram Channels] 162cfg/54KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/vless", // [10ium:Vless - Mixed] 2231cfg/636KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/vless", // [10ium:Vless - Links] 2705cfg/778KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/vmess", // [10ium:Vmess - Mixed] 96cfg/54KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/protocols/vmess", // [10ium:Vmess - Telegram Channels] 17cfg/10KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/protocols/vless", // [10ium:Vless - Telegram Channels] 196cfg/56KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/vmess", // [10ium:Vmess - Links] 252cfg/143KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/protocols/trojan", // [10ium:Trojan - Telegram Channels] 115cfg/30KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/trojan", // [10ium:Trojan - Links] 393cfg/86KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/trojan", // [10ium:Trojan - Mixed] 459cfg/115KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/protocols/shadowsocks", // [10ium:Shadowsocks - Mixed] 457cfg/77KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/protocols/shadowsocks", // [10ium:Shadowsocks - Links] 1282cfg/234KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/protocols/shadowsocks", // [10ium:Shadowsocks - Telegram Channels] 94cfg/16KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/splitted/channels", // [10ium:Mixed Type - Telegram Channels] 576cfg/165KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/networks/grpc", // [10ium:Google Remote Procedure Call (GRPC) - Links] 223cfg/54KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/networks/grpc", // [10ium:Google Remote Procedure Call (GRPC) - Telegram Channels] 27cfg/8KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/splitted/mixed", // [10ium:Mixed Type - Mixed] 4002cfg/1140KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/networks/ws", // [10ium:WebSocket Protocol (WS) - Telegram Channels] 165cfg/52KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/networks/tcp", // [10ium:Transmission Control Protocol (TCP) - Telegram Channels] 342cfg/91KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/networks/tcp", // [10ium:Transmission Control Protocol (TCP) - Links] 2591cfg/688KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/networks/grpc", // [10ium:Google Remote Procedure Call (GRPC) - Mixed] 109cfg/30KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/networks/ws", // [10ium:WebSocket Protocol (WS) - Links] 2825cfg/826KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/networks/tcp", // [10ium:Transmission Control Protocol (TCP) - Mixed] 1544cfg/427KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/security/tls", // [10ium:Transport Layer Security (TLS) - Mixed] 2264cfg/671KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/security/tls", // [10ium:Transport Layer Security (TLS) - Links] 2314cfg/700KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/security/non-tls", // [10ium:Non Transport Layer Security (Non-TLS) - Links] 1892cfg/452KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/splitted/subscribe", // [10ium:Mixed Type - Links] 3883cfg/1104KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/al/mixed", // [10ium:آلبانی 🇦🇱] 5cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/networks/ws", // [10ium:WebSocket Protocol (WS) - Mixed] 2664cfg/771KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/layers/ipv4", // [10ium:Internet Protocol Version 4 (IPV4) - Mixed] 4002cfg/1140KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/security/non-tls", // [10ium:Non Transport Layer Security (Non-TLS) - Mixed] 822cfg/189KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/security/non-tls", // [10ium:Non Transport Layer Security (Non-TLS) - Telegram Chann] 126cfg/27KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/security/tls", // [10ium:Transport Layer Security (TLS) - Telegram Channels] 247cfg/74KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/channels/layers/ipv4", // [10ium:Internet Protocol Version 4 (IPV4) - Telegram Channels] 576cfg/165KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/au/mixed", // [10ium:استرالیا 🇦🇺] 15cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/at/mixed", // [10ium:اتریش 🇦🇹] 10cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/by/mixed", // [10ium:بلاروس 🇧🇾] 6cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/br/mixed", // [10ium:برزیل 🇧🇷] 7cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/bg/mixed", // [10ium:بلغارستان 🇧🇬] 24cfg/7KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ca/mixed", // [10ium:کانادا 🇨🇦] 42cfg/9KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/cn/mixed", // [10ium:چین 🇨🇳] 7cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/cy/mixed", // [10ium:قبرس 🇨🇾] 29cfg/7KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/cz/mixed", // [10ium:چک 🇨🇿] 22cfg/5KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/dk/mixed", // [10ium:دانمارک 🇩🇰] 5cfg/0KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ee/mixed", // [10ium:استونی 🇪🇪] 13cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/fr/mixed", // [10ium:فرانسه 🇫🇷] 88cfg/25KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/de/mixed", // [10ium:آلمان 🇩🇪] 280cfg/79KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/fi/mixed", // [10ium:فنلاند 🇫🇮] 50cfg/15KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/hk/mixed", // [10ium:هنگ کنگ 🇭🇰] 107cfg/32KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/hu/mixed", // [10ium:مجارستان 🇭🇺] 6cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/in/mixed", // [10ium:هند 🇮🇳] 8cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ir/mixed", // [10ium:ایران 🇮🇷] 112cfg/35KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ie/mixed", // [10ium:ایرلند 🇮🇪] 8cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/it/mixed", // [10ium:ایتالیا 🇮🇹] 31cfg/6KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/il/mixed", // [10ium:اسرائیل 🇮🇱] 6cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/kz/mixed", // [10ium:قزاقستان 🇰🇿] 16cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/kr/mixed", // [10ium:کره جنوبی 🇰🇷] 17cfg/4KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/jp/mixed", // [10ium:ژاپن 🇯🇵] 92cfg/28KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/lt/mixed", // [10ium:لیتوانی 🇱🇹] 34cfg/8KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/lv/mixed", // [10ium:لتونی 🇱🇻] 15cfg/4KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/my/mixed", // [10ium:مالزی 🇲🇾] 9cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/md/mixed", // [10ium:مولداوی 🇲🇩] 8cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/nl/mixed", // [10ium:هلند 🇳🇱] 217cfg/61KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/na/mixed", // [10ium:نامشخص ❓] 1533cfg/441KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/subscribe/layers/ipv4", // [10ium:Internet Protocol Version 4 (IPV4) - Links] 3883cfg/1104KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/pl/mixed", // [10ium:لهستان 🇵🇱] 38cfg/9KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ro/mixed", // [10ium:رومانی 🇷🇴] 18cfg/4KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/rs/mixed", // [10ium:صربستان 🇷🇸] 8cfg/2KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ru/mixed", // [10ium:روسیه 🇷🇺] 74cfg/21KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/sc/mixed", // [10ium:سیشل 🇸🇨] 53cfg/14KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/si/mixed", // [10ium:اسلوونی 🇸🇮] 7cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/sg/mixed", // [10ium:سنگاپور 🇸🇬] 94cfg/29KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/za/mixed", // [10ium:آفریقای جنوبی 🇿🇦] 8cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/es/mixed", // [10ium:اسپانیا 🇪🇸] 25cfg/6KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/se/mixed", // [10ium:سوئد 🇸🇪] 29cfg/9KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ch/mixed", // [10ium:سوئیس 🇨🇭] 24cfg/6KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/tw/mixed", // [10ium:تایوان 🇹🇼] 14cfg/3KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/th/mixed", // [10ium:تایلند 🇹🇭] 5cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/tr/mixed", // [10ium:ترکیه 🇹🇷] 39cfg/10KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ua/mixed", // [10ium:اوکراین 🇺🇦] 8cfg/1KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/ae/mixed", // [10ium:امارات متحده عربی 🇦🇪] 18cfg/4KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/gb/mixed", // [10ium:بریتانیا 🇬🇧] 151cfg/34KB
        "https://raw.githubusercontent.com/10ium/telegram-configs-collector/main/countries/us/mixed", // [10ium:ایالات متحده 🇺🇸] 606cfg/174KB
    )
    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    const val DELAY_TEST_URL2 = "https://www.google.com/generate_204"
    const val OBSERVATORY_LEAST_PING_INTERVAL = "3m"
    const val OBSERVATORY_LEAST_LOAD_INTERVAL = "5m"
    const val OBSERVATORY_LEAST_LOAD_METHOD = "HEAD"
    const val OBSERVATORY_LEAST_LOAD_SAMPLING = "2"
    const val OBSERVATORY_LEAST_LOAD_TIMEOUT = "30s"

    //    const val IP_API_URL = "https://speed.cloudflare.com/meta"
    const val IP_API_URL = "https://api.ip.sb/geoip"

    /** DNS server addresses. */
    const val DNS_PROXY = "https://cloudflare-dns.com/dns-query"
    const val DNS_DIRECT = "178.22.122.100" // Shecan — Iranian public resolver for domestic domains
    const val DNS_VPN = "1.1.1.1"
    const val GEOSITE_PRIVATE = "geosite:private"
    const val GEOSITE_CN = "geosite:cn"
    const val GEOIP_PRIVATE = "geoip:private"
    const val GEOIP_CN = "geoip:cn"
    const val GEOSITE_CATEGORY_IR = "geosite:category-ir"
    const val GEOIP_IR = "geoip:ir"
    const val DOMAIN_IR = "domain:ir"

    /** Geo data file names. */
    const val GEOSITE_DAT = "geosite.dat"
    const val GEOIP_DAT = "geoip.dat"
    const val GEOIP_ONLY_CN_PRIVATE_DAT = "geoip-only-cn-private.dat"
    const val GEOIP_ONLY_CN_PRIVATE_URL = "$GITHUB_RAW_URL/Loyalsoldier/geoip/release/$GEOIP_ONLY_CN_PRIVATE_DAT"

    /** Ports and addresses for various services. */
    const val PORT_LOCAL_DNS = "10853"
    const val PORT_SOCKS = "10808"
    const val LOOPBACK = "127.0.0.1"

    /** Shared defaults for settings shown in the UI and consumed by config generation. */
    const val DEFAULT_SOCKS_ENABLE_UDP = true
    const val DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD = "1"
    const val DEFAULT_VPN_BYPASS_LAN = "1"
    const val DEFAULT_HEV_TUNNEL_LOGLEVEL = "warn"
    const val DEFAULT_MUX_XUDP_CONCURRENCY = "8"

    /** Message constants for communication. */
    const val MSG_REGISTER_CLIENT = 1
    const val MSG_STATE_RUNNING = 11
    const val MSG_STATE_NOT_RUNNING = 12
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_STATE_START = 3
    const val MSG_STATE_START_SUCCESS = 31
    const val MSG_STATE_START_FAILURE = 32
    const val MSG_STATE_STOP = 4
    const val MSG_STATE_STOP_SUCCESS = 41
    const val MSG_STATE_RESTART = 5
    const val MSG_MEASURE_DELAY = 6
    const val MSG_MEASURE_DELAY_RESULT = 61
    const val MSG_MEASURE_DELAY_CANCEL = 62
    const val MSG_MEASURE_CONFIG_START = 7
    const val MSG_MEASURE_CONFIG_CANCEL = 71
    const val MSG_MEASURE_CONFIG_SUCCESS = 72
    const val MSG_MEASURE_CONFIG_NOTIFY = 73
    const val MSG_MEASURE_CONFIG_FINISH = 74

    const val MSG_SUB_UPDATE_START = 8
    const val MSG_SUB_UPDATE_CANCEL = 81

    const val MSG_SESSION_EXPIRED = 91
    const val MSG_SESSION_EXTENDED = 92

    /** Notification channel IDs and names. */
    // Use a new ID because Android does not let an app raise an existing channel's importance.
    const val RAY_NG_CHANNEL_ID = "CORE_M_CH_ID_V2"

    /** Protocols Scheme **/
    const val VMESS = "vmess://"
    const val CUSTOM = ""
    const val SHADOWSOCKS = "ss://"
    const val SOCKS = "socks://"
    const val SOCKS4 = "socks4://"
    const val SOCKS5 = "socks5://"
    const val HTTP = "http://"
    const val VLESS = "vless://"
    const val TROJAN = "trojan://"
    const val WIREGUARD = "wireguard://"
    const val TUIC = "tuic://"
    const val HYSTERIA = "hysteria://"
    const val HYSTERIA2 = "hysteria2://"
    const val HY2 = "hy2://"
    const val V2RAYNFMTS = "v2rayn://"

    /** Give a good name to this, IDK*/
    const val VPN = "VPN"
    const val VPN_MTU = 1500

    /** Root (system-wide) mode runtime constants. */
    const val ROOT_RUNTIME_DIR = "sys_cache"
    const val ROOT_IPTABLES_CHAIN = "CORE_FILTER"
    const val ROOT_FWMARK = 255            // defensive RETURN tag; hev's only upstream socket is loopback (already bypassed)
    const val ROOT_MARK_ROUTE = 1          // packets we want pushed into the tun device
    const val ROOT_ROUTE_TABLE = 2024
    const val ROOT_RULE_PRIORITY = 1000
    const val ROOT_TUN_NAME = "utun7788"
    const val ROOT_TUN_ADDR_V4 = "198.18.0.1/15"
    const val ROOT_TUN_ADDR_V6 = "fdfe:dcba:9876::1/64"

    // hev-socks5-tunnel run as a standalone root binary (reuses the same project already
    // bundled for the VPN hev path; distinct filename from the JNI lib to avoid collision).
    const val ROOT_TUN2SOCKS_BIN = "libhevsockstun.so"
    const val ROOT_FWD_CHAIN = "CORE_FWD"   // FORWARD chain for LAN/tethering sharing
    const val ROOT_DNS_CHAIN = "CORE_DNS"   // nat chain for tethered-client DNS DNAT
    const val ROOT_DNS_OUT_CHAIN = "CORE_DNS_OUT"  // nat chain for device DNS DNAT (root mode)
    const val ROOT_V6_CHAIN = "CORE6_FILTER"       // ip6tables filter/OUTPUT chain: blackhole native IPv6 when it isn't tunneled
    const val ROOT_V6_FWD_CHAIN = "CORE6_FWD" // ip6tables FORWARD chain: route or reject tethered clients' native IPv6
    const val ROOT_V6_PRE_CHAIN = "CORE6_PRE" // ip6tables mangle/PREROUTING chain: mark forwarded clients' IPv6 into the tun
    const val ROOT_LAN_DNS = "1.1.1.1"          // fallback resolver for tethered clients when no plain-IPv4 DNS is configured
    const val ROOT_OOM_SCORE = "-1000"          // oom_score_adj that makes the LMK never kill us

    /** hev-sock5-tunnel read-write-timeout value */
    const val HEVTUN_RW_TIMEOUT = "300,60"

    // Google API rule constants
    const val GOOGLEAPIS_CN_DOMAIN = "domain:googleapis.cn"
    const val GOOGLEAPIS_COM_DOMAIN = "googleapis.com"

    // Android Private DNS constants
    const val DNS_ALIDNS_DOMAIN = "dns.alidns.com"
    const val DNS_CISCO_SSE_DOMAIN = "dns.sse.cisco.com"
    const val DNS_CISCO_UMBRELLA_DOMAIN = "dns.umbrella.com"
    const val DNS_CLOUDFLARE_ONE_DOMAIN = "one.one.one.one"
    const val DNS_CLOUDFLARE_ONEDOT_DNS_DOMAIN = "1dot1dot1dot1.cloudflare-dns.com"
    const val DNS_CLOUDFLARE_DNS_COM_DOMAIN = "dns.cloudflare.com"
    const val DNS_CLOUDFLARE_DNS_DOMAIN = "cloudflare-dns.com"
    const val DNS_CLOUDFLARE_WARP_DOMAIN = "engage.cloudflareclient.com"
    const val DNS_DNSPOD_DOH_DOMAIN = "doh.pub"
    const val DNS_DNSPOD_DOT_DOMAIN = "dot.pub"
    const val DNS_GOOGLE_DOMAIN = "dns.google"
    const val DNS_QUAD9_DOMAIN = "dns.quad9.net"
    const val DNS_SB_DOMAIN = "dns.sb"
    const val DNS_YANDEX_DOMAIN = "common.dot.dns.yandex.net"

    const val DEFAULT_PORT = 443
    const val DEFAULT_SECURITY = "auto"
    const val DEFAULT_LEVEL = 8
    const val DEFAULT_NETWORK = "tcp"
    const val TLS = "tls"
    const val REALITY = "reality"
    const val HEADER_TYPE_HTTP = "http"

    const val UNIDENTIFIED_PACKAGE = "__unknown_app__"

    val DNS_ALIDNS_ADDRESSES = arrayListOf("223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1")
    val DNS_CISCO_SSE_ADDRESSES = arrayListOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
    val DNS_CISCO_UMBRELLA_ADDRESSES = arrayListOf("208.67.220.220", "208.67.222.222", "2620:119:35::35", "2620:119:53::53")
    val DNS_CLOUDFLARE_ONE_ADDRESSES = arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    val DNS_CLOUDFLARE_ONEDOT_DNS_ADDRESSES = arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    val DNS_CLOUDFLARE_DNS_COM_ADDRESSES = arrayListOf("162.159.61.8", "172.64.41.8", "2a06:98c1:52::8", "2803:f800:53::8")
    val DNS_CLOUDFLARE_DNS_ADDRESSES = arrayListOf("104.16.248.249", "104.16.249.249", "2606:4700::6810:f8f9", "2606:4700::6810:f9f9")
    val DNS_CLOUDFLARE_WARP_ADDRESSES = arrayListOf("162.159.192.1", "2606:4700:d0::a29f:c001")
    val DNS_DNSPOD_DOH_ADDRESSES = arrayListOf("1.12.12.12", "120.53.53.53")
    val DNS_DNSPOD_DOT_ADDRESSES = arrayListOf("1.12.12.12", "120.53.53.53")
    val DNS_GOOGLE_ADDRESSES = arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    val DNS_QUAD9_ADDRESSES = arrayListOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    val DNS_SB_ADDRESSES = arrayListOf("45.11.45.11", "185.222.222.222", "2a09::", "2a11::")
    val DNS_YANDEX_ADDRESSES = arrayListOf("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff")

    //minimum list https://serverfault.com/a/304791
    val ROUTED_IP_LIST = arrayListOf(
        "0.0.0.0/5",
        "8.0.0.0/7",
        "11.0.0.0/8",
        "12.0.0.0/6",
        "16.0.0.0/4",
        "32.0.0.0/3",
        "64.0.0.0/2",
        "128.0.0.0/3",
        "160.0.0.0/5",
        "168.0.0.0/6",
        "172.0.0.0/12",
        "172.32.0.0/11",
        "172.64.0.0/10",
        "172.128.0.0/9",
        "173.0.0.0/8",
        "174.0.0.0/7",
        "176.0.0.0/4",
        "192.0.0.0/9",
        "192.128.0.0/11",
        "192.160.0.0/13",
        "192.169.0.0/16",
        "192.170.0.0/15",
        "192.172.0.0/14",
        "192.176.0.0/12",
        "192.192.0.0/10",
        "193.0.0.0/8",
        "194.0.0.0/7",
        "196.0.0.0/6",
        "200.0.0.0/5",
        "208.0.0.0/4",
        "240.0.0.0/4"
    )

    val PRIVATE_IP_LIST = arrayListOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "127.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "224.0.0.0/4"
    )

    val GEO_FILES_SOURCES = arrayListOf(
        "Chocolate4U/Iran-v2ray-rules",
        "Loyalsoldier/v2ray-rules-dat",
        "runetfreedom/russia-v2ray-rules-dat",
    )

    val BUILTIN_OUTBOUND_TAGS = setOf(
        TAG_PROXY,
        TAG_DIRECT,
        TAG_BLOCKED,
    )

    val OBSERVATORY_DURATION_PATTERN = Regex("""[1-9]\d*(ms|s|m|h)""")
}
