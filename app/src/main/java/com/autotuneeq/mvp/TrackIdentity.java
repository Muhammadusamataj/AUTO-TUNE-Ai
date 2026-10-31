package com.autotuneeq.mvp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative identity normalization shared (rule for rule) with server/server.py.
 * Case mapping uses the frozen table in IdentityCase (generated), so keys are identical on every Android/JDK/Python version.
 * It removes only upload/packaging noise (promo suffixes, a "Artist - " prefix that repeats the artist field,
 * a YouTube " - Topic" channel suffix). It never merges remixes, live versions, remasters, edits or features.
 * Any change here must be mirrored in server.py and re-checked with the Java/Python differential fuzz.
 */
public final class TrackIdentity {
    private TrackIdentity() { }
    // ---- 0.3.3-compatible rules (kept for migrating caches written by older versions) ----
    private static final String PROMO = "(?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrics?\\s+video|visuali[sz]er|audio\\s+only|hd\\s+video)";
    private static final Pattern PROMO_SUFFIX = Pattern.compile(
        "\\s*(?:\\(\\s*" + PROMO + "\\s*\\)|\\[\\s*" + PROMO + "\\s*\\])\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Old title normalization (0.3.3/0.3.4 first revision). Used for v1/v2 cache migration only. */
    public static String canonical(String input) {
        String s = whitespace(input);
        s = PROMO_SUFFIX.matcher(s).replaceFirst("");
        return WHITESPACE.matcher(s.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
    /** Old key format. Used to find and migrate profiles saved by older versions. */
    public static String legacyKey(String title, String artist) {
        String a = whitespace(artist).toLowerCase(Locale.ROOT);
        return a.length() + ":" + a + canonical(title);
    }

    // ---- current rules ----
    private static final String PROMO_WORD = "(?:official\\s+(?:music\\s+|lyric\\s+|hd\\s+)?video|official\\s+audio"
            + "|official\\s+visuali[sz]er|official\\s+lyrics?(?:\\s+video)?|lyrics?(?:\\s+video)?|visuali[sz]er"
            + "|audio(?:\\s+only)?|music\\s+video|hd\\s+video|hd|hq|4k|1080p|720p|full\\s+(?:song|video|audio))";
    private static final Pattern BRACKET_SUFFIX = Pattern.compile(
        "\\s*(?:\\(\\s*" + PROMO_WORD + "\\s*\\)|\\[\\s*" + PROMO_WORD + "\\s*\\])\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH_SUFFIX = Pattern.compile(
        "\\s*[-\u2013\u2014|]\\s*(?:official\\s+(?:music\\s+|lyric\\s+)?video|official\\s+audio|lyric\\s+video)\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC_SUFFIX = Pattern.compile("\\s*[-\u2013\u2014]\\s*topic\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_PREFIX_REST = Pattern.compile("^\\s+[-\u2013\u2014]\\s+(\\S.*)$");

    /** NFC (Unicode-9-assigned text only), every Unicode/ASCII whitespace or separator (incl. U+001C-001F, U+0085) collapsed to one space. */
    static String whitespace(String input) {
        String in = input == null ? "" : input;
        // NFC only for text whose characters all existed in Unicode 9.0; newer characters may normalise differently per runtime.
        if (IdentityCase.nfcSafe(in)) in = java.text.Normalizer.normalize(in, java.text.Normalizer.Form.NFC);
        return trimSpaces(in.replaceAll("[\\s\\p{Z}\\u001c-\\u001f\\u0085]+", " "));
    }
    /** Strip only U+0020. String.trim() would also drop other control characters, which Python's strip() keeps. */
    private static String trimSpaces(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == ' ') a++;
        while (b > a && s.charAt(b - 1) == ' ') b--;
        return s.substring(a, b);
    }

    public static String canonicalArtist(String artist) {
        String s = whitespace(artist);
        s = TOPIC_SUFFIX.matcher(s).replaceFirst("");
        return lower(WHITESPACE.matcher(trimSpaces(s)).replaceAll(" "));
    }
    /** Frozen Unicode 10 simple lower-casing + final-sigma fold (IdentityCase); never the runtime's own, version-dependent tables. */
    private static String lower(String s) { return IdentityCase.lower(s); }

    public static String canonicalTitle(String title, String artist) {
        String s = whitespace(title);
        for (int i = 0; i < 6; i++) {
            String n = DASH_SUFFIX.matcher(BRACKET_SUFFIX.matcher(s).replaceFirst("")).replaceFirst("");
            if (n.equals(s)) break;
            s = n;
        }
        String t = lower(WHITESPACE.matcher(trimSpaces(s)).replaceAll(" "));
        String a = canonicalArtist(artist);
        if (!a.isEmpty() && t.startsWith(a)) {
            Matcher m = ARTIST_PREFIX_REST.matcher(t.substring(a.length()));
            if (m.matches()) t = m.group(1);
        }
        return t;
    }

    public static String key(String title, String artist) {
        String a = canonicalArtist(artist);
        return a.length() + ":" + a + canonicalTitle(title, artist);
    }
}
