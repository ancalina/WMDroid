#!/usr/bin/env python3
"""Verify a separately signed distribution APK without installing it."""
import argparse
import hashlib
import os
import re
import struct
import subprocess
import zipfile
from pathlib import Path

DEX_NAME = re.compile(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex\Z')
APP_PREFIX = b'Lme/ancal/wmdroid/'
TEST_PREFIXES = tuple(APP_PREFIX + p for p in (b'test/', b'shelltest/', b'compat/'))
PRIVATE_PATHS = tuple(str(path).encode('utf-8') for path in
                      (Path(__file__).resolve().parents[1], Path.home()))

def dex_classes(data):
    """Read standard little-endian DEX class defs and each encoded method's debug offset."""
    def u32(offset):
        assert 0 <= offset <= len(data) - 4, 'DEX uint32 outside file'
        return struct.unpack_from('<I', data, offset)[0]

    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            assert offset < len(data), 'truncated DEX ULEB128'
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                assert value <= 0xffffffff, 'DEX ULEB128 overflow'
                return value, offset
        raise AssertionError('invalid DEX ULEB128')

    assert len(data) >= 112 and data[:4] == b'dex\n' and data[7] == 0, 'not a standard DEX'
    assert u32(32) == len(data) and u32(36) >= 112 and u32(40) == 0x12345678, 'unsupported DEX header'
    string_count, string_offset = u32(56), u32(60)
    type_count, type_offset = u32(64), u32(68)
    count, offset = u32(96), u32(100)
    def string(index):
        assert index < string_count, 'DEX string index outside table'
        _, start = uleb(u32(string_offset + index * 4))
        end = data.find(b'\0', start)
        assert end >= start, 'unterminated DEX string'
        return data[start:end]  # Keep MUTF-8 bytes; comparison needs no lossy decoding.

    classes = {}
    for index in range(count):
        entry = offset + index * 32
        type_index = u32(entry)
        assert type_index < type_count, 'DEX class type outside table'
        string_index = u32(type_offset + type_index * 4)
        descriptor = string(string_index)
        assert descriptor.startswith(b'L') and descriptor.endswith(b';') and descriptor not in classes, 'invalid/duplicate DEX class descriptor'
        source = u32(entry + 16)
        assert source == 0xffffffff or source < string_count, 'DEX source index outside string table'
        cursor, debug = u32(entry + 24), []
        if cursor:
            sizes = []
            for _ in range(4):
                size, cursor = uleb(cursor)
                sizes.append(size)
            for _ in range(sizes[0] + sizes[1]):
                _, cursor = uleb(cursor)  # field_idx_diff
                _, cursor = uleb(cursor)  # access_flags
            for _ in range(sizes[2] + sizes[3]):
                _, cursor = uleb(cursor)  # method_idx_diff
                _, cursor = uleb(cursor)  # access_flags
                code, cursor = uleb(cursor)
                if code:
                    assert code % 4 == 0 and code <= len(data) - 16, 'invalid DEX code item'
                    debug.append(u32(code + 8))
        classes[descriptor] = (u32(entry + 4), None if source == 0xffffffff else string(source), debug)
    return classes


def apk_classes(archive):
    classes = {}
    for name in archive.namelist():
        if DEX_NAME.fullmatch(name):
            parsed = dex_classes(archive.read(name))
            assert not classes.keys() & parsed.keys(), 'class repeated across DEX files'
            classes.update(parsed)
    assert classes, 'APK contains no DEX classes'
    return classes


def verify_classes(release, debug):
    missing, extra = debug.keys() - release.keys(), release.keys() - debug.keys()
    normal_release = {n for n, value in release.items() if not value[0] & 0x1000}
    normal_debug = {n for n, value in debug.items() if not value[0] & 0x1000}
    assert normal_release == normal_debug, ('release/debug non-synthetic class descriptors differ', sorted(normal_debug - normal_release), sorted(normal_release - normal_debug))
    assert all(debug[n][0] & 0x1000 for n in missing) and all(release[n][0] & 0x1000 for n in extra), 'only ACC_SYNTHETIC descriptor differences are allowed'
    app = {name: value for name, value in release.items() if name.startswith(APP_PREFIX)}
    assert app and any(n.startswith(APP_PREFIX + b'sdk/') for n in app), 'app and SDK classes must be present'
    generated = generated_source = generated_debug = 0
    for name, (flags, source, offsets) in app.items():
        assert not name.startswith(TEST_PREFIXES), ('test/compat class packaged', name)
        if flags & 0x1000:
            generated += 1
            # D8 --release synthesizes these markers even from javac -g:none classes.
            assert source in (None, b'D8$$SyntheticClass'), ('unexpected synthetic source_file', name, source)
            assert not any(offsets) or source == b'D8$$SyntheticClass', ('unclassified synthetic debug_info', name)
            generated_source += source is not None
            generated_debug += bool(any(offsets))
        else:
            assert source is None, ('release app/SDK source_file retained', name)
            assert not any(offsets), ('release app/SDK method debug_info retained', name)
    return len(normal_release), len(app) - generated, len(missing), len(extra), generated_source, generated_debug


def verify_private_contents(archive):
    assert not any(n.lower().endswith(('.pk8', '.pem', '.jks', '.keystore')) for n in archive.namelist()), 'signing key packaged'
    for name in archive.namelist():
        assert not any(path in name.encode('utf-8') or path in archive.read(name) for path in PRIVATE_PATHS), ('local absolute path packaged', name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--cert', type=Path, required=True)
    parser.add_argument('--apk', type=Path, default=Path('build/WMDroid-release.apk'))
    parser.add_argument('--debug-apk', type=Path, default=Path('build/WMDroid-debug.apk'))
    args = parser.parse_args()
    env = dict(os.environ, JAVA_HOME=str(args.jdk))
    tools = max((args.sdk / 'build-tools').iterdir(), key=lambda p: tuple(int(n) for n in p.name.split('.') if n.isdecimal()))
    def output(*command):
        return subprocess.check_output([str(c) for c in command], env=env, text=True)

    signature = output(tools / 'apksigner', 'verify', '--verbose', '--print-certs', '--min-sdk-version', '14', args.apk)
    certificate = subprocess.check_output(['openssl', 'x509', '-in', str(args.cert), '-outform', 'DER'])
    fingerprint = hashlib.sha256(certificate).hexdigest()
    assert 'certificate SHA-256 digest: ' + fingerprint in signature
    assert 'Verified using v1 scheme (JAR signing): true' in signature
    manifest = output(tools / 'aapt2', 'dump', 'xmltree', args.apk, '--file', 'AndroidManifest.xml')
    debug = output(tools / 'aapt2', 'dump', 'xmltree', args.debug_apk, '--file', 'AndroidManifest.xml')
    assert re.sub(r'debuggable\(0x0101000f\)=(?:true|false)', 'debuggable=CHECKED', manifest) == re.sub(r'debuggable\(0x0101000f\)=(?:true|false)', 'debuggable=CHECKED', debug)
    assert 'debuggable(0x0101000f)=false' in manifest
    assert 'minSdkVersion(0x0101020c)=14' in manifest
    with zipfile.ZipFile(args.apk) as release, zipfile.ZipFile(args.debug_apk) as baseline:
        names = {n for n in release.namelist() if not n.startswith('META-INF/') and n != 'AndroidManifest.xml'}
        assert names == {n for n in baseline.namelist() if not n.startswith('META-INF/') and n != 'AndroidManifest.xml'}, 'release/debug packaged entries differ'
        assert all(release.read(n) == baseline.read(n) for n in names if not DEX_NAME.fullmatch(n)), 'release/debug resources or assets differ'
        counts = verify_classes(apk_classes(release), apk_classes(baseline))
        verify_private_contents(release)
    print('PASS API14 v1/expected certificate, nondebuggable manifest, identical permissions/resources/assets, matching %d non-synthetic class descriptors, %d non-synthetic app/SDK classes without source/debug info, no test/compat classes/local paths/signing key; ACC_SYNTHETIC descriptor differences debug-only=%d release-only=%d; D8-generated app/SDK source markers=%d debug-info classes=%d' % counts)


if __name__ == '__main__':
    main()
