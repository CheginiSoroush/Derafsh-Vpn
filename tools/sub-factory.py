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

خروجی‌ها (OUT_DIR):
  sub1.txt … subN.txt        هر فایل حداکثر MAX_PER_SUB کانفیگ
  sub1_b64.txt … subN_b64    نسخه base64 هر chunk
  sub.txt / sub_base64.txt   لیست کامل (برای سازگاری با لینک‌های قدیمی)
  manifest.json              فهرست فایل‌ها و تعدادشان
  sub-stats.json             آمار آخرین اجرا

متغیرهای محیطی:
  DERAFSH_SUB_OUT_DIR   پوشه خروجی (پیش‌فرض: /home/z/my-project/download)
  DERAFSH_SUB_CHUNK     اندازه هر ساب (پیش‌فرض: 200)
  DERAFSH_SUB_SOURCES   منابع سفارشی، جدا‌شده با خط جدید (اختیاری)
"""

import base64
import json
import math
import os
import re
import ssl
import sys
import urllib.request
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
def main() -> int:
    started = datetime.now(timezone.utc)
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


if __name__ == "__main__":
    sys.exit(main())
