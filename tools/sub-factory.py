#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Derafsh Sub Factory (درفش) — Local Cron Edition
=================================================
بازنویسی محلیِ `.github/workflows/sub-factory.yml` از پروژه Derafsh-Vpn.

هر اجرا:
  1. کانفیگ‌های رایگان را از منابع گیت‌هاب دریافت می‌کند
  2. اگر خروجی base64 بود، دیکود می‌کند
  3. فقط خطوط با پروتکل مجاز (vmess|vless|trojan|ss|socks|hysteria2) را نگه می‌دارد
  4. تکراری‌ها را حذف و ورودی‌های خراب را پاک می‌کند
  5. کانفیگ‌ها را به ساب‌های حداکثر ۲۰۰تایی تقسیم می‌کند:
        ۶۰۰ کانفیگ  →  sub1.txt + sub2.txt + sub3.txt
        ۱۰۰۰ کانفیگ →  sub1.txt … sub5.txt
  6. هر chunk یک نسخه base64 هم دارد (subN_b64.txt)
  7. خروجی در manifest.json + sub-stats.json ثبت می‌شود

حالت پرمیوم (`--premium`، کار ۱۰):
  همان خط لوله دریافت/تکراری‌زدایی، سپس:
  8. از هر کانفیگ (host, port) استخراج می‌شود
  9. تست TCP-connect موازی — فقط کانفیگ‌های «زنده» نگه داشته می‌شوند
 10. مرتب بر اساس latency، سقف DERAFSH_PREMIUM_MAX → premium-sub.txt
  خروجی سروِ endpoint قفل‌دار license-api است (GET /v1/sub/<code>) و هرگز
  روی برنچ عمومی `sub` آپلود نمی‌شود.

خروجی‌ها (OUT_DIR):
  sub1.txt … subN.txt        هر فایل حداکثر MAX_PER_SUB کانفیگ
  sub1_b64.txt … subN_b64    نسخه base64 هر chunk
  sub.txt / sub_base64.txt   لیست کامل (برای سازگاری با لینک‌های قدیمی)
  manifest.json              فهرست فایل‌ها و تعدادشان
  sub-stats.json             آمار آخرین اجرا
  premium-sub.txt            [فقط --premium] پول زنده‌ی ساب پرمیوم
  premium-stats.json         [فقط --premium] آمار تست latency

متغیرهای محیطی:
  DERAFSH_SUB_OUT_DIR        پوشه خروجی (پیش‌فرض: /home/z/my-project/download)
  DERAFSH_SUB_CHUNK          اندازه هر ساب (پیش‌فرض: 200)
  DERAFSH_SUB_SOURCES        منابع سفارشی، جدا‌شده با خط جدید (اختیاری)
  DERAFSH_PREMIUM_MAX        سقف کانفیگ‌های پرمیوم (پیش‌فرض: 100)
  DERAFSH_PREMIUM_CANDIDATES سقف کاندیدها قبل از تست (پیش‌فرض: 2000)
  DERAFSH_PREMIUM_TIMEOUT    تایم‌اوت TCP-connect ثانیه (پیش‌فرض: 2.0)
"""

import base64
import json
import math
import os
import random
import re
import socket
import ssl
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

# ── تنظیمات ────────────────────────────────────────────────────────────
DEFAULT_SOURCES = [
    # ── منابع جایگزین فعال (تأییدشده با probe در 2026-11-03) ──
    "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
    "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
    "https://raw.githubusercontent.com/MhdiTaheri/V2rayCollector_Py/HEAD/sub/Mix/mix.txt",
    "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/HEAD/subscriptions/v2ray/all_sub.txt",
    "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity.txt",
    "https://raw.githubusercontent.com/freefq/free/master/v2",
]

OUT_DIR = os.environ.get("DERAFSH_SUB_OUT_DIR", "out")
MAX_PER_SUB = int(os.environ.get("DERAFSH_SUB_CHUNK", "200"))   # سقف کانفیگ در هر لینک ساب
MAX_PER_SOURCE = 20000          # سقف ایمنی برای هر منبع
FETCH_TIMEOUT = 60              # ثانیه
USER_AGENT = "Derafsh-SubFactory/1.1"

# پرمیوم (کار ۱۰)
PREMIUM_MAX = int(os.environ.get("DERAFSH_PREMIUM_MAX", "100"))
PREMIUM_CANDIDATES = int(os.environ.get("DERAFSH_PREMIUM_CANDIDATES", "2000"))
PREMIUM_TIMEOUT = float(os.environ.get("DERAFSH_PREMIUM_TIMEOUT", "2.0"))
PREMIUM_WORKERS = 64

SCHEME = re.compile(r"^\s*(vmess|vless|trojan|ss|socks|hysteria2|hy2|tuic)://\S+\s*$", re.IGNORECASE)


# ── دریافت یک منبع ─────────────────────────────────────────────────────
def fetch(url: str) -> str:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        ctx = ssl.create_default_context()
        with urllib.request.urlopen(req, timeout=FETCH_TIMEOUT, context=ctx) as r:
            return r.read().decode("utf-8", "ignore")
    except Exception as e:  # noqa: BLE001 — همان رفتار ورک‌فلوی اصلی
        print(f"WARN fetch failed: {url} -> {e}", file=sys.stderr)
        return ""


def decode_if_base64(body: str) -> str:
    """برخی منابع کل payload را base64 می‌پیچند."""
    body = body.strip()
    if "://" not in body and re.fullmatch(r"[A-Za-z0-9+/=\s]+", body[:4096] or ""):
        try:
            return base64.b64decode(body + "===").decode("utf-8", "ignore")
        except Exception:  # noqa: BLE001
            pass
    return body


def write(path: str, content: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


# ── خط لوله اصلی ───────────────────────────────────────────────────────
# ── خط لوله مشترک: دریافت + تکراری‌زدایی + پاکسازی ─────────────────────
def collect_configs() -> tuple:
    """منابع را می‌خواند، base64 را باز می‌کند، تکراری‌ها و خط‌های خراب را حذف می‌کند."""
    per_source = {}

    sources = [
        ln.strip()
        for ln in os.environ.get("DERAFSH_SUB_SOURCES", "").splitlines()
        if ln.strip()
    ] or DEFAULT_SOURCES

    lines = []
    for url in sources:
        body = fetch(url)
        if not body:
            per_source[url] = 0
            continue
        body = decode_if_base64(body)
        count = 0
        for ln in body.splitlines()[:MAX_PER_SOURCE]:
            ln = ln.strip()
            if ln and SCHEME.match(ln):
                lines.append(ln)
                count += 1
        per_source[url] = count
        print(f"INFO {url} -> {count} configs")

    # حذف تکراری (حفظ ترتیب)
    seen, uniq = set(), []
    for ln in lines:
        if ln not in seen:
            seen.add(ln)
            uniq.append(ln)

    # پاکسازی ورودی‌های واضحاً خراب (بدون host معنادار)
    cleaned = [ln for ln in uniq if re.match(r"^\w+://", ln) and len(ln) > 20]
    return cleaned, per_source


# ── خط لوله اصلی ───────────────────────────────────────────────────────
def main() -> int:
    started = datetime.now(timezone.utc)

    cleaned, per_source = collect_configs()

    # A32: اگر همه منابع در دسترس نبودند (قطعی شبکه / فیلترینگ raw.githubusercontent)
    # نباید خروجی خالی جای ساب‌های منتشرشده را بگیرد — با شکست صریح خارج شو؛
    # گام publish در sub-factory.yml اجرا نمی‌شود و ساب‌های قبلی سر جایشان می‌مانند.
    # (همان گارد run_premium؛ قبل از پاکسازی فایل‌های قدیمی.)
    if not cleaned:
        print("FAIL no configs collected from any source — refusing to wipe existing subs",
              file=sys.stderr)
        return 1

    os.makedirs(OUT_DIR, exist_ok=True)

    # ── تقسیم به ساب‌های حداکثر MAX_PER_SUB تایی ────────────────────────
    # مثال: ۶۰۰ کانفیگ → ۳ ساب لینک؛ ۱۰۰۰ کانفیگ → ۵ ساب لینک
    chunk_size = max(1, MAX_PER_SUB)
    chunks = [cleaned[i:i + chunk_size] for i in range(0, len(cleaned), chunk_size)]
    # فایل‌های chunk قدیمی که این اجرا تولیدشان نکرده حذف می‌شوند تا ساب مرده باقی نماند.
    for old in os.listdir(OUT_DIR):
        if re.fullmatch(r"sub\d+(_b64)?\.txt", old):
            os.remove(os.path.join(OUT_DIR, old))

    manifest_chunks = []
    for idx, chunk in enumerate(chunks, start=1):
        plain = "\n".join(chunk) + "\n"
        b64 = base64.b64encode(plain.encode()).decode()
        plain_name = f"sub{idx}.txt"
        b64_name = f"sub{idx}_b64.txt"
        write(os.path.join(OUT_DIR, plain_name), plain)
        write(os.path.join(OUT_DIR, b64_name), b64)
        manifest_chunks.append({"file": plain_name, "base64": b64_name, "count": len(chunk)})
        print(f"INFO {plain_name}: {len(chunk)} configs")

    # لیست کامل برای سازگاری با لینک‌های قدیمی (sub.txt)
    full_plain = "\n".join(cleaned) + "\n" if cleaned else ""
    sub_txt = os.path.join(OUT_DIR, "sub.txt")
    sub_b64 = os.path.join(OUT_DIR, "sub_base64.txt")
    write(sub_txt, full_plain)
    write(sub_b64, base64.b64encode(full_plain.encode()).decode())

    finished = datetime.now(timezone.utc)
    manifest = {
        "generated_utc": finished.isoformat(timespec="seconds"),
        "generator": "Derafsh-SubFactory/1.1",
        "chunk_size": chunk_size,
        "total_configs": len(cleaned),
        "chunks": manifest_chunks,
    }
    write(os.path.join(OUT_DIR, "manifest.json"),
          json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")

    stats = {
        "last_run_utc": finished.isoformat(timespec="seconds"),
        "duration_seconds": round((finished - started).total_seconds(), 1),
        "total_configs": len(cleaned),
        "chunk_size": chunk_size,
        "chunk_count": len(chunks),
        "per_source": per_source,
        "files": {
            "chunks": [c["file"] for c in manifest_chunks],
            "chunk_base64": [c["base64"] for c in manifest_chunks],
            "full": {"sub.txt": sub_txt, "sub_base64.txt": sub_b64},
            "manifest.json": os.path.join(OUT_DIR, "manifest.json"),
        },
    }
    write(os.path.join(OUT_DIR, "sub-stats.json"),
          json.dumps(stats, ensure_ascii=False, indent=2) + "\n")

    print(f"OK configs merged: {len(cleaned)} -> {len(chunks)} sub link(s) "
          f"(≤{chunk_size} each) in {stats['duration_seconds']}s -> {OUT_DIR}")
    return 0


# ── حالت پرمیوم (کار ۱۰): تست latency سمت فکتوری ──────────────────────
def parse_endpoint(line: str):
    """(host, port) از هر کانفیگ؛ vmess (b64 JSON) و بقیه URIهای استاندارد. خراب → None."""
    try:
        scheme, _, rest = line.partition("://")
        if scheme.lower() == "vmess":
            pad = rest.split("#", 1)[0].strip()
            obj = json.loads(base64.b64decode(pad + "=" * (-len(pad) % 4)).decode("utf-8", "ignore"))
            host = str(obj.get("add", "")).strip()
            port = int(obj.get("port", 0))
            return (host, port) if host and 0 < port < 65536 else None

        u = urllib.parse.urlsplit(line)
        host, port = u.hostname, u.port
        if not host:
            # ss قدیمی: کل بخش بعد از ss:// یک b64 از method:pass@host:port است
            raw = rest.split("#", 1)[0].split("?", 1)[0].strip()
            dec = base64.b64decode(raw + "=" * (-len(raw) % 4)).decode("utf-8", "ignore")
            u2 = urllib.parse.urlsplit("ss://" + dec)
            host, port = u2.hostname, u2.port
        if not host or not port or not (0 < port < 65536):
            return None
        return (host.strip("[]"), int(port))
    except Exception:  # noqa: BLE001 — خط خراب فقط حذف می‌شود
        return None


def tcp_probe(host: str, port: int):
    """TCP-connect handshake؛ latency ثانیه یا None اگر زنده نباشد."""
    t0 = time.monotonic()
    try:
        with socket.create_connection((host, port), timeout=PREMIUM_TIMEOUT):
            return time.monotonic() - t0
    except OSError:
        return None


def run_premium() -> int:
    started = datetime.now(timezone.utc)
    cleaned, per_source = collect_configs()
    if not cleaned:
        print("FAIL no configs collected", file=sys.stderr)
        return 1

    # نمونه‌گیری متنوع (ترتیب منابع را می‌شکند تا یک منبع انحصاری نشود)
    candidates = cleaned[:]
    random.shuffle(candidates)
    candidates = candidates[:PREMIUM_CANDIDATES]

    # هر (host, port) فقط یک‌بار تست شود؛ یک نماینده از هر سرور کافی است
    endpoints = {}
    for ln in candidates:
        ep = parse_endpoint(ln)
        if ep:
            endpoints.setdefault(ep, ln)

    print(f"INFO testing {len(endpoints)} unique endpoint(s) "
          f"(from {len(candidates)} candidates, timeout={PREMIUM_TIMEOUT}s, workers={PREMIUM_WORKERS})")

    with ThreadPoolExecutor(max_workers=PREMIUM_WORKERS) as pool:
        results = list(pool.map(lambda ep: (ep, tcp_probe(ep[0], ep[1])), endpoints.keys()))
    live = [(ep, lat) for ep, lat in results if lat is not None]
    live.sort(key=lambda kv: kv[1])

    picked, used = [], set()
    for ep, lat in live:
        ln = endpoints[ep]
        if ln not in used:
            picked.append(ln)
            used.add(ln)
        if len(picked) >= PREMIUM_MAX:
            break

    os.makedirs(OUT_DIR, exist_ok=True)
    body = "\n".join(picked) + ("\n" if picked else "")
    write(os.path.join(OUT_DIR, "premium-sub.txt"), body)

    finished = datetime.now(timezone.utc)
    stats = {
        "generated_utc": finished.isoformat(timespec="seconds"),
        "generator": "Derafsh-SubFactory/1.1-premium",
        "sources": per_source,
        "pool_size_before_dedupe": len(cleaned),
        "candidates": len(candidates),
        "unique_endpoints": len(endpoints),
        "reachable": len(live),
        "selected": len(picked),
        "timeout_seconds": PREMIUM_TIMEOUT,
        "median_latency_seconds": (round(live[len(live) // 2][1], 3) if live else None),
        "duration_seconds": round((finished - started).total_seconds(), 1),
    }
    write(os.path.join(OUT_DIR, "premium-stats.json"),
          json.dumps(stats, ensure_ascii=False, indent=2) + "\n")

    print(f"OK premium: {len(live)}/{len(endpoints)} endpoint(s) reachable -> "
          f"{len(picked)} configs in {stats['duration_seconds']}s -> "
          f"{os.path.join(OUT_DIR, 'premium-sub.txt')}")
    return 0 if picked else 1


if __name__ == "__main__":
    sys.exit(run_premium() if "--premium" in sys.argv else main())
