"""Exact unmodified OFL font payload; never permits arbitrary font binaries."""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FONT_ASSETS = {
    'fonts/gulim/Gulim-Regular.ttf': '53b5bf1f49462b84e2f33d24cbef513a1cb0381821ca33d7b51063221e95eedc',
    'fonts/gulim/OFL.txt': '59fc225e9c23417ad83070289cd77814937c72fe7c65e72aaff13e5114957988',
    'fonts/gulim/AUTHORS.txt': '25a075bfac35553bb7d9e8f6de0a3ba9ef20379b5d072e18dc4e4ef0d77da0cb',
}


def validate_font_asset(name, data):
    if name not in FONT_ASSETS:
        return False
    if hashlib.sha256(data).hexdigest() != FONT_ASSETS[name]:
        raise ValueError('Unapproved font/license bytes: ' + name)
    return True


def font_assets():
    entries = {}
    for name in FONT_ASSETS:
        data = (ROOT / 'sdk/src/main/assets' / name).read_bytes()
        validate_font_asset(name, data)
        entries[name] = data
    return entries


if __name__ == '__main__':
    font_assets()
    print('PASS pinned OFL font, license and author payloads')
