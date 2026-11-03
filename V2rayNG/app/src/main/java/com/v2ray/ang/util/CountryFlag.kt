package com.v2ray.ang.util

import java.util.Locale

/**
 * Derafsh privacy layer: server rows never display config remarks (raw hostnames from public
 * subscriptions leak infrastructure details and look "clienty"). Instead, every row shows a
 * location flag inferred entirely offline from the remark text and the server hostname.
 *
 * Inference passes, most reliable first:
 *  1. Existing flag emoji in the remark (regional indicator pair, e.g. "🇩🇪 Node 3").
 *  2. Uppercase ISO token in the remark ("US-01", "[NL]", "SG 2").
 *  3. Country-code+digit token ("us007", "DE2") in the remark or hostname labels.
 *  4. Country name in English or Persian ("Germany", "آلمان").
 *  5. Hostname: country TLD, then exact ISO labels, then ISO+digits prefixes.
 *
 * Anything unresolved falls back to a globe, so IP-only servers still get a neutral icon.
 */
object CountryFlag {

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val FALLBACK_FLAG = "\uD83C\uDF10" // 🌐

    /** ISO 3166-1 alpha-2 codes this util can recognize. */
    private val SUPPORTED = setOf(
        "US", "GB", "DE", "NL", "FR", "CA", "TR", "RU", "JP", "HK", "SG", "IN", "AE",
        "IT", "ES", "PL", "SE", "CH", "AT", "RO", "UA", "KZ", "AU", "BR", "FI", "NO",
        "DK", "CZ", "GR", "PT", "HU", "IL", "MY", "ID", "TH", "VN", "PH", "KR", "CN",
        "TW", "MX", "AR", "IE", "IS", "ZA", "BE", "MD", "AL", "BG", "IQ", "IR", "SA",
        "QA", "OM", "KW", "BH", "LB", "PK", "BD", "GE", "AM", "AZ", "CY", "EE", "LV",
        "LT", "HR", "RS", "SI", "SK", "BY", "UZ", "NZ", "CL", "EG", "MA", "EU", "MO",
    )

    /**
     * ISO tokens that double as common words ("NO LOGS", "USER ID", "TO BE", "AI", ...);
     * excluded from bare-token matching in remarks to avoid false positives.
     */
    private val PLAIN_TOKEN_EXCLUDED = setOf(
        "IN", "NO", "IT", "IS", "ME", "AT", "BE", "BY", "PR", "ID", "MY", "AM",
        "TV", "AI", "CO", "CA", "MD", "MA", "HR", "AZ",
    )

    /** Digit labels like "us007" are stronger signals, but "no1" (number one) and
     *  "cn2" (the CN2 transit line, usually not China) still must not become flags. */
    private val DIGIT_TOKEN_EXCLUDED = setOf("NO", "IT", "CN")

    /** Uppercase two-letter word with non-letter boundaries, e.g. " US-01 ".
     *  Digits are excluded from the lookahead so "CN2 GIA" (a US transit line, not China)
     *  and friends are never read as bare ISO tokens. */
    private val PLAIN_ISO_TOKEN = Regex("(?<![A-Za-z])[A-Z]{2}(?![a-zA-Z0-9])")

    /** Label like "us007" / "DE2": two letters followed by 1-3 digits. */
    private val ISO_DIGIT_LABEL = Regex("^([A-Za-z]{2})\\d{1,3}$")

    /** Country-code TLDs safe for inference (generic TLDs deliberately excluded). */
    private val TLD_TO_ISO = mapOf(
        "us" to "US", "uk" to "GB", "de" to "DE", "nl" to "NL", "fr" to "FR",
        "ca" to "CA", "tr" to "TR", "ru" to "RU", "jp" to "JP", "hk" to "HK",
        "sg" to "SG", "in" to "IN", "ae" to "AE", "it" to "IT", "es" to "ES",
        "pl" to "PL", "se" to "SE", "ch" to "CH", "at" to "AT", "ro" to "RO",
        "ua" to "UA", "kz" to "KZ", "au" to "AU", "br" to "BR", "fi" to "FI",
        "no" to "NO", "dk" to "DK", "cz" to "CZ", "gr" to "GR", "pt" to "PT",
        "hu" to "HU", "il" to "IL", "my" to "MY", "id" to "ID", "th" to "TH",
        "vn" to "VN", "ph" to "PH", "kr" to "KR", "cn" to "CN", "tw" to "TW",
        "mx" to "MX", "ar" to "AR", "ie" to "IE", "is" to "IS", "za" to "ZA",
        "be" to "BE", "md" to "MD", "al" to "AL", "bg" to "BG", "iq" to "IQ",
        "ir" to "IR", "sa" to "SA", "qa" to "QA", "om" to "OM", "kw" to "KW",
        "bh" to "BH", "lb" to "LB", "pk" to "PK", "bd" to "BD", "ge" to "GE",
        "am" to "AM", "az" to "AZ", "cy" to "CY", "ee" to "EE", "lv" to "LV",
        "lt" to "LT", "hr" to "HR", "rs" to "RS", "si" to "SI", "sk" to "SK",
        "by" to "BY", "uz" to "UZ", "nz" to "NZ", "cl" to "CL", "eg" to "EG",
        "ma" to "MA",
    )

    /**
     * Labels safe to read as bare ISO codes inside hostnames. Word-like codes (in/no/it/is/me,
     * azure's "az", pull-request "pr", ...) are deliberately absent; they are only trusted when
     * glued to digits ("us007") or as the TLD.
     */
    private val UNAMBIGUOUS_HOST_LABELS = SUPPORTED - setOf(
        "IN", "NO", "IT", "IS", "ME", "AT", "BE", "BY", "PR", "ID", "MY", "AM",
        "AI", "CO", "CA", "MD", "MA", "HR", "AZ", "TV",
    )

    /** English and Persian country names -> ISO code, sorted longest-first for contains(). */
    private val NAME_TO_ISO: List<Pair<String, String>> = buildList {
        put("united states", "US"); put("usa", "US"); put("america", "US")
        put("united kingdom", "GB"); put("britain", "GB"); put("england", "GB")
        put("united arab", "AE"); put("emirates", "AE"); put("saudi", "SA")
        put("hong kong", "HK"); put("south korea", "KR"); put("korea", "KR")
        put("south africa", "ZA"); put("new zealand", "NZ")
        put("germany", "DE"); put("netherlands", "NL"); put("france", "FR")
        put("canada", "CA"); put("turkey", "TR"); put("russia", "RU"); put("japan", "JP")
        put("singapore", "SG"); put("india", "IN"); put("iraq", "IQ"); put("iran", "IR")
        put("oman", "OM"); put("qatar", "QA"); put("poland", "PL"); put("spain", "ES")
        put("italy", "IT"); put("switzerland", "CH"); put("ukraine", "UA")
        put("australia", "AU"); put("brazil", "BR"); put("sweden", "SE")
        put("finland", "FI"); put("norway", "NO"); put("denmark", "DK")
        put("greece", "GR"); put("portugal", "PT"); put("hungary", "HU")
        put("romania", "RO"); put("bulgaria", "BG"); put("austria", "AT")
        put("belgium", "BE"); put("ireland", "IE"); put("iceland", "IS")
        put("moldova", "MD"); put("albania", "AL"); put("israel", "IL")
        put("kuwait", "KW"); put("bahrain", "BH"); put("lebanon", "LB")
        put("pakistan", "PK"); put("bangladesh", "BD"); put("indonesia", "ID")
        put("malaysia", "MY"); put("thailand", "TH"); put("vietnam", "VN")
        put("philippines", "PH"); put("taiwan", "TW"); put("macau", "MO")
        put("kazakhstan", "KZ"); put("uzbekistan", "UZ"); put("morocco", "MA")
        put("egypt", "EG"); put("mexico", "MX"); put("argentina", "AR")
        put("chile", "CL"); put("colombia", "CO"); put("europe", "EU")
        put("آمریکا", "US"); put("امریکا", "US"); put("ایالات متحده", "US")
        put("آلمان", "DE"); put("هلند", "NL"); put("فرانسه", "FR")
        put("انگلیس", "GB"); put("انگلستان", "GB"); put("بریتانیا", "GB")
        put("کانادا", "CA"); put("ترکیه", "TR"); put("امارات", "AE")
        put("سوئد", "SE"); put("فنلاند", "FI"); put("روسیه", "RU"); put("ژاپن", "JP")
        put("سنگاپور", "SG"); put("هندوستان", "IN"); put("هند", "IN")
        put("عراق", "IQ"); put("ایران", "IR"); put("عمان", "OM"); put("قطر", "QA")
        put("لهستان", "PL"); put("اسپانیا", "ES"); put("ایتالیا", "IT")
        put("سوئیس", "CH"); put("اوکراین", "UA"); put("چین", "CN")
        put("کره", "KR"); put("استرالیا", "AU"); put("برزیل", "BR")
        put("آذربایجان", "AZ"); put("ارمنستان", "AM"); put("گرجستان", "GE")
        put("قبرس", "CY"); put("مجارستان", "HU"); put("رومانی", "RO")
        put("بلغارستان", "BG"); put("یونان", "GR"); put("پرتغال", "PT")
        put("اتریش", "AT"); put("بلژیک", "BE"); put("دانمارک", "DK")
        put("نروژ", "NO"); put("ایرلند", "IE"); put("ایسلند", "IS")
        put("مولداوی", "MD"); put("آلبانی", "AL"); put("اسرائیل", "IL")
        put("عربستان", "SA"); put("کویت", "KW"); put("بحرین", "BH")
        put("لبنان", "LB"); put("پاکستان", "PK"); put("بنگلادش", "BD")
        put("اندونزی", "ID"); put("مالزی", "MY"); put("تایلند", "TH")
        put("ویتنام", "VN"); put("فیلیپین", "PH"); put("تایوان", "TW")
        put("هنگ کنگ", "HK"); put("قزاقستان", "KZ"); put("ازبکستان", "UZ")
        put("مراکش", "MA"); put("مصر", "EG"); put("مکزیک", "MX")
        put("آرژانتین", "AR"); put("شیلی", "CL"); put("کلمبیا", "CO")
        put("نیوزیلند", "NZ"); put("اروپا", "EU")
    }
        .sortedByDescending { it.first.length }

    /** Infers a flag emoji for a server, or a globe when the location is unknown. */
    fun flagFor(remarks: String?, host: String?): String {
        remarks?.takeIf { it.isNotBlank() }?.let { remark ->
            flagFromEmoji(remark)?.let { return it }
            flagFromIsoTokens(remark)?.let { return it }
            flagFromNames(remark)?.let { return it }
        }
        host?.takeIf { it.isNotBlank() }?.let { address ->
            flagFromHost(address)?.let { return it }
        }
        return FALLBACK_FLAG
    }

    /** Pass 1: an existing regional-indicator flag emoji anywhere in the text. */
    private fun flagFromEmoji(text: String): String? {
        val codePoints = text.codePoints().toArray()
        for (i in 0 until codePoints.size - 1) {
            val a = codePoints[i]
            val b = codePoints[i + 1]
            if (a in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_A + 25 &&
                b in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_A + 25
            ) {
                return String(Character.toChars(a)) + String(Character.toChars(b))
            }
        }
        return null
    }

    /** Passes 2+3: uppercase ISO tokens and letter+digit labels inside the remark. */
    private fun flagFromIsoTokens(text: String): String? {
        PLAIN_ISO_TOKEN.findAll(text).forEach { match ->
            val iso = match.value
            if (iso in SUPPORTED && iso !in PLAIN_TOKEN_EXCLUDED) return flagOf(iso)
        }
        text.split(Regex("[^A-Za-z0-9]+")).forEach { token ->
            if (token.length < 3 || token.length > 5) return@forEach
            val match = ISO_DIGIT_LABEL.find(token) ?: return@forEach
            val iso = match.groupValues[1].uppercase(Locale.US)
            if (iso in SUPPORTED && iso !in DIGIT_TOKEN_EXCLUDED) return flagOf(iso)
        }
        return null
    }

    /** Pass 4: country names in English or Persian. */
    private fun flagFromNames(text: String): String? {
        val lower = text.lowercase(Locale.US)
        for ((name, iso) in NAME_TO_ISO) {
            if (lower.contains(name)) return flagOf(iso)
        }
        return null
    }

    /** Pass 5: hostname TLD, exact ISO labels, then letter+digit labels ("us007"). */
    private fun flagFromHost(hostRaw: String): String? {
        val host = hostRaw.lowercase(Locale.US).trim()
            .removePrefix("[").substringBefore(']')
            .substringBefore(':')
        if (host.isEmpty() || host.none { it in 'a'..'z' }) return null

        val labels = host.split('.', '-')
        TLD_TO_ISO[labels.lastOrNull().orEmpty()]?.let { return flagOf(it) }

        for (label in labels) {
            if (label.length < 3 || label.length > 5) continue
            val match = ISO_DIGIT_LABEL.find(label) ?: continue
            val iso = match.groupValues[1].uppercase(Locale.US)
            if (iso in SUPPORTED && iso !in DIGIT_TOKEN_EXCLUDED) return flagOf(iso)
        }

        for (label in labels.dropLast(1)) {
            val iso = label.uppercase(Locale.US)
            if (iso in UNAMBIGUOUS_HOST_LABELS) return flagOf(iso)
        }
        return null
    }

    private fun flagOf(iso: String): String? {
        if (iso.length != 2 || iso.any { it !in 'A'..'Z' }) return null
        val sb = StringBuilder()
        for (ch in iso) {
            sb.append(String(Character.toChars(REGIONAL_INDICATOR_A + (ch - 'A'))))
        }
        return sb.toString()
    }

    private fun MutableList<Pair<String, String>>.put(name: String, iso: String) {
        add(name to iso)
    }
}
