#!/usr/bin/env python3
"""Differential test: the app's TrackIdentity (Java) and server/server.py (Python) must normalise identically.

A mismatch means the server would store or look up different keys than the app computes (silent lookup misses).
Needs a JDK (javac) on PATH. Usage: identity_parity_fuzz.py [--full]   (--full scans all code points up to U+1FFFF)
"""
import argparse, base64, importlib.util, os, random, shutil, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def load_server():
    spec = importlib.util.spec_from_file_location("server_api", os.path.join(ROOT, "server", "server.py"))
    mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); return mod

def java_results(classes, pairs):
    inp = "\n".join(base64.b64encode(t.encode("utf-8", "surrogatepass")).decode() + " " +
                    base64.b64encode(a.encode("utf-8", "surrogatepass")).decode() for t, a in pairs) + "\n"
    out = subprocess.run(["java", "-cp", classes, "IdentityDump"], input=inp.encode(), capture_output=True, check=True).stdout.decode().splitlines()
    if len(out) != len(pairs): raise SystemExit(f"java returned {len(out)} lines for {len(pairs)} inputs")
    return [tuple(base64.b64decode(x).decode("utf-8", "surrogatepass") for x in line.split(" ")) for line in out]

def compare(api, classes, pairs, label):
    bad = []
    for (t, a), (jt, ja) in zip(pairs, java_results(classes, pairs)):
        pt, pa = api.canonical_title(t, a), api.canonical_artist(a)
        if (jt, ja) != (pt, pa): bad.append((t, a, jt, pt, ja, pa))
    print(f"{label}: {len(pairs)} cases, {len(bad)} mismatches")
    for b in bad[:5]: print("   ", [ascii(x) for x in b])
    return len(bad)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--full", action="store_true"); ap.add_argument("--seed", type=int, default=11)
    args = ap.parse_args(); random.seed(args.seed); api = load_server()
    work = tempfile.mkdtemp(prefix="idparity")
    try:
        javac = shutil.which("javac")
        if not javac: raise SystemExit("javac not found (a JDK is required)")
        subprocess.run([javac, "-d", work, os.path.join(ROOT, "app/src/main/java/com/autotuneeq/mvp/TrackIdentity.java"),
                        os.path.join(ROOT, "tools/IdentityDump.java")], check=True)
        limit = 0x20000 if args.full else 0x3000
        pairs = []
        for cp in range(limit):
            if 0xD800 <= cp <= 0xDFFF: continue
            c = chr(cp)
            pairs.append(("Song" + c + "(Official" + c + "Video)", "Art" + c + "ist" + c + "- Topic"))
            pairs.append(("Art" + c + "ist" + c + "-" + c + "Song" + c + "[HD]", "Art" + c + "ist"))
        # always include the interesting special ranges even in quick mode
        for cp in list(range(0x2000, 0x2070)) + [0x85, 0xA0, 0x1680, 0x180E, 0x3000, 0xFEFF, 0x3A3, 0x3C2, 0x130, 0x1E9E, 0x212A, 0x17F]:
            c = chr(cp); pairs.append(("Song" + c + "(Official" + c + "Video)", "Art" + c + "ist" + c + "- Topic"))
        bad = compare(api, work, pairs, "code-point scan")
        words = ["Yellow", "Coldplay", "COLDPLAY", "Song", "Caf\u00e9", "Cafe\u0301", "\u0130stanbul", "\u03a3\u039f\u03a6\u039f\u03a3", "stra\u00dfe",
                 "(Official Video)", "[Official Audio]", "(OFFICIAL  MUSIC VIDEO)", "(Lyric Video)", "(Lyrics)", "[4K]", "(HD)", "(Remix)", "(Live)",
                 "(Remastered 2011)", "(feat. X)", "- Official Video", "| Official Music Video", "\u2013 Official Audio", "- Topic", "\u2014", "\u2013", "-",
                 "|", "Artist", "VEVO", "(Visualiser)", "[Full Song]", "(Audio)", "[1080p]", "\u017f", "K", "\u01c5", "\ufb01"]
        seps = [" ", "  ", "\t", "\u00a0", "\u3000", " - ", " \u2013 ", " \u2014 "]
        noise = []
        for _ in range(12000 if args.full else 4000):
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
