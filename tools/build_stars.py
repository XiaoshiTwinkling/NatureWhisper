#!/usr/bin/env python3
"""Build NatureWhisper's compact star catalog asset.

Reads a HYG-like CSV (columns containing RA, Dec, magnitude, and B-V/colour index)
and writes src/client/resources/assets/naturewhisper/sky/stars.dat:
  "NWST" magic | u32 count (big-endian) | per star: f32 RA_deg, Dec_deg, mag, B-V.

Usage:
    python tools/build_stars.py <input.csv> [-o out.dat]
Run from the repo root. The raw CSV should NOT be committed; stars.dat is.
"""
import argparse
import csv
import math
import struct
import sys
from pathlib import Path

RA_KEYS = ("ra", "ra_hours", "ra_hours_decimal", "ra_deg", "radeg")
DEC_KEYS = ("dec", "dec_deg", "decdegrees")
MAG_KEYS = ("mag", "magnitude", "vmag", "hpmag")
CI_KEYS = ("ci", "color_index", "bv", "b-v", "colorindex")

# Import the whole catalog by default (set --max-mag to cap the shipped magnitude).
MAX_MAG_DEFAULT = None


def pick(row, keys):
    for key in keys:
        if key in row:
            return key
    # case-insensitive fallback
    for actual in row:
        if actual.lower() in keys:
            return actual
    return None


def to_float(value, what):
    try:
        f = float(value)
    except (TypeError, ValueError):
        raise ValueError(f"non-numeric {what}: {value!r}")
    if math.isnan(f) or math.isinf(f):
        raise ValueError(f"bad {what}: {value!r}")
    return f


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    ap.add_argument("-o", "--out", type=Path,
                    default=Path("src/client/resources/assets/naturewhisper/sky/stars.dat"))
    ap.add_argument("--hours", action="store_true",
                    help="force RA column units as hours (×15 to degrees)")
    ap.add_argument("--max-mag", type=float, default=MAX_MAG_DEFAULT,
                    help="only keep stars brighter than this (default: keep all)")
    args = ap.parse_args()

    if not args.csv.exists():
        print(f"input not found: {args.csv}", file=sys.stderr)
        return 2

    stars = []
    with open(args.csv, newline="", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None:
            print("empty CSV", file=sys.stderr)
            return 2
        header = {name.strip().lower(): name for name in reader.fieldnames}

        ra_key = pick(header, RA_KEYS)
        dec_key = pick(header, DEC_KEYS)
        mag_key = pick(header, MAG_KEYS)
        ci_key = pick(header, CI_KEYS)
        if not (ra_key and dec_key and mag_key):
            print("need ra/dec/mag columns; found header "
                  f"ra={ra_key} dec={dec_key} mag={mag_key} ci={ci_key}", file=sys.stderr)
            return 2

        ra_is_hours = args.hours or ("ra_deg" not in ra_key.lower()
                                     and "degrees" not in ra_key.lower())

        for row in reader:
            try:
                ra = to_float(row[header[ra_key]], "ra")
                dec = to_float(row[header[dec_key]], "dec")
                mag = to_float(row[header[mag_key]], "mag")
                bv = to_float(row[header[ci_key]], "B-V") if ci_key else 0.0
            except ValueError:
                continue
            if args.max_mag is not None and mag > args.max_mag:
                continue
            if dec < -90.0 or dec > 90.0:
                continue
            # HYG-style RA is in hours unless a degree-style column was chosen.
            if ra_is_hours:
                ra *= 15.0
            ra %= 360.0
            stars.append((ra, dec, mag, bv))

    stars.sort(key=lambda s: s[2])  # brightest first
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "wb") as f:
        f.write(b"NWST")
        f.write(struct.pack(">I", len(stars)))
        for ra, dec, mag, bv in stars:
            f.write(struct.pack(">4f", ra, dec, mag, bv))

    limit = f"≤ {args.max_mag}" if args.max_mag is not None else "all"
    print(f"wrote {len(stars)} stars (mag {limit}) to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
