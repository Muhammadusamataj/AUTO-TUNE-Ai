package com.autotuneeq.mvp;

import java.util.Locale;

/**
 * Built-in starting curves for the five bands (80, 250, 1000, 4000, 10000 Hz), in dB relative to flat.
 * They are hand-designed, conservative starting points (every value within +/-3 dB and then applied cut-only by
 * EqMath), NOT the output of listening tests or of a trained model. CUSTOM is a marker: its values come from the user.
 */
public enum Presets {
    FLAT("Flat", "No change. Use to compare against any other setting.", new double[]{0, 0, 0, 0, 0}),
    BASS("Bass", "Fuller low end for small speakers; other bands are held down so the overall level never rises.", new double[]{3.0, 1.5, 0, -0.5, -1.0}),
    VOCAL("Vocal", "Clearer voices and speech: a little less low-mid mud, more presence around 1-4 kHz.", new double[]{-1.5, -0.5, 1.5, 2.0, 0.5}),
    ROCK("Rock", "Punchy low end and bright top with a small mid scoop.", new double[]{2.0, 1.0, -1.0, 1.5, 2.0}),
    POP("Pop", "Gentle low-mid warmth and a little air; broadly forgiving.", new double[]{1.0, 1.5, 0.5, 1.5, 1.0}),
    JAZZ("Jazz", "Smooth mids with relaxed low end and slightly lifted treble for cymbals and strings.", new double[]{1.0, 0.5, -0.5, 1.0, 1.5}),
    CUSTOM("Custom", "Your own setting. Saved on this device and restored when the app starts.", new double[]{0, 0, 0, 0, 0});

    public final String label, description;
    private final double[] db;
    Presets(String label, String description, double[] db) { this.label = label; this.description = description; this.db = db; }

    public double[] db() { return db.clone(); }
    public Profile profile() { return new Profile(db.clone(), "preset " + label.toLowerCase(Locale.ROOT)); }
    public boolean builtIn() { return this != CUSTOM; }

    /** Parse a stored id (enum name or label, case-insensitive); unknown/blank values fall back to FLAT, never throw. */
    public static Presets fromId(String id) {
        if (id == null) return FLAT;
        for (Presets p : values())
            if (p.name().equalsIgnoreCase(id.trim()) || p.label.equalsIgnoreCase(id.trim())) return p;
        return FLAT;
    }
}
