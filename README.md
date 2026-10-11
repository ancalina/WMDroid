# WMDroid

[한국어](README.ko.md)

Android HOME launcher. `me.ancal.wmdroid`, minSdk 14, targetSdk 35.
It implements a Windows Mobile-style UI with Android Views, Canvas and system APIs.
It does not execute Windows CE binaries.

## Build

This repository consumes the SDK AAR. SDK, example and resource-extractor sources
are in separate repositories. Place a separately built or distributed
`WMDroid-sdk.aar` in `libs/WMDroid-sdk.aar`.
The SDK AAR and dependency binaries are not included in this repository.

Requires Python 3, JDK 17 or newer, and Android SDK android-35 and build-tools.
Release verification also requires `openssl` on PATH. The build tools below do
not download files. Prepare the files specified by
`tools/cache/**/dependencies.json` at their listed paths.
The build stops if a file's SHA256 differs from its pinned value.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar
```

Output: `build/WMDroid-debug.apk`. A development key is created in
`build/debug.keystore`. Gradle also uses `libs/WMDroid-sdk.aar` and may download
its dependencies.

Release builds require both an existing PKCS#8 key and an X.509 certificate.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar \
  --release-key /path/to/signing.pk8 --release-cert /path/to/signing.x509.pem
python3 tools/check_release_build.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --cert /path/to/signing.x509.pem
```

Output: `build/WMDroid-release.apk`. The build uses `debuggable=false`, strips
Java debug information and uses D8 release mode. Verification also needs a debug
APK built from the same sources and AAR. Signing keys are not included in sources
or APKs.

## Resources and permissions

Import or remove a ZIP under Settings → Personal → Resource pack.
Distribution APKs exclude Microsoft/OEM fonts, sounds, backgrounds and detailed
icons. Without a pack, fallback images and default colors are used. Retained
simple status icons and two previously hand-redrawn Today wireless/lock icons
use SDK built-in drawing paths. The SDK's OFL Gulim retains its separate OFL notices.
Packs requiring original assets are created with the separate extractor from
user-owned media. Applying a pack does not guarantee native parity for every
screen or behavior.

Calendar, contacts, notifications and locking depend on Android version and user
granted permissions. Differences between WinCE GDI and Android font rendering
and unported behavior remain.

## License

Launcher: GPL-3.0-or-later. SDK: Apache-2.0.
See [LICENSES.md](LICENSES.md) for scope, [NOTICE](NOTICE) for notices and
[DEPENDENCIES.md](DEPENDENCIES.md) for dependencies and unverified areas.
Extracted resources retain their owners' rights.
Passing export checks does not complete provenance or corresponding-source review.
