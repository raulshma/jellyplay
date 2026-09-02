package com.raulshma.jellyplay.core.model.subtitle

/**
 * Web twin of the jvmShared [SubtitleLanguageCodes] (same package, same API).
 *
 * The JVM original derives its ISO 639 tables from `java.util.Locale`
 * (`getAvailableLocales` / `isO3Language` / `getDisplayLanguage`), which does
 * not exist on wasmJs. This clone keeps byte-level conversion semantics for
 * every code the static tables below cover — including the identical
 * 639-2B↔639-3 override map — and falls back like the original for uncovered
 * inputs (primary-subtag passthrough / raw-code return). Documented deltas
 * vs. the JDK-backed original:
 *  - coverage is bounded by the compiled-in matrix instead of the host CLDR;
 *    codes outside it fall through to the passthrough both implementations
 *    use for unknown codes,
 *  - display names are fixed English names from the table rather than
 *    locale-data lookups (same casing style: "English", "German", ...),
 *  - JDK legacy subtags (`iw`/`in`/`ji`) are normalized to their modern forms
 *    (`he`/`id`/`yi`) before lookup instead of relying on host legacy tables, and
 *  - exotic multi-subtag originals bypass the JVM's secondary
 *    `forLanguageTag` re-parse and resolve against their normalized primary
 *    subtag instead.
 *
 * Keep member semantics in sync when editing the jvmShared original; the
 * :shared:core:model:jvmTest suite pins the JVM side.
 */
object SubtitleLanguageCodes {

    /**
     * ISO 639-3 (terminologic/T) → ISO 639-2B (bibliographic/B) overrides —
     * copied verbatim from the jvmShared original so B-form consumers
     * (OpenSubtitles) stay stable across platforms.
     */
    private val ISO3_TO_2B: Map<String, String> = mapOf(
        "bod" to "tib", "deu" to "ger", "ell" to "gre", "fas" to "per",
        "fra" to "fre", "hye" to "arm", "isl" to "ice", "mkd" to "mac",
        "mri" to "mao", "msa" to "may", "mya" to "bur", "nld" to "dut",
        "ron" to "rum", "slk" to "slo", "sqi" to "alb", "hbs" to "scr",
        "cym" to "wel", "zho" to "chi", "ces" to "cze", "kat" to "geo",
        "eus" to "baq", "srp" to "scc",
    )

    /** Reversed: ISO 639-2B (B) → ISO 639-3 (T) for the differing codes. */
    private val ISO2B_TO_3: Map<String, String> = ISO3_TO_2B.entries.associate { (a, b) -> b to a }

    /**
     * Full matrix of the officially assigned ISO 639-1 codes, as
     * `iso1:iso3(T):English name` triplets. One row per 639-1 entry — the
     * same population the JDK locale sweep exposes on Android/desktop hosts.
     */
    private const val LANG_TABLE: String =
        "aa:aar:Afar;ab:abk:Abkhazian;ae:ave:Avestan;af:afr:Afrikaans;" +
            "ak:aka:Akan;am:amh:Amharic;an:arg:Aragonese;ar:ara:Arabic;" +
            "as:asm:Assamese;av:ava:Avaric;ay:aym:Aymara;az:aze:Azerbaijani;" +
            "ba:bak:Bashkir;be:bel:Belarusian;bg:bul:Bulgarian;bh:bih:Bihari languages;" +
            "bi:bis:Bislama;bm:bam:Bambara;bn:ben:Bengali;bo:bod:Tibetan;" +
            "br:bre:Breton;bs:bos:Bosnian;ca:cat:Catalan;ce:che:Chechen;" +
            "ch:cha:Chamorro;co:cos:Corsican;cr:cre:Cree;cs:ces:Czech;" +
            "cu:chu:Church Slavic;cv:chv:Chuvash;cy:cym:Welsh;da:dan:Danish;" +
            "de:deu:German;dv:div:Divehi;dz:dzo:Dzongkha;ee:ewe:Ewe;" +
            "el:ell:Greek;en:eng:English;eo:epo:Esperanto;es:spa:Spanish;" +
            "et:est:Estonian;eu:eus:Basque;fa:fas:Persian;ff:ful:Fulah;" +
            "fi:fin:Finnish;fj:fij:Fijian;fo:fao:Faroese;fr:fra:French;" +
            "fy:fry:Western Frisian;ga:gle:Irish;gd:gla:Scottish Gaelic;gl:glg:Galician;" +
            "gv:glv:Manx;gn:grn:Guarani;gu:guj:Gujarati;ha:hau:Hausa;" +
            "he:heb:Hebrew;hi:hin:Hindi;ho:hmo:Hiri Motu;hr:hrv:Croatian;" +
            "ht:hat:Haitian Creole;hu:hun:Hungarian;hy:hye:Armenian;hz:her:Herero;" +
            "ia:ina:Interlingua;id:ind:Indonesian;ie:ile:Interlingue;ig:ibo:Igbo;" +
            "ii:iii:Sichuan Yi;ik:ipk:Inupiaq;io:ido:Ido;is:isl:Icelandic;" +
            "it:ita:Italian;iu:iku:Inuktitut;ja:jpn:Japanese;jv:jav:Javanese;" +
            "ka:kat:Georgian;kg:kon:Kongo;ki:kik:Kikuyu;kj:kua:Kuanyama;" +
            "kk:kaz:Kazakh;kl:kal:Kalaallisut;km:khm:Central Khmer;kn:kan:Kannada;" +
            "ko:kor:Korean;kr:kau:Kanuri;ks:kas:Kashmiri;ku:kur:Kurdish;" +
            "kv:kom:Komi;kw:cor:Cornish;ky:kir:Kirghiz;la:lat:Latin;" +
            "lb:ltz:Luxembourgish;lg:lug:Ganda;li:lim:Limburgan;ln:lin:Lingala;" +
            "lo:lao:Lao;lt:lit:Lithuanian;lu:lub:Luba-Katanga;lv:lav:Latvian;" +
            "mg:mlg:Malagasy;mh:mah:Marshallese;mk:mkd:Macedonian;ml:mal:Malayalam;" +
            "mn:mon:Mongolian;mr:mar:Marathi;ms:msa:Malay;mt:mlt:Maltese;" +
            "my:mya:Burmese;na:nau:Nauru;nb:nob:Norwegian Bokmål;nd:nde:North Ndebele;" +
            "ne:nep:Nepali;ng:ndo:Ndonga;nl:nld:Dutch;nn:nno:Norwegian Nynorsk;" +
            "no:nor:Norwegian;nr:nbl:South Ndebele;nv:nav:Navajo;ny:nya:Chichewa;oc:oci:Occitan;" +
            "oj:oji:Ojibwa;om:orm:Oromo;or:ori:Oriya;os:oss:Ossetian;" +
            "pa:pan:Panjabi;pi:pli:Pali;pl:pol:Polish;ps:pus:Pushto;" +
            "pt:por:Portuguese;qu:que:Quechua;rm:roh:Romansh;rn:run:Rundi;" +
            "ro:ron:Romanian;ru:rus:Russian;rw:kin:Kinyarwanda;sa:san:Sanskrit;" +
            "sc:srd:Sardinian;sd:snd:Sindhi;se:sme:Northern Sami;sg:sag:Sango;" +
            "si:sin:Sinhala;sk:slk:Slovak;sl:slv:Slovenian;sm:smo:Samoan;" +
            "sn:sna:Shona;so:som:Somali;sq:sqi:Albanian;sr:srp:Serbian;" +
            "ss:ssw:Swati;st:sot:Southern Sotho;su:sun:Sundanese;sv:swe:Swedish;" +
            "sw:swa:Swahili;ta:tam:Tamil;te:tel:Telugu;tg:tgk:Tajik;" +
            "th:tha:Thai;ti:tir:Tigrinya;tk:tuk:Turkmen;tl:tgl:Tagalog;" +
            "tn:tsn:Tswana;to:ton:Tonga;tr:tur:Turkish;ts:tso:Tsonga;" +
            "tt:tat:Tatar;tw:twi:Twi;ty:tah:Tahitian;ug:uig:Uighur;" +
            "uk:ukr:Ukrainian;ur:urd:Urdu;uz:uzb:Uzbek;ve:ven:Venda;" +
            "vi:vie:Vietnamese;vo:vol:Volapük;wa:wln:Walloon;wo:wol:Wolof;" +
            "xh:xho:Xhosa;yi:yid:Yiddish;yo:yor:Yoruba;za:zha:Zhuang;" +
            "zh:zho:Chinese;zu:zul:Zulu"

    private val iso1ToIso3: Map<String, String>
    private val iso3ToIso1: Map<String, String>
    private val iso3ToDisplay: Map<String, String>

    init {
        val forward = mutableMapOf<String, String>()
        val names = mutableMapOf<String, String>()
        for (row in LANG_TABLE.split(';')) {
            val fields = row.split(':')
            if (fields.size != 3) continue
            val (one, three, name) = fields
            forward[one] = three
            names[three] = name
        }
        iso1ToIso3 = forward
        iso3ToIso1 = forward.entries.associate { (one, three) -> three to one }
        iso3ToDisplay = names
    }

    /** Converts an arbitrary language code (1/2/3-letter or BCP-47) to ISO 639-3. */
    fun toIso3(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val cleaned = code.trim().replace('_', '-').substringBefore('-')
        val lower = when (cleaned.lowercase()) {
            // JDK grandfetched tag equivalents (iw→he, in→id, ji→yi): the JVM
            // original resolves them via host legacy tables; wasm normalizes.
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> cleaned.lowercase()
        }
        // Short (639-1 or unregistered BCP-47 prefix) input: table lookup, then
        // alphabetic passthrough (the JVM fallback's net behavior).
        if (cleaned.length <= 2) {
            if (!lower.all { it.isLetter() }) return null
            return iso1ToIso3[lower] ?: lower.ifBlank { null }
        }
        // 3-letter: could be 639-2B (B) or 639-3 (T). Normalize B→T, else passthrough.
        return ISO2B_TO_3[lower] ?: lower.ifBlank { null }
    }

    /** Converts an arbitrary language code to ISO 639-1 (2-letter). Null if unmappable. */
    fun toIso1(code: String?): String? {
        val iso3 = toIso3(code) ?: return null
        // Codes without a 639-1 counterpart echo their 3-letter form, matching
        // the JVM fallback that re-parses the tag into itself.
        return iso3ToIso1[iso3] ?: iso3
    }

    /** Converts an arbitrary language code to ISO 639-2B (OpenSubtitles). */
    fun toIso2B(code: String?): String? {
        val iso3 = toIso3(code) ?: return null
        return ISO3_TO_2B[iso3] ?: iso3
    }

    /**
     * Joins [codes] (any dialect) into a comma-separated list in the target
     * dialect, dropping any that fail to convert. Returns "" for empty input so
     * callers can append `&language=` unconditionally without trailing junk.
     */
    fun join(codes: List<String>, convert: (String) -> String?): String =
        codes.mapNotNull(convert).filter { it.isNotBlank() }.joinToString(",")

    /** Human-readable display name for a code, e.g. `eng` → "English". */
    fun displayName(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val iso3 = toIso3(code) ?: return code
        return iso3ToDisplay[iso3] ?: iso3
    }
}
