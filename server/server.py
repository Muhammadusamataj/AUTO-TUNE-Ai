#!/usr/bin/env python3
"""Local-first AutoTune EQ metadata profile API.

Runs on 127.0.0.1 by default. Put it behind HTTPS reverse proxy for Android clients.
No audio storage, scraping, song downloads, or automated analysis of copyrighted streams.
"""
from __future__ import annotations

import hmac
from contextlib import closing
import json
import os
import sqlite3
import re
import sys
import unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # server.py may be loaded by path (tests, tools)
import identity_case_table  # generated, frozen Unicode 10 lower-casing shared with the Android app

DB_PATH = os.getenv("AUTOTUNEEQ_DB", os.path.join(os.path.dirname(__file__), "profiles.sqlite3"))
KEY = os.getenv("AUTOTUNEEQ_API_KEY", "")
HOST = os.getenv("AUTOTUNEEQ_HOST", "127.0.0.1")
PORT = int(os.getenv("AUTOTUNEEQ_PORT", "8765"))

def db_connect():
    db = sqlite3.connect(DB_PATH, timeout=5)
    db.execute("""CREATE TABLE IF NOT EXISTS profiles(
        title_key TEXT NOT NULL,
        artist_key TEXT NOT NULL,
        title TEXT NOT NULL,
        artist TEXT NOT NULL,
        bands_json TEXT NOT NULL,
        source TEXT NOT NULL,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (title_key, artist_key)
    )""")
    db.commit()
    return db

def clean(s, limit=200):
    if not isinstance(s, str) or not s.strip() or len(s) > limit:
        raise ValueError(f"expected nonempty text <= {limit} chars")
    return s.strip()

def validate_bands(raw):
    if not isinstance(raw, list) or len(raw) != 5:
        raise ValueError("bands_db must contain five numbers")
    # bool is an int in Python; explicitly exclude it
    try:
        ok = not any(isinstance(v, bool) or not isinstance(v, (int, float)) or
                     not -3.0 <= float(v) <= 3.0 for v in raw)
    except (OverflowError, ValueError):
        ok = False
    if not ok:
        raise ValueError("each EQ gain must be a finite number in [-3.0, 3.0] dB")
    return [round(float(v), 2) for v in raw]


def _whitespace(text):
    """NFC and collapse every whitespace/separator (same set as TrackIdentity.whitespace in the app)."""
    if identity_case_table.nfc_safe(text):   # NFC only for text whose characters all existed in Unicode 9.0
        text = unicodedata.normalize("NFC", text)
    return " ".join(text.split())

_PROMO_WORD = (r"(?:official\s+(?:music\s+|lyric\s+|hd\s+)?video|official\s+audio"
               r"|official\s+visuali[sz]er|official\s+lyrics?(?:\s+video)?|lyrics?(?:\s+video)?|visuali[sz]er"
               r"|audio(?:\s+only)?|music\s+video|hd\s+video|hd|hq|4k|1080p|720p|full\s+(?:song|video|audio))")
_FLAGS = re.I | re.A   # ASCII-only case folding and \s, exactly like java.util.regex without UNICODE_CASE
_BRACKET_SUFFIX = re.compile(r"\s*(?:\(\s*" + _PROMO_WORD + r"\s*\)|\[\s*" + _PROMO_WORD + r"\s*\])\s*$", _FLAGS)
_DASH_SUFFIX = re.compile(r"\s*[-\u2013\u2014|]\s*(?:official\s+(?:music\s+|lyric\s+)?video|official\s+audio|lyric\s+video)\s*$", _FLAGS)
_TOPIC_SUFFIX = re.compile(r"\s*[-\u2013\u2014]\s*topic\s*$", _FLAGS)
_ARTIST_PREFIX_REST = re.compile(r"^\s+[-\u2013\u2014]\s+(\S.*)$", re.A)

def _lower(s):
    """Frozen Unicode 10 simple lower-casing + final-sigma fold (identity_case_table), same as IdentityCase.lower in the app."""
    return identity_case_table.lower(s)

def canonical_artist(text):
    s = _TOPIC_SUFFIX.sub("", _whitespace(text), count=1)
    return _lower(" ".join(s.split()))

def canonical_title(text, artist):
    s = _whitespace(text)
    for _ in range(6):
        n = _DASH_SUFFIX.sub("", _BRACKET_SUFFIX.sub("", s, count=1), count=1)
        if n == s:
            break
        s = n
    t = _lower(" ".join(s.strip().split()))
    a = canonical_artist(artist)
    if a and t.startswith(a):
        m = _ARTIST_PREFIX_REST.match(t[len(a):])
        if m:
            t = m.group(1)
    return t

class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    server_version = "AutoTuneEQ/0.1"
    def reply(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            return self.reply(200, {"ok": True, "name": "autotune-eq-profile-api"})
        if parsed.path != "/v1/profile":
            return self.reply(404, {"error": "not found"})
        if len(self.path) > 4096:
            return self.reply(414, {"error":"request URI too long"})
        query = parse_qs(parsed.query)
        try:
            title = clean(query.get("title", [None])[0])
            artist = clean(query.get("artist", [None])[0])
        except ValueError as exc:
            return self.reply(400, {"error": str(exc)})
        with closing(db_connect()) as db:
            row = db.execute("SELECT title, artist, bands_json, source FROM profiles "
                             "WHERE title_key=? AND artist_key=?", (canonical_title(title, artist), canonical_artist(artist))).fetchone()
        if not row:
            return self.reply(404, {"error": "profile not found"})
        return self.reply(200, {"title": row[0], "artist": row[1],
                                "bands_db": json.loads(row[2]), "source": row[3]})

    def do_POST(self):
        if self.path != "/v1/profile":
            return self.reply(404, {"error": "not found"})
        # Intentionally fail closed: no writes unless an API key is configured.
        provided = self.headers.get("Authorization", "")
        if not KEY or not hmac.compare_digest(provided.encode("utf-8"), ("Bearer " + KEY).encode("utf-8")):
            return self.reply(403, {"error": "valid admin token required"})
        try:
            if self.headers.get("Transfer-Encoding"):
                return self.reply(400, {"error": "transfer encoding unsupported"})
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > 4096:
                return self.reply(413, {"error": "invalid JSON length"})
            raw = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(raw, dict):
                raise ValueError("JSON payload must be an object")
            title = clean(raw.get("title"))
            artist = clean(raw.get("artist"))
            if not canonical_title(title, artist):
                raise ValueError("title identity is empty")
            bands = validate_bands(raw.get("bands_db"))
            source = clean(raw.get("source", "curated"), 50)
            if source not in ("curated", "user_test", "licensed_analysis"):
                return self.reply(400, {"error": "unsupported source"})
        except (ValueError, TypeError, KeyError, UnicodeDecodeError, json.JSONDecodeError, TimeoutError) as exc:
            return self.reply(400, {"error": str(exc)})
        with closing(db_connect()) as db:
            db.execute("INSERT INTO profiles(title_key,artist_key,title,artist,bands_json,source) "
                       "VALUES(?,?,?,?,?,?) ON CONFLICT(title_key,artist_key) DO UPDATE SET "
                       "title=excluded.title, artist=excluded.artist, bands_json=excluded.bands_json, "
                       "source=excluded.source, updated_at=CURRENT_TIMESTAMP",
                       (canonical_title(title, artist),canonical_artist(artist),title,artist,json.dumps(bands),source))
            db.commit()
        return self.reply(201, {"stored": True, "title": title, "artist": artist, "bands_db": bands})

    def log_message(self, fmt, *args):
        # Avoid logging song titles and artist names in HTTP request paths.
        return

if __name__ == "__main__":
    with closing(db_connect()):
        pass
    print(f"AutoTune EQ profile API listening at http://{HOST}:{PORT}; use HTTPS proxy for Android")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
