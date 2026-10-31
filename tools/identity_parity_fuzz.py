#!/usr/bin/env python3
"""Differential test: the app's TrackIdentity (Java) and server/server.py (Python) must normalise identically.

A mismatch means the server would store or look up different keys than the app computes (silent lookup misses).
Needs a JDK (javac/java) on PATH.  Usage: identity_parity_fuzz.py [--full] [--seed N]
  quick : first 0x3000 code points + every interesting range + random YouTube-style noise
  --full: EVERY Unicode code point U+0000..U+10FFFF (surrogates excluded) in several positions, every combining-mark
          pair order (canonical reordering), plus a larger noise set.  Runs in CI against that runner's JDK and Python.
The Java and Python runtimes ship different Unicode versions (JDK 17: 13, Python 3.12: 15, Android 8.x: 9/10); case mapping is
therefore taken from the frozen shared table (tools/gen_identity_table.py), and this test proves nothing else diverges.
"""
import argparse, base64, importlib.util, os, random, shutil, subprocess, sys, tempfile, unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BATCH = 40000

def load_server():
    spec = importlib.util.spec_from_file_location("server_api", os.path.join(ROOT, "server", "server.py"))
    mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); return mod

def enc(s): return base64.b64encode(s.encode("utf-8", "surrogatepass")).decode()
def dec(s): return base64.b64decode(s).decode("utf-8", "surrogatepass")

def java_results(classes, pairs):
    inp = "\n".join(enc(t) + " " + enc(a) for t, a in pairs) + "\n"
    out = subprocess.run(["java", "-cp", classes, "IdentityDump"], input=inp.encode(), capture_output=True, check=True).stdout.decode().splitlines()
    if len(out) != len(pairs): raise SystemExit(f"java returned {len(out)} lines for {len(pairs)} inputs")
    return [tuple(dec(x) for x in line.split(" ")) for line in out]

def compare(api, classes, pairs, label):
    bad = []
    for i in range(0, len(pairs), BATCH):
        chunk = pairs[i:i + BATCH]
        for (t, a), (jt, ja) in zip(chunk, java_results(classes, chunk)):
            pt, pa = api.canonical_title(t, a), api.canonical_artist(a)
            if (jt, ja) != (pt, pa): bad.append((t, a, jt, pt, ja, pa))
    print(f"{label}: {len(pairs)} cases, {len(bad)} mismatches")
    for b in bad[:6]: print("   ", [ascii(x) for x in b])
    return len(bad)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--full", action="store_true"); ap.add_argument("--seed", type=int, default=11)
    args = ap.parse_args(); random.seed(args.seed); api = load_server()
    print(f"python {sys.version.split()[0]} unicodedata {unicodedata.unidata_version}")
    work = tempfile.mkdtemp(prefix="idparity")
    try:
        javac = shutil.which("javac")
        if not javac: raise SystemExit("javac not found (a JDK is required)")
        src = os.path.join(ROOT, "app/src/main/java/com/autotuneeq/mvp")
        subprocess.run([javac, "-encoding", "UTF-8", "-d", work, os.path.join(src, "TrackIdentity.java"), os.path.join(src, "IdentityCase.java"),
                        os.path.join(ROOT, "tools/IdentityDump.java")], check=True)
        print(subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()[0])
        limit = 0x110000 if args.full else 0x3000
        pairs = []
        for cp in range(limit):
            if 0xD800 <= cp <= 0xDFFF: continue
            c = chr(cp)
            pairs.append(("Song" + c + "(Official" + c + "Video)", "Art" + c + "ist" + c + "- Topic"))
            pairs.append(("Art" + c + "ist" + c + "-" + c + "Song" + c + "[HD]", "Art" + c + "ist"))
        for cp in list(range(0x2000, 0x2070)) + [0x85, 0xA0, 0x1680, 0x180E, 0x3000, 0xFEFF, 0x3A3, 0x3C2, 0x130, 0x1E9E, 0x212A, 0x17F, 0x1C90, 0x2C2F, 0xA7C0, 0x10570]:
            c = chr(cp); pairs.append(("Song" + c + "(Official" + c + "Video)", "Art" + c + "ist" + c + "- Topic"))
        bad = compare(api, work, pairs, "every code point as separator/letter" if args.full else "code-point scan (quick)")
        # canonical reordering: two combining marks in both orders after a base letter
        marks = [c for c in range(limit) if not 0xD800 <= c <= 0xDFFF and unicodedata.combining(chr(c)) > 0]
        anchors = ["\u0301", "\u0323", "\u0345", "\u05b0", "\u064b"]
        seq = []
        for m in marks:
            for a in anchors:
                seq.append(("e" + chr(m) + a, "x")); seq.append(("e" + a + chr(m), "x"))
        bad += compare(api, work, seq, "combining-mark order (canonical reordering)")
        words = ["Yellow", "Coldplay", "COLDPLAY", "Song", "Caf\u00e9", "Cafe\u0301", "\u0130stanbul", "\u03a3\u039f\u03a6\u039f\u03a3", "stra\u00dfe",
                 "(Official Video)", "[Official Audio]", "(OFFICIAL  MUSIC VIDEO)", "(Lyric Video)", "(Lyrics)", "[4K]", "(HD)", "(Remix)", "(Live)",
                 "(Remastered 2011)", "(feat. X)", "- Official Video", "| Official Music Video", "\u2013 Official Audio", "- Topic", "\u2014", "\u2013", "-",
                 "|", "Artist", "VEVO", "(Visualiser)", "[Full Song]", "(Audio)", "[1080p]", "\u017f", "K", "\u01c5", "\ufb01", "\u2c2f", "\ua7c0", "\U00010570", "\u1c90"]
        seps = [" ", "  ", "\t", "\u00a0", "\u3000", " - ", " \u2013 ", " \u2014 "]
        noise = []
        for _ in range(30000 if args.full else 4000):
            t = "".join(random.choice(words) + random.choice(seps) for _ in range(random.randint(1, 5))).strip() or "x"
            a = "".join(random.choice(words) + random.choice(seps) for _ in range(random.randint(0, 3))).strip()
            noise.append((t, a))
            if random.random() < 0.3: noise.append((a + " - " + t, a))
        bad += compare(api, work, noise, "realistic YouTube-style noise")
        print("PARITY OK" if bad == 0 else "PARITY FAILED"); return 1 if bad else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)

if __name__ == "__main__":
    sys.exit(main())
