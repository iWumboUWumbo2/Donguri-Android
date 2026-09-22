package world.wumbo.donguri.bbs.text

/// Normalises the raw dat/read.cgi markup into the markdown-ish plain text the
/// rest of the app works with: `<br>` becomes a newline, absolute `<a href>`
/// links become markdown links, every other tag is stripped, and HTML entities
/// are resolved.
///
/// 5ch serves its own hand-rolled subset of HTML rather than well-formed
/// documents, so this is a deliberate ordered rewrite rather than a parse.
fun String.htmlDecoded(): String {
    if (!contains('&') && !contains('<')) return this

    var s = this

    if (s.contains('<')) {
        // Void tags
        s = VOID_BR.replace(s, "\n")
        s = VOID_HR.replace(s, "\n")
        s = CLOSE_P.replace(s, "\n")

        // <a href="...">text</a> becomes a markdown link for absolute URLs only;
        // relative hrefs (e.g. >>N reply anchors) are left as plain text so the
        // >>N replacement in PostService can handle them.
        s = ANCHOR.replace(s) { match ->
            val href = match.groupValues[1]
            val text = match.groupValues[2]
            if (href.startsWith("http://") || href.startsWith("https://")) "[$text]($href)" else text
        }

        // Inline formatting — strip tags, keep text
        s = BOLD.replace(s) { it.groupValues[1] }
        s = ITALIC.replace(s) { it.groupValues[1] }
        s = STRIKE.replace(s) { it.groupValues[1] }

        // Strip all remaining tags (including <font …>, etc.)
        s = ANY_TAG.replace(s, "")
    }

    if (!s.contains('&')) return s

    return ENTITY.replace(s) { match ->
        val hex = match.groupValues[1]
        val dec = match.groupValues[2]
        val name = match.groupValues[3]
        when {
            hex.isNotEmpty() -> hex.toIntOrNull(16)?.let(::codePointToString) ?: match.value
            dec.isNotEmpty() -> dec.toIntOrNull()?.let(::codePointToString) ?: match.value
            name.isNotEmpty() -> HTML_NAMED_ENTITIES[name] ?: match.value
            else -> match.value
        }
    }
}

private fun codePointToString(codePoint: Int): String? =
    if (codePoint in 0..0x10FFFF) String(Character.toChars(codePoint)) else null

private val VOID_BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val VOID_HR = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE)
private val CLOSE_P = Regex("""</p>""", RegexOption.IGNORE_CASE)
private val ANCHOR = Regex("""<a\b[^>]*\bhref="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
private val BOLD = Regex("""<(?:b|strong)>(.*?)</(?:b|strong)>""", RegexOption.IGNORE_CASE)
private val ITALIC = Regex("""<(?:i|em)>(.*?)</(?:i|em)>""", RegexOption.IGNORE_CASE)
private val STRIKE = Regex("""<(?:s|del|strike)>(.*?)</(?:s|del|strike)>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]+>""")
private val ENTITY = Regex("""&(?:#x([0-9a-fA-F]+)|#(\d+)|([a-zA-Z][a-zA-Z0-9]*));""")

// Complete HTML4 named entity table — numeric entities (&#N; / &#xN;) cover
// everything else including emoji, so only named forms are listed here.
private val HTML_NAMED_ENTITIES: Map<String, String> = mapOf(

    // XML / HTML basics
    "quot" to "\"", "amp" to "&", "apos" to "'", "lt" to "<", "gt" to ">",
    // Latin-1 supplement
    "nbsp" to "\u00A0", "iexcl" to "\u00A1", "cent" to "\u00A2", "pound" to "\u00A3",
    "curren" to "\u00A4", "yen" to "\u00A5", "brvbar" to "\u00A6", "sect" to "\u00A7",
    "uml" to "\u00A8", "copy" to "\u00A9", "ordf" to "\u00AA", "laquo" to "\u00AB",
    "not" to "\u00AC", "shy" to "\u00AD", "reg" to "\u00AE", "macr" to "\u00AF",
    "deg" to "\u00B0", "plusmn" to "\u00B1", "sup2" to "\u00B2", "sup3" to "\u00B3",
    "acute" to "\u00B4", "micro" to "\u00B5", "para" to "\u00B6", "middot" to "\u00B7",
    "cedil" to "\u00B8", "sup1" to "\u00B9", "ordm" to "\u00BA", "raquo" to "\u00BB",
    "frac14" to "\u00BC", "frac12" to "\u00BD", "frac34" to "\u00BE", "iquest" to "\u00BF",
    "Agrave" to "\u00C0", "Aacute" to "\u00C1", "Acirc" to "\u00C2", "Atilde" to "\u00C3",
    "Auml" to "\u00C4", "Aring" to "\u00C5", "AElig" to "\u00C6", "Ccedil" to "\u00C7",
    "Egrave" to "\u00C8", "Eacute" to "\u00C9", "Ecirc" to "\u00CA", "Euml" to "\u00CB",
    "Igrave" to "\u00CC", "Iacute" to "\u00CD", "Icirc" to "\u00CE", "Iuml" to "\u00CF",
    "ETH" to "\u00D0", "Ntilde" to "\u00D1", "Ograve" to "\u00D2", "Oacute" to "\u00D3",
    "Ocirc" to "\u00D4", "Otilde" to "\u00D5", "Ouml" to "\u00D6", "times" to "\u00D7",
    "Oslash" to "\u00D8", "Ugrave" to "\u00D9", "Uacute" to "\u00DA", "Ucirc" to "\u00DB",
    "Uuml" to "\u00DC", "Yacute" to "\u00DD", "THORN" to "\u00DE", "szlig" to "\u00DF",
    "agrave" to "\u00E0", "aacute" to "\u00E1", "acirc" to "\u00E2", "atilde" to "\u00E3",
    "auml" to "\u00E4", "aring" to "\u00E5", "aelig" to "\u00E6", "ccedil" to "\u00E7",
    "egrave" to "\u00E8", "eacute" to "\u00E9", "ecirc" to "\u00EA", "euml" to "\u00EB",
    "igrave" to "\u00EC", "iacute" to "\u00ED", "icirc" to "\u00EE", "iuml" to "\u00EF",
    "eth" to "\u00F0", "ntilde" to "\u00F1", "ograve" to "\u00F2", "oacute" to "\u00F3",
    "ocirc" to "\u00F4", "otilde" to "\u00F5", "ouml" to "\u00F6", "divide" to "\u00F7",
    "oslash" to "\u00F8", "ugrave" to "\u00F9", "uacute" to "\u00FA", "ucirc" to "\u00FB",
    "uuml" to "\u00FC", "yacute" to "\u00FD", "thorn" to "\u00FE", "yuml" to "\u00FF",
    // Latin Extended-A / B
    "OElig" to "\u0152", "oelig" to "\u0153", "Scaron" to "\u0160", "scaron" to "\u0161",
    "Yuml" to "\u0178", "fnof" to "\u0192",
    // Spacing modifier letters
    "circ" to "\u02C6", "tilde" to "\u02DC",
    // Greek
    "Alpha" to "\u0391", "Beta" to "\u0392", "Gamma" to "\u0393", "Delta" to "\u0394",
    "Epsilon" to "\u0395", "Zeta" to "\u0396", "Eta" to "\u0397", "Theta" to "\u0398",
    "Iota" to "\u0399", "Kappa" to "\u039A", "Lambda" to "\u039B", "Mu" to "\u039C",
    "Nu" to "\u039D", "Xi" to "\u039E", "Omicron" to "\u039F", "Pi" to "\u03A0",
    "Rho" to "\u03A1", "Sigma" to "\u03A3", "Tau" to "\u03A4", "Upsilon" to "\u03A5",
    "Phi" to "\u03A6", "Chi" to "\u03A7", "Psi" to "\u03A8", "Omega" to "\u03A9",
    "alpha" to "\u03B1", "beta" to "\u03B2", "gamma" to "\u03B3", "delta" to "\u03B4",
    "epsilon" to "\u03B5", "zeta" to "\u03B6", "eta" to "\u03B7", "theta" to "\u03B8",
    "iota" to "\u03B9", "kappa" to "\u03BA", "lambda" to "\u03BB", "mu" to "\u03BC",
    "nu" to "\u03BD", "xi" to "\u03BE", "omicron" to "\u03BF", "pi" to "\u03C0",
    "rho" to "\u03C1", "sigmaf" to "\u03C2", "sigma" to "\u03C3", "tau" to "\u03C4",
    "upsilon" to "\u03C5", "phi" to "\u03C6", "chi" to "\u03C7", "psi" to "\u03C8",
    "omega" to "\u03C9", "thetasym" to "\u03D1", "upsih" to "\u03D2", "piv" to "\u03D6",
    // General punctuation
    "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
    "zwnj" to "\u200C", "zwj" to "\u200D", "lrm" to "\u200E", "rlm" to "\u200F",
    "ndash" to "\u2013", "mdash" to "\u2014",
    "lsquo" to "\u2018", "rsquo" to "\u2019", "sbquo" to "\u201A",
    "ldquo" to "\u201C", "rdquo" to "\u201D", "bdquo" to "\u201E",
    "dagger" to "\u2020", "Dagger" to "\u2021", "bull" to "\u2022", "hellip" to "\u2026",
    "permil" to "\u2030", "prime" to "\u2032", "Prime" to "\u2033",
    "lsaquo" to "\u2039", "rsaquo" to "\u203A", "oline" to "\u203E", "frasl" to "\u2044",
    "euro" to "\u20AC", "image" to "\u2111", "weierp" to "\u2118", "real" to "\u211C",
    "trade" to "\u2122", "alefsym" to "\u2135",
    // Arrows
    "larr" to "\u2190", "uarr" to "\u2191", "rarr" to "\u2192", "darr" to "\u2193",
    "harr" to "\u2194", "crarr" to "\u21B5",
    "lArr" to "\u21D0", "uArr" to "\u21D1", "rArr" to "\u21D2", "dArr" to "\u21D3", "hArr" to "\u21D4",
    // Mathematical operators
    "forall" to "\u2200", "part" to "\u2202", "exist" to "\u2203", "empty" to "\u2205",
    "nabla" to "\u2207", "isin" to "\u2208", "notin" to "\u2209", "ni" to "\u220B",
    "prod" to "\u220F", "sum" to "\u2211", "minus" to "\u2212", "lowast" to "\u2217",
    "radic" to "\u221A", "prop" to "\u221D", "infin" to "\u221E", "ang" to "\u2220",
    "and" to "\u2227", "or" to "\u2228", "cap" to "\u2229", "cup" to "\u222A",
    "int" to "\u222B", "there4" to "\u2234", "sim" to "\u223C", "cong" to "\u2245",
    "asymp" to "\u2248", "ne" to "\u2260", "equiv" to "\u2261", "le" to "\u2264", "ge" to "\u2265",
    "sub" to "\u2282", "sup" to "\u2283", "nsub" to "\u2284", "sube" to "\u2286", "supe" to "\u2287",
    "oplus" to "\u2295", "otimes" to "\u2297", "perp" to "\u22A5", "sdot" to "\u22C5",
    // Miscellaneous technical
    "lceil" to "\u2308", "rceil" to "\u2309", "lfloor" to "\u230A", "rfloor" to "\u230B",
    "lang" to "\u2329", "rang" to "\u232A",
    // Geometric / card suits
    "loz" to "\u25CA",
    "spades" to "\u2660", "clubs" to "\u2663", "hearts" to "\u2665", "diams" to "\u2666"
)
