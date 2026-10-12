# WMDroid

[English](README.md)

Windows Mobile 스타일을 Android View, Canvas와 시스템 API로 구현한 Android HOME 런처입니다. 앱 ID는 `me.ancal.wmdroid`이며, minSdk 14, targetSdk 35를 사용합니다. Windows CE 바이너리는 실행하지 않습니다.

## 빌드

이 저장소는 SDK AAR을 사용합니다. SDK, 예제와 리소스 추출기 소스는 별도 저장소에 있습니다. 직접 빌드하거나 배포받은 `WMDroid-sdk.aar`을 `libs/WMDroid-sdk.aar`에 넣으세요. SDK AAR과 의존성 바이너리는 이 저장소에 포함하지 않습니다.

Python 3, JDK 17 이상, Android SDK의 android-35와 build-tools가 필요합니다. 배포 빌드 검증에는 PATH에서 실행할 수 있는 `openssl`도 필요합니다.

아래 Python 빌드 도구는 파일을 다운로드하지 않습니다. `tools/cache/**/dependencies.json`에 지정된 파일을 해당 경로에 준비하세요. 파일의 SHA256이 지정값과 다르면 빌드를 중단합니다.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar
```

`build/WMDroid-debug.apk`가 생성됩니다. 개발용 서명키는 `build/debug.keystore`에 생성합니다. Gradle 빌드도 `libs/WMDroid-sdk.aar`을 사용하며, 의존성을 다운로드할 수 있습니다.

배포 빌드에는 기존 PKCS#8 키와 X.509 인증서를 모두 지정하세요.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar \
  --release-key /path/to/signing.pk8 --release-cert /path/to/signing.x509.pem
python3 tools/check_release_build.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --cert /path/to/signing.x509.pem
```

`build/WMDroid-release.apk`가 생성됩니다. 배포 빌드는 `debuggable=false`와 D8 release 모드를 적용하고 Java 디버그 정보를 제거합니다. 검증에는 같은 소스와 AAR로 만든 debug APK도 필요합니다. 서명키는 소스나 APK에 포함하지 않습니다.

## 리소스와 권한

설정 → 개인 → 리소스팩에서 ZIP을 가져오거나 해제할 수 있습니다. Microsoft/OEM 폰트, 사운드, 배경과 상세 아이콘은 배포 APK에 포함하지 않습니다.

팩이 없으면 대체 이미지와 기본 색을 사용합니다. 검토 후 유지한 단순 상태 아이콘과 기존에 수작업으로 다시 그린 Today 무선 및 잠금 아이콘 2개는 SDK 내장 경로로 그립니다. SDK의 OFL Gulim에는 별도 OFL 고지가 적용됩니다.

원본 에셋이 필요한 팩은 사용자가 보유한 미디어에서 별도 추출기로 만듭니다. 팩을 적용해도 모든 화면과 동작이 원본과 일치하는 것은 아닙니다.

일정, 연락처, 알림과 잠금 등의 기능은 Android 버전과 사용자가 허용한 권한에 따라 동작합니다. WinCE GDI와 Android의 글꼴 렌더링 차이와 아직 이식하지 않은 동작이 남아 있습니다.

## 라이선스

런처는 GPL-3.0-or-later, SDK는 Apache-2.0을 사용합니다. 적용 범위는 [LICENSES.md](LICENSES.md), 고지는 [NOTICE](NOTICE), 의존성과 확인이 필요한 사항은 [DEPENDENCIES.md](DEPENDENCIES.md)를 참고하세요.

추출한 리소스의 권리는 원저작자에게 있습니다. 내보내기 검사를 통과해도 출처와 대응 소스(corresponding source) 검토가 완료된 것은 아닙니다.
