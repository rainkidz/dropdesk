#!/usr/bin/env python3
"""Generate kode lisensi premium bulanan TubeNime.

Format: TN1-YYYYMM-XXXX-SSSSSSSS  (MAC = 40 bit pertama HMAC-SHA256,
di-encode alfabet Crockford — HARUS identik dengan LicenseManager.kt).

Pakai:
    set LICENSE_HMAC_SECRET=<isi local.properties>
    python tools/make_license.py --months 1
    python tools/make_license.py --months 3 --count 5

JANGAN commit secret ke git. Kirim kode hasilnya ke pembeli via chat.
"""
import argparse
import hashlib
import hmac
import os
import secrets
import sys
from datetime import date

ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"


def mac40(secret: str, ver: str, expiry: str, rand: str) -> str:
    msg = f"{ver}|{expiry}|{rand}".encode()
    digest = hmac.new(secret.encode(), msg, hashlib.sha256).digest()
    acc, bits, out = 0, 0, []
    for b in digest:
        acc = (acc << 8) | b
        bits += 8
        while bits >= 5 and len(out) < 8:
            bits -= 5
            out.append(ALPHABET[(acc >> bits) & 0x1F])
        if len(out) == 8:
            break
    return "".join(out)


def expiry_yyyymm(months: int) -> str:
    total = date.today().year * 12 + (date.today().month - 1) + months
    return f"{total // 12}{total % 12 + 1:02d}"


def make_key(secret: str, months: int) -> str:
    expiry = expiry_yyyymm(months)
    rand = "".join(secrets.choice(ALPHABET) for _ in range(4))
    return f"TN1-{expiry}-{rand}-{mac40(secret, 'TN1', expiry, rand)}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--months", type=int, default=1, help="masa berlaku (bulan)")
    ap.add_argument("--count", type=int, default=1, help="jumlah kode")
    args = ap.parse_args()
    secret = os.environ.get("LICENSE_HMAC_SECRET", "").strip()
    if not secret:
        print("ERROR: set LICENSE_HMAC_SECRET dulu (lihat local.properties).", file=sys.stderr)
        return 1
    for _ in range(args.count):
        print(make_key(secret, args.months))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
