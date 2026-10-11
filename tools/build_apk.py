#!/usr/bin/env python3
"""Offline APK build with an explicitly supplied existing Android SDK/JDK.

No Gradle, downloads, package installation, home cache or existing signing keys.
Pinned core-library backports must already exist in tools/cache/.
All intermediate files and the newly generated public debug key stay in build/.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
from public_fonts import font_assets
from dependency_notices import runtime_assets
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DESUGAR_VERSION = '2.1.5'
DESUGAR_SHA256 = {
    'desugar_jdk_libs': 'd8044befae095781b9a80bf1faa92edc30382d75d437476784c1bf991598a976',
    'desugar_jdk_libs_configuration': '7bc9051b3a1ec19806311dcb6aa9b9ba7ef9c22caa6f4810da55bde285fb7770',
}
MULTIDEX_VERSION = '2.0.1'
MULTIDEX_SHA256 = '42dd32ff9f97f85771b82a20003a8d70f68ab7b4ba328964312ce0732693db09'


def run(command, project=ROOT):
    subprocess.run([str(x) for x in command], cwd=project, check=True)


def write_apk_entry(archive, name, data):
    # Build time and checkout mtimes must not change the signed APK.
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, data)


def core_libraries():
    cache = ROOT / 'tools/cache/desugar_jdk_libs' / DESUGAR_VERSION
    files = []
    for artifact, expected in DESUGAR_SHA256.items():
        path = cache / f'{artifact}-{DESUGAR_VERSION}.jar'
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise SystemExit(f'Missing or changed pinned dependency: {path}; nothing was downloaded.')
        files.append(path)
    return files


def multidex_library():
    path = ROOT / f'tools/cache/multidex/{MULTIDEX_VERSION}/multidex-{MULTIDEX_VERSION}.aar'
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != MULTIDEX_SHA256:
        raise SystemExit(f'Missing or changed pinned dependency: {path}; nothing was downloaded.')
    return path


def read_sdk_aar(path, min_sdk):
    """The local resource-free WMDroid AAR, not a general AAR merger."""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise SystemExit('SDK AAR has duplicate entries or corrupt contents.')
        if any(name not in ('AndroidManifest.xml', 'classes.jar', 'R.txt', 'res/', 'LICENSE', 'NOTICE')
               and not name.startswith('assets/') for name in names):
            raise SystemExit('Offline SDK AAR mode requires the resource-free WMDroid SDK; use AGP for other AARs.')
        if archive.read('R.txt').strip():
            raise SystemExit('SDK AAR resources require AGP; nothing was signed.')
        manifest = ET.fromstring(archive.read('AndroidManifest.xml'))
        android = '{http://schemas.android.com/apk/res/android}'
        if manifest.tag != 'manifest' or manifest.get('package') != 'me.ancal.wmdroid.sdk':
            raise SystemExit('Expected the local me.ancal.wmdroid.sdk AAR.')
        uses_sdk = manifest.find('uses-sdk')
        try:
            library_min = int(uses_sdk.get(android + 'minSdkVersion')) if uses_sdk is not None else 0
        except (TypeError, ValueError):
            raise SystemExit('SDK AAR requires a numeric minSdkVersion.')
        if library_min < 14:
            raise SystemExit('SDK AAR minimum declaration is missing or below14.')
        if library_min > min_sdk:
            raise SystemExit('SDK AAR minimum exceeds the requested app minimum.')
        for node in manifest:
            if node.tag not in ('uses-sdk', 'uses-permission', 'queries', 'application'):
                raise SystemExit(f'Unsupported SDK manifest element: {node.tag}; use AGP.')
            if node.tag == 'application' and (node.attrib or len(node)):
                raise SystemExit('SDK application declarations require AGP.')
            if node.tag == 'queries':
                for query in node:
                    if query.tag == 'intent':
                        expected = {('action', 'android.intent.action.MAIN'), ('category', 'android.intent.category.LAUNCHER')}
                        if (query.attrib or len(query) != 2 or any(len(child) or set(child.attrib) != {android + 'name'} for child in query)
                                or {(child.tag, child.get(android + 'name')) for child in query} != expected):
                            raise SystemExit('Only SDK MAIN/LAUNCHER intent queries are supported; use AGP for other queries.')
                    elif query.tag not in ('package', 'provider') or len(query):
                        raise SystemExit('Unsupported SDK query; use AGP.')
        assets = {}
        for name in names:
            if not name.startswith('assets/'):
                continue
            relative = name[7:].rstrip('/')
            if (not relative or '\\' in relative or '..' in PurePosixPath(relative).parts
                    or PurePosixPath(relative).is_absolute()
                    or PurePosixPath(relative).as_posix() != relative):
                raise SystemExit(f'Invalid SDK asset path: {name}')
            if not name.endswith('/'):
                assets[relative] = archive.read(name)
        return archive.read('classes.jar'), assets, manifest


def merge_sdk_manifest(root, library):
    android = '{http://schemas.android.com/apk/res/android}'
    existing = {node.get(android + 'name') for node in root.findall('uses-permission')}
    for node in library.findall('uses-permission'):
        if node.get(android + 'name') not in existing:
            root.append(node)
            existing.add(node.get(android + 'name'))
    library_queries = library.find('queries')
    if library_queries is not None:
        queries = root.find('queries')
        if queries is None:
            queries = ET.SubElement(root, 'queries')
        def query_key(node):
            return node.tag, tuple(sorted(node.attrib.items())), tuple(sorted(query_key(child) for child in node))
        for node in library_queries:
            if node.tag == 'package' and node.get(android + 'name') == root.get('package'):
                continue
            if node.tag == 'provider' and any(
                    node.get(android + 'authorities') in provider.get(android + 'authorities', '').split(';')
                    for provider in root.findall('application/provider')):
                continue
            if not any(query_key(old) == query_key(node) for old in queries):
                queries.append(node)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--platform', default='android-35')
    parser.add_argument('--min-sdk', type=int, default=14)
    parser.add_argument('--project', type=Path, default=ROOT,
                        help='Android project containing app/src/main; outputs stay in its build/.')
    parser.add_argument('--package', default='me.ancal.wmdroid')
    parser.add_argument('--apk-name', default='WMDroid-debug.apk')
    parser.add_argument('--debug-keystore', type=Path,
                        help='Explicit local debug key, e.g. the host key for instrumentation.')
    parser.add_argument('--release-key', type=Path, help='Existing PKCS#8 signing key; never copied into the APK.')
    parser.add_argument('--release-cert', type=Path, help='Matching X.509 certificate; both release arguments are required.')
    integration = parser.add_mutually_exclusive_group()
    integration.add_argument('--without-sdk-sources', action='store_true',
                        help='Build reflection-only instrumentation without duplicating host SDK classes.')
    integration.add_argument('--sdk-aar', type=Path,
                        help='Compile against the local WMDroid AAR instead of SDK Java sources; merge its assets/manifest requirements.')
    args = parser.parse_args()
    release = args.release_key is not None or args.release_cert is not None
    if release and (args.release_key is None or args.release_cert is None or args.debug_keystore):
        parser.error('Release signing requires --release-key and --release-cert, without --debug-keystore.')
    if release and not all(p.is_file() for p in (args.release_key, args.release_cert)):
        parser.error('Release signing files must already exist.')
    if release and args.apk_name == 'WMDroid-debug.apk':
        args.apk_name = 'WMDroid-release.apk'
    if not 14 <= args.min_sdk <= 35:
        parser.error('--min-sdk must be between 14 and target SDK 35')
    if Path(args.apk_name).name != args.apk_name or not args.apk_name.endswith('.apk'):
        parser.error('--apk-name must be an APK filename, without directories')
    project = args.project.resolve()
    libraries = core_libraries()
    multidex = multidex_library()
    candidates = [p for p in (args.sdk / 'build-tools').iterdir()
                  if all((p / tool).is_file() for tool in ('aapt2', 'd8', 'zipalign', 'apksigner'))]
    if not candidates:
        raise SystemExit('No complete existing SDK build-tools; nothing was downloaded.')
    def version(path):
        return tuple(int(x) for x in path.name.split('.') if x.isdecimal())
    tools = max(candidates, key=version)
    android = args.sdk / 'platforms' / args.platform / 'android.jar'
    if not android.is_file():
        raise SystemExit(f'Missing {args.platform}/android.jar; nothing was downloaded.')
    build = project / 'build/offline'
    for name in ('generated', 'classes', 'dex'):
        intermediate = build / name
        # Remove only this tool's derived files: deleted Java classes must never
        # survive into a later APK. Source, assets and the debug key are retained.
        if intermediate.exists():
            shutil.rmtree(intermediate)
        intermediate.mkdir(parents=True, exist_ok=True)
    source = project / 'app/src/main'
    sdk_jar, sdk_assets, sdk_manifest = None, {} if args.without_sdk_sources or args.sdk_aar else font_assets(), None
    if args.sdk_aar:
        classes, sdk_assets, sdk_manifest = read_sdk_aar(args.sdk_aar, args.min_sdk)
        sdk_jar = build / 'wmdroid-sdk.jar'
        sdk_jar.write_bytes(classes)
    # AGP injects namespace into the manifest. Mirror that in this temporary copy.
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest = ET.parse(source / 'AndroidManifest.xml')
    manifest.getroot().set('package', args.package)
    if sdk_manifest is not None:
        merge_sdk_manifest(manifest.getroot(), sdk_manifest)
    manifest.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable', 'false' if release else 'true')
    if args.min_sdk < 21:
        application = manifest.getroot().find('application')
        application_name = application.get('{http://schemas.android.com/apk/res/android}name') if application is not None else None
        if application_name != 'androidx.multidex.MultiDexApplication':
            raise SystemExit('API14–20 requires android:name="androidx.multidex.MultiDexApplication" in this project manifest.')
    manifest.write(build / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    resource_inputs = []
    if (source / 'res').is_dir():
        run([tools / 'aapt2', 'compile', '--dir', source / 'res', '-o', build / 'resources.zip'], project)
        resource_inputs.append(build / 'resources.zip')
    run([tools / 'aapt2', 'link', '-o', build / 'resources.apk', '-I', android,
         '--manifest', build / 'AndroidManifest.xml', '--java', build / 'generated',
         '--min-sdk-version', str(args.min_sdk), '--target-sdk-version', '35', '--version-code', '1',
         '--version-name', '0.1.0', *resource_inputs], project)
    sdk_java = [] if args.without_sdk_sources or sdk_jar else sorted((ROOT / 'sdk/src/main/java').rglob('*.java'))
    java = sorted((source / 'java').rglob('*.java')) + sdk_java + sorted((build / 'generated').rglob('*.java'))
    multidex_classes = build / 'multidex.jar'
    with zipfile.ZipFile(multidex) as archive:
        multidex_classes.write_bytes(archive.read('classes.jar'))
    import os
    classpath = os.pathsep.join(str(path) for path in
                               [android, multidex_classes, *([sdk_jar] if sdk_jar else [])])
    run([args.jdk / 'bin/javac', '--release', '17', '-g:none' if release else '-g', '-encoding', 'UTF-8', '-classpath', classpath,
         '-d', build / 'classes', *java], project)
    # SDK d8/apksigner scripts use JAVA_HOME. This process-local value points only
    # to the explicitly authorized JDK; no shell or home configuration is loaded.
    env = os.environ.copy()
    env['JAVA_HOME'] = str(args.jdk)
    with zipfile.ZipFile(libraries[1]) as archive:
        configuration = archive.read('META-INF/desugar/d8/desugar.json')
    config = build / 'desugar.json'
    config.write_bytes(configuration)
    app_dex, library_dex = build / 'app-dex.zip', build / 'library-dex.zip'
    subprocess.run([str(tools / 'd8'), '--release' if release else '--debug', '--lib', str(android), '--min-api', str(args.min_sdk),
                    '--desugared-lib', str(config), '--output', str(app_dex),
                    *[str(p) for p in sorted((build / 'classes').rglob('*.class'))],
                    *([str(sdk_jar)] if sdk_jar else []), str(multidex_classes)],
                   cwd=project, env=env, check=True)
    # L8 supplies java.time and collection API implementations. Configuration
    # JAR classes include conversion adapters and are also library inputs.
    run([args.jdk / 'bin/java', '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.L8',
         '--release', '--lib', android, '--min-api', str(args.min_sdk),
         '--desugared-lib', config, '--output', library_dex, *libraries], project)
    # D8 intentionally forbids merging j$. library classes with application
    # classes. Keep L8 separate, as AGP does. API14–20 install the secondary DEX
    # through MultiDexApplication; API21+ load it natively.
    with zipfile.ZipFile(app_dex) as archive:
        names = sorted(n for n in archive.namelist() if n.endswith('.dex'))
        if args.min_sdk < 21 and names != ['classes.dex']:
            raise SystemExit('The API14–20 application must fit in its primary DEX. No APK was signed.')
        for name in names:
            (build / 'dex' / name).write_bytes(archive.read(name))
    next_dex = len(names) + 1
    with zipfile.ZipFile(library_dex) as archive:
        for name in sorted(n for n in archive.namelist() if n.endswith('.dex')):
            (build / 'dex' / f'classes{next_dex}.dex').write_bytes(archive.read(name))
            next_dex += 1
    dex_files = sorted((build / 'dex').glob('*.dex'))
    unsigned = build / 'unsigned.apk'
    shutil.copyfile(build / 'resources.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in dex_files:
            write_apk_entry(archive, dex.name, dex.read_bytes())
        # Application assets override library assets, matching app ownership.
        app_assets = {asset.relative_to(source / 'assets').as_posix(): asset
                      for asset in sorted((source / 'assets').rglob('*')) if asset.is_file()}
        notices = runtime_assets()
        if set(notices).intersection(app_assets) or set(notices).intersection(sdk_assets):
            raise SystemExit('Application or SDK assets cannot replace dependency notices.')
        for name, data in sorted(notices.items()):
            write_apk_entry(archive, 'assets/' + name, data)
        for name, data in sorted(sdk_assets.items()):
            if name not in app_assets:
                write_apk_entry(archive, 'assets/' + name, data)
        for name, asset in app_assets.items():
            write_apk_entry(archive, 'assets/' + name, asset.read_bytes())
    aligned = build / 'aligned.apk'
    run([tools / 'zipalign', '-f', '-p', '4', unsigned, aligned], project)
    key = args.debug_keystore.resolve() if args.debug_keystore else project / 'build/debug.keystore'
    if not release and not key.exists():
        if args.debug_keystore:
            raise SystemExit('The explicitly supplied debug key does not exist.')
        run([args.jdk / 'bin/keytool', '-genkeypair', '-keystore', key, '-alias', 'androiddebugkey',
             '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Android Debug,O=Today Launcher,C=KR',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000'], project)
    apk = project / 'build' / args.apk_name
    signing = (['--key', str(args.release_key.resolve()), '--cert', str(args.release_cert.resolve())] if release else
               ['--ks', str(key), '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android'])
    subprocess.run([str(tools / 'apksigner'), 'sign', *signing, '--out', str(apk), str(aligned)], cwd=project, env=env, check=True)
    subprocess.run([str(tools / 'apksigner'), 'verify', str(apk)], cwd=project, env=env, check=True)
    summary = {'apk': str(apk.relative_to(project)), 'bytes': apk.stat().st_size,
               'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'platform': args.platform,
               'package': args.package, 'min_sdk': args.min_sdk,
               'desugar_version': DESUGAR_VERSION, 'dex_files': len(dex_files),
               'multidex_version': MULTIDEX_VERSION,
               'dex_loading': 'legacy-multidex' if args.min_sdk < 21 else 'native-multidex',
               'dex_method_ids': [int.from_bytes(p.read_bytes()[88:92], 'little') for p in dex_files],
               'build_tools': tools.name, 'signature_verified': True, 'runtime_tested': False,
               'sdk_integration': 'aar' if sdk_jar else 'none' if args.without_sdk_sources else 'sources',
               'sdk_java_sources': len(sdk_java), 'sdk_aar_assets': len(sdk_assets),
               'sdk_aar_sha256': hashlib.sha256(args.sdk_aar.read_bytes()).hexdigest() if sdk_jar else None}
    summary['signing'] = 'release-pkcs8' if release else 'debug-keystore'
    summary['build_type'] = 'release' if release else 'debug'
    summary['java_debug_info'] = not release
    (project / ('build/release-build-report.json' if release else 'build/build-report.json')).write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary))


if __name__ == '__main__':
    main()
