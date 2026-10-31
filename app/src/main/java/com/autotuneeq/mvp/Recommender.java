package com.autotuneeq.mvp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Local-first EQ recommendation with a confidence value and a plain-language explanation.
 * Rules, in priority order: (1) a profile the user saved for this exact song, (2) an online/curated profile for it,
 * (3) a genre preset adjusted by what this user previously chose for that genre class, (4) neutral.
 * It is a rule-based system with bounded preference learning. It does NOT analyse audio, does not know songs it was not
 * told about, and is not a trained AI model. Confidence is a heuristic score, not a probability.
 */
public final class Recommender {
    private Recommender() { }
    public enum Basis { SAVED_FOR_SONG, ONLINE_FOR_SONG, GENRE_PRESET, NEUTRAL }

    public static final class Query {
        public String title = "", artist = "", genre = "";
        public Profile saved;          // profile the user saved for this song (nullable)
        public Profile online;         // profile from the optional lookup (nullable)
        public String onlineSource = "";
    }
    public static final class Recommendation {
        public final Basis basis; public final Profile profile; public final double confidence;
        public final Presets preset;   // preset behind a genre suggestion, otherwise null
        public final String genreClass; public final List<String> why;
        Recommendation(Basis b, Profile p, double c, Presets preset, String genreClass, List<String> why) {
            basis = b; profile = p; confidence = c; this.preset = preset; this.genreClass = genreClass; this.why = why;
        }
        public String confidenceLabel() { return confidence >= 0.75 ? "high" : confidence >= 0.4 ? "medium" : confidence > 0 ? "low" : "none"; }
        public String explanation() { StringBuilder b = new StringBuilder(); for (String s : why) b.append("\u2022 ").append(s).append('\n'); return b.toString().trim(); }
    }

    private static final String[][] GENRES = {
        {"ROCK", "rock", "metal", "punk", "grunge", "hard rock", "indie rock", "alternative"},
        {"POP", "pop", "dance", "synthpop", "k-pop", "disco"},
        {"JAZZ", "jazz", "blues", "swing", "bebop", "soul"},
        {"BASS", "hip hop", "hip-hop", "rap", "trap", "r&b", "rnb", "edm", "electronic", "house", "techno", "dubstep", "reggaeton", "drum and bass"},
        {"VOCAL", "classical", "opera", "acoustic", "folk", "singer-songwriter", "podcast", "spoken word", "audiobook", "speech", "vocal", "country"},
    };
    public static boolean isKnownClass(String key) {
        for (String[] g : GENRES) if (g[0].equals(key)) return true;
        return false;
    }
    /** Map free-text genre metadata to a preset class name, or null when unknown (never guessed). */
    public static String genreClass(String genre) {
        if (genre == null) return null;
        String g = genre.toLowerCase(Locale.ROOT).trim();
        if (g.isEmpty() || g.length() > 80) return null;
        // Whole-word match; the rightmost keyword names the main genre ("Alternative R&B" -> R&B, "Indie Rock" -> rock).
        String best = null; int bestEnd = -1, bestLen = 0;
        for (String[] row : GENRES)
            for (int i = 1; i < row.length; i++) {
                int end = lastWholeWord(g, row[i]);
                if (end > bestEnd || (end == bestEnd && end >= 0 && row[i].length() > bestLen)) { best = row[0]; bestEnd = end; bestLen = row[i].length(); }
            }
        if (best != null) return best;
        return null;
    }

    /** End index of the last occurrence of kw in g not touching a letter/digit on either side, or -1. */
    private static int lastWholeWord(String g, String kw) {
        for (int from = g.length(); ; ) {
            int i = g.lastIndexOf(kw, from);
            if (i < 0) return -1;
            int end = i + kw.length();
            boolean left = i == 0 || !Character.isLetterOrDigit(g.charAt(i - 1));
            boolean right = end == g.length() || !Character.isLetterOrDigit(g.charAt(end));
            if (left && right) return end;
            from = i - 1;
            if (from < 0) return -1;
        }
    }

    public static Recommendation recommend(Query q, PreferenceModel prefs) {
        List<String> why = new ArrayList<>();
        if (q.saved != null) {
            why.add("You saved a profile for this song.");
            return new Recommendation(Basis.SAVED_FOR_SONG, q.saved, 0.95, null, null, why);
        }
        if (q.online != null) {
            why.add("An online profile exists for this song" + (q.onlineSource.isEmpty() ? "." : " (source: " + q.onlineSource + ")."));
            why.add("Online profiles are limited to what the server operator has curated; they are not an analysis of the audio.");
            return new Recommendation(Basis.ONLINE_FOR_SONG, q.online, 0.80, null, null, why);
        }
        String cls = genreClass(q.genre);
        if (cls != null) {
            Presets preset = Presets.valueOf(cls);
            double[] db = preset.db(); double[] off = prefs == null ? new double[5] : prefs.offset(cls);
            int n = prefs == null ? 0 : prefs.feedbackCount(cls);
            for (int i = 0; i < 5; i++) db[i] += off[i];
            double conf = 0.40 + 0.30 * (n / (n + 4.0));                 // 0.40 untouched .. approaches 0.70 with feedback
            why.add("Song metadata says genre \"" + q.genre.trim() + "\", which maps to the " + preset.label + " preset.");
            if (n > 0) why.add("Adjusted by your " + n + " earlier choice" + (n == 1 ? "" : "s") + " for this kind of music (offsets " + fmt(off) + " dB).");
            else why.add("No earlier choices from you for this kind of music yet, so this is the plain preset.");
            why.add("This is a rule-based suggestion from genre text, not an analysis of this recording.");
            return new Recommendation(Basis.GENRE_PRESET, new Profile(db, "genre suggestion " + preset.label.toLowerCase(Locale.ROOT)), conf, preset, cls, why);
        }
        why.add(q.genre == null || q.genre.trim().isEmpty() ? "The player provided no genre and no profile is stored for this song." : "Genre \"" + q.genre.trim() + "\" is not one of the supported classes.");
        why.add("Staying neutral (flat) is the safe choice.");
        return new Recommendation(Basis.NEUTRAL, Profile.flat(), 0.0, null, null, why);
    }
    private static String fmt(double[] v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) { if (i > 0) b.append(", "); b.append(String.format(Locale.US, "%+.1f", v[i])); }
        return b.append("]").toString();
    }
}
