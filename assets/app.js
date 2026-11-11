/* Derafsh landing — i18n (EN default / FA RTL) + live release links */
(function () {
  'use strict';

  var REPO = 'CheginiSoroush/Derafsh-Vpn';
  var RELEASES = 'https://github.com/' + REPO + '/releases/latest';

  /* ---------------- i18n ---------------- */
  var DICT = {
    en: {
      skip: 'Skip to content',
      nav_features: 'Features', nav_download: 'Download', nav_privacy: 'Privacy', nav_faq: 'FAQ',
      preview_overline: 'App preview',
      preview_title: 'See it before you install it.',
      preview_sub: 'Screens from the app itself \u2014 one tap to connect, live server delays, full bilingual UI.',
      cap_main: 'Home \u2014 one tap to connect',
      cap_about: 'About \u2014 source, releases & support',
      cap_settings: 'Settings \u2014 full control',
      badge: '100% free & open source — GPL-3.0',
      h1a: 'The internet,', h1b: 'without walls.',
      lead: 'Derafsh is a free, privacy-first VPN client for Android, powered by the battle-tested Xray core. One tap, a built-in free source — and you\u2019re in.',
      cta_download: 'Download for Android', cta_github: 'View on GitHub', cta_download2: 'Download Derafsh — it\u2019s free',
      chip_android: 'Android 7.0+',
      status_connected: 'Connected',
      features_overline: 'Why Derafsh',
      features_title: 'Everything you need. Nothing you don\u2019t.',
      f1_t: 'Instant free connection',
      f1_p: 'A public free source is built right in. Open the app and it refreshes itself, delay-tests every config and buries the dead ones — no setup, no hunting.',
      f2_t: 'Xray core inside',
      f2_p: 'VLESS, VMess, Trojan and Shadowsocks — plus Hysteria2, with Reality, XTLS, WebSocket and gRPC transports for the toughest networks.',
      f3_t: 'Privacy by design',
      f3_p: 'No account, no sign-up, no tracking. The tunnel is built 100% on your device by the local Xray core — we operate no servers in your path.',
      f4_t: 'Feather-light & fast',
      f4_p: 'A ~25 MB arm64 APK, per-ABI builds and a tuned startup profile. Runs smoothly on Android 7.0 and up.',
      f5_t: 'English & فارسی',
      f5_p: 'A polished bilingual interface with proper RTL support — made for Iranian users first, without excluding anyone else.',
      f6_t: 'Open source, forever',
      f6_p: 'GPL-3.0. Every line is on GitHub — audit it, fork it, build it yourself. Free means free.',
      dl_overline: 'Download',
      dl_title: 'Get Derafsh on your phone',
      dl_sub: 'Direct APKs from the official GitHub releases — signed, reproducible and always up to date.',
      dl_btn: 'Download for Android',
      dl_meta: 'Latest release · arm64-v8a — for most modern phones',
      dl_others: 'Other builds',
      abi_modern: 'most phones · recommended', abi_old: 'older devices',
      abi_x64: 'emulators · Chromebooks', abi_x86: 'legacy 32-bit x86', abi_uni: 'not sure? pick this',
      step1: 'Download the APK that matches your phone.',
      step2: 'Allow \u201cInstall unknown apps\u201d for your browser when Android asks.',
      step3: 'Install, open, connect — the free source is ready on first launch.',
      pr_overline: 'Privacy',
      pr_title: 'What Derafsh never collects',
      p1_t: 'Your identity',
      p1_p: 'No account, name, email or phone number. Derafsh has no sign-up — nothing to leak.',
      p2_t: 'Your traffic',
      p2_p: 'Processed entirely on your device by the local Xray core. No Derafsh server ever touches your connection.',
      p3_t: 'Your history',
      p3_p: 'No logs of visited sites, no browsing history, no precise location. Your phone keeps your stats — locally.',
      pr_link: 'Read the full privacy policy →',
      pr_ads: 'Derafsh shows a few ads to cover development costs — exactly what the ad networks can see is spelled out in the policy.',
      faq_overline: 'FAQ', faq_title: 'Quick answers',
      q1: 'Is Derafsh really free?',
      a1: 'Yes. Every feature is free and the whole app is open source under GPL-3.0. A small number of ads cover the costs.',
      q2: 'Do I need an account or an invitation code?',
      a2: 'No. There is no sign-up, no code, no invite. Install, open, connect.',
      q3: 'Where do the configs come from?',
      a3: 'A public free source ships built-in and is refreshed and delay-tested automatically when you open the app. You can also add your own subscriptions or configs at any time.',
      q4: 'Which APK should I install?',
      a4: 'Modern phones (2016 and later): <b>arm64-v8a</b>. Older devices: <b>armeabi-v7a</b>. Emulators and Chromebooks: <b>x86_64</b>. When in doubt, pick <b>universal</b>.',
      q5: 'Can I check the code myself?',
      a5: 'Absolutely — the full source is public on GitHub under GPL-3.0, and every release APK is signed with the same official key.',
      final_t: 'Ready when you are.',
      final_p: 'Free forever. No account. One tap.',
      f_tag: 'free & open-source VPN for Android',
      f_releases: 'Releases', f_privacy: 'Privacy',
      f_made: 'built with ❤️ and the Xray core',
      ver_pill: 'Latest release — <b>{v}</b> · published {d}'
    },
    fa: {
      skip: 'پرش به محتوا',
      nav_features: 'امکانات', nav_download: 'دانلود', nav_privacy: 'حریم خصوصی', nav_faq: 'سؤالات متداول',
      preview_overline: 'پیش‌نمایش برنامه',
      preview_title: 'قبل از نصب، ببینش.',
      preview_sub: 'صفحه‌های خود برنامه \u2014 اتصال با یک لمس، تأخیر زندهٔ سرورها، رابط دوزبانهٔ کامل.',
      cap_main: 'خانه \u2014 اتصال با یک لمس',
      cap_about: 'درباره \u2014 سورس، ریلیزها و پشتیبانی',
      cap_settings: 'تنظیمات \u2014 کنترل کامل',
      badge: '۱۰۰٪ رایگان و متن‌باز — GPL-3.0',
      h1a: 'اینترنت،', h1b: 'بدون دیوار.',
      lead: 'درفش یک کلاینت VPN رایگان و حامی حریم خصوصی برای اندروید است؛ قدرت‌گرفته از هستهٔ امتحان‌پس‌دادهٔ Xray. یک لمس، منبع رایگانِ داخلی — و وصلیدی.',
      cta_download: 'دانلود برای اندروید', cta_github: 'مشاهده در گیت‌هاب', cta_download2: 'درفش را دانلود کن — رایگان است',
      chip_android: 'اندروید ۷ به بالا',
      status_connected: 'متصل',
      features_overline: 'چرا درفش',
      features_title: 'هرچه لازم داری، نه یک قدم بیشتر.',
      f1_t: 'اتصال رایگان و فوری',
      f1_p: 'یک منبع عمومی رایگان داخل برنامه تعبیه شده. درفش را باز می‌کنی؛ خودش آپدیت می‌شود، تأخیر همهٔ کانفیگ‌ها را واقعاً تست می‌کند و کانفیگ‌های مرده را کنار می‌گذارد — بدون هیچ تنظیمی.',
      f2_t: 'هستهٔ Xray در درون',
      f2_p: 'VLESS و VMess و Trojan و Shadowsocks — به‌علاوهٔ Hysteria2، با ترنسپورت‌های Reality و XTLS و WebSocket و gRPC برای سخت‌ترین شبکه‌ها.',
      f3_t: 'حریم خصوصی از پایه',
      f3_p: 'بدون حساب، بدون ثبت‌نام، بدون ردیابی. تونل ۱۰۰٪ روی دستگاه خودت با هستهٔ محلی Xray ساخته می‌شود — ما هیچ سروری در مسیر ترافیکت نداریم.',
      f4_t: 'سبک و سریع',
      f4_p: 'یک APK حدود ۲۵ مگابایتی arm64، بیلدهای جداشده برای هر معماری و پروفایل راه‌اندازی بهینه‌شده. روی اندروید ۷ به بالا روان اجرا می‌شود.',
      f5_t: 'فارسی و English',
      f5_p: 'رابط کاربری دوزبانه با پشتیبانی درست از راست‌به‌چپ — اول برای کاربر ایرانی، بدون اینکه کسی حذف شود.',
      f6_t: 'متن‌باز، برای همیشه',
      f6_p: 'GPL-3.0. تک‌تک خط‌ها در گیت‌هاب است — بازبینی‌اش کن، فورکش کن، خودت بیلدش کن. رایگان یعنی رایگان.',
      dl_overline: 'دانلود',
      dl_title: 'درفش را روی گوشی‌ات بگیر',
      dl_sub: 'فایل‌های APK مستقیم از ریلیزهای رسمی گیت‌هاب — امضاشده، قابل بازتولید و همیشه به‌روز.',
      dl_btn: 'دانلود برای اندروید',
      dl_meta: 'آخرین نسخه · arm64-v8a — برای بیشتر گوشی‌های امروزی',
      dl_others: 'بیلدهای دیگر',
      abi_modern: 'بیشتر گوشی‌ها · پیشنهادی', abi_old: 'گوشی‌های قدیمی‌تر',
      abi_x64: 'شبیه‌سازها · کروم‌بوک', abi_x86: 'x86 سی‌ودو بیتی قدیمی', abi_uni: 'مطمئن نیستی؟ همین را بگیر',
      step1: 'APK متناسب با گوشی‌ات را دانلود کن.',
      step2: 'وقتی اندروید پرسید، «نصب برنامه‌های ناشناس» را برای مرورگرت مجاز کن.',
      step3: 'نصب کن، باز کن، وصل شو — منبع رایگان از اولین اجرا آماده است.',
      pr_overline: 'حریم خصوصی',
      pr_title: 'درفش هرگز این‌ها را جمع نمی‌کند',
      p1_t: 'هویت تو',
      p1_p: 'نه حساب، نه نام، نه ایمیل، نه شماره تلفن. درفش ثبت‌نام ندارد — چیزی برای لو دادن وجود ندارد.',
      p2_t: 'ترافیک تو',
      p2_p: 'تماماً روی دستگاه خودت و با هستهٔ محلی Xray پردازش می‌شود. هیچ سرور درفشی هرگز اتصالت را لمس نمی‌کند.',
      p3_t: 'تاریخچهٔ تو',
      p3_p: 'نه لاگ سایت‌های بازدیدشده، نه تاریخچهٔ مرور، نه موقعیت دقیق. آمارهایت روی گوشی خودت می‌ماند — به‌صورت محلی.',
      pr_link: 'خواندن سیاست کامل حریم خصوصی ←',
      pr_ads: 'درفش برای پوشش هزینه‌ها چند تبلیغ نشان می‌دهد — اینکه دقیقاً چه داده‌ای به شبکه‌های تبلیغاتی می‌رسد در سیاست حریم خصوصی شفاف نوشته شده.',
      faq_overline: 'سؤالات متداول', faq_title: 'پاسخ‌های سریع',
      q1: 'درفش واقعاً رایگان است؟',
      a1: 'بله. همهٔ امکانات رایگان‌اند و کل برنامه زیر GPL-3.0 متن‌باز است. چند تبلیغِ کم هزینه‌ها را پوشش می‌دهند.',
      q2: 'نیاز به حساب یا کد دعوت دارم؟',
      a2: 'نه. نه ثبت‌نامی هست، نه کدی، نه دعوتی. نصب کن، باز کن، وصل شو.',
      q3: 'کانفیگ‌ها از کجا می‌آیند؟',
      a3: 'یک منبع عمومی رایگان داخل برنامه هست که موقع باز کردن برنامه خودکار آپدیت و تست تأخیر می‌شود. هر وقت بخواهی می‌توانی اشتراک یا کانفیگ خودت را هم اضافه کنی.',
      q4: 'کدام APK را نصب کنم؟',
      a4: 'گوشی‌های امروزی (۲۰۱۶ به بعد): <b>arm64-v8a</b>. دستگاه‌های قدیمی‌تر: <b>armeabi-v7a</b>. شبیه‌ساز و کروم‌بوک: <b>x86_64</b>. اگر شک داری <b>universal</b> را بگیر.',
      q5: 'می‌توانم خودم کد را بررسی کنم؟',
      a5: 'البته — سورس کامل در گیت‌هاب زیر GPL-3.0 عمومی است و APK هر نسخه با همان کلید رسمی امضا می‌شود.',
      final_t: 'هر وقت آماده بودی.',
      final_p: 'برای همیشه رایگان. بدون حساب. با یک لمس.',
      f_tag: 'VPN رایگان و متن‌باز برای اندروید',
      f_releases: 'ریلیزها', f_privacy: 'حریم خصوصی',
      f_made: 'ساخته‌شده با ❤️ و هستهٔ Xray',
      ver_pill: 'آخرین نسخه — <b>{v}</b> · منتشرشده {d}'
    }
  };

  var langBtn = document.getElementById('langBtn');

  function applyLang(lang) {
    var dict = DICT[lang];
    var nodes = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < nodes.length; i++) {
      var key = nodes[i].getAttribute('data-i18n');
      if (dict[key] != null) nodes[i].innerHTML = dict[key];
    }
    var html = document.documentElement;
    html.setAttribute('lang', lang);
    html.setAttribute('dir', lang === 'fa' ? 'rtl' : 'ltr');
    document.title = lang === 'fa'
      ? 'درفش — کلاینت VPN رایگان و متن‌باز اندروید · هستهٔ Xray'
      : 'Derafsh — Free & Open-Source VPN for Android · Xray Core';
    langBtn.textContent = lang === 'fa' ? 'EN' : 'فا';
    langBtn.setAttribute('aria-label', lang === 'fa' ? 'Switch to English' : 'تغییر به فارسی');
    try { localStorage.setItem('derafsh-lang', lang); } catch (e) { /* private mode */ }
  }

  var saved = null;
  try { saved = localStorage.getItem('derafsh-lang'); } catch (e) { /* ignore */ }
  if (saved === 'fa') applyLang('fa'); // English stays the default otherwise
  langBtn.addEventListener('click', function () {
    applyLang(document.documentElement.getAttribute('lang') === 'fa' ? 'en' : 'fa');
  });

  /* ---------------- scroll reveal ---------------- */
  var revealEls = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); }
      });
    }, { threshold: 0.05, rootMargin: '0px 0px 12% 0px' });
    revealEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('in'); });
  }

  /* ---------------- footer year ---------------- */
  var yearEl = document.getElementById('year');
  if (yearEl) yearEl.textContent = String(new Date().getFullYear());

  /* ---------------- live release data ---------------- */
  function fmtSize(bytes) {
    return (bytes / 1048576).toFixed(1) + ' MB';
  }
  function fmtDate(iso) {
    try {
      return new Date(iso).toLocaleDateString(
        document.documentElement.getAttribute('lang') === 'fa' ? 'fa-IR' : 'en-US',
        { year: 'numeric', month: 'short', day: 'numeric' }
      );
    } catch (e) { return ''; }
  }
  function byAbi(assets, abi) {
    for (var i = 0; i < assets.length; i++) {
      if (new RegExp('_' + abi + '\\.apk$', 'i').test(assets[i].name)) return assets[i];
    }
    return null;
  }

  fetch('https://api.github.com/repos/' + REPO + '/releases/latest')
    .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
    .then(function (rel) {
      var assets = (rel && rel.assets) || [];
      var arm64 = byAbi(assets, 'arm64-v8a');
      var ver = (rel.tag_name || '').replace(/^v/, '');

      // version pill under hero CTAs
      var pill = document.getElementById('versionPill');
      if (pill && ver) {
        var tpl = DICT[document.documentElement.getAttribute('lang')].ver_pill || '';
        pill.innerHTML = tpl.replace('{v}', ver).replace('{d}', fmtDate(rel.published_at));
        pill.hidden = false;
      }

      // primary buttons -> direct arm64 asset
      var primary = arm64 ? arm64.browser_download_url : RELEASES;
      ['dlHero', 'dlPrimary', 'dlFinal'].forEach(function (id) {
        var a = document.getElementById(id);
        if (a) a.href = primary;
      });

      // meta line
      var meta = document.getElementById('dlMeta');
      if (meta && arm64) {
        var lang = document.documentElement.getAttribute('lang');
        meta.innerHTML = (lang === 'fa')
          ? 'نسخهٔ <b>v' + ver + '</b> · arm64-v8a · <b>' + fmtSize(arm64.size) + '</b>'
          : 'Latest <b>v' + ver + '</b> · arm64-v8a · <b>' + fmtSize(arm64.size) + '</b>';
      }

      // ABI grid -> real asset links + sizes
      var items = document.querySelectorAll('#dlGrid .dl-item');
      for (var j = 0; j < items.length; j++) {
        (function (item) {
          var asset = byAbi(assets, item.getAttribute('data-abi'));
          if (asset) {
            item.href = asset.browser_download_url;
            var sz = document.createElement('span');
            sz.className = 'sz';
            sz.textContent = fmtSize(asset.size);
            item.appendChild(sz);
          } else {
            item.href = RELEASES;
          }
        })(items[j]);
      }

      // playful ping jitter
      var ping = document.getElementById('statusPing');
      if (ping) {
        setInterval(function () {
          ping.textContent = (19 + Math.floor(Math.random() * 14)) + ' ms';
        }, 2600);
      }
    })
    .catch(function () { /* fallback links already point to /releases/latest */ });

  // star badge (best effort)
  fetch('https://api.github.com/repos/' + REPO)
    .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
    .then(function (repo) {
      if (typeof repo.stargazers_count === 'number') {
        var el = document.getElementById('ghStars');
        if (el) { el.textContent = '★ ' + repo.stargazers_count; el.hidden = false; }
      }
    })
    .catch(function () { /* ignore */ });
})();
