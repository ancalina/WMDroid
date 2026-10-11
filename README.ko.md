# WMDroid

[English](README.md)

Android HOME 런처. `me.ancal.wmdroid`, minSdk 14, targetSdk 35.
Windows Mobile 스타일 UI를 Android View·Canvas·시스템 API로 구현한다.
Windows CE 바이너리는 실행하지 않는다.

## 빌드

이 저장소는 SDK AAR을 사용한다. SDK·예제·리소스 추출기 소스는 별도 저장소다.
별도로 빌드하거나 배포받은 `WMDroid-sdk.aar`을 `libs/WMDroid-sdk.aar`에 넣는다.
SDK AAR과 의존성 바이너리는 이 저장소에 포함하지 않는다.

Python 3, JDK 17 이상, Android SDK의 android-35와 build-tools가 필요하다.
배포 빌드 검증에는 PATH의 `openssl`도 필요하다. 아래 빌드 도구는 다운로드하지 않는다.
`tools/cache/**/dependencies.json`에 지정된 파일을 해당 경로에 준비한다.
파일의 SHA256이 지정값과 다르면 빌드가 중단된다.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar
```

결과: `build/WMDroid-debug.apk`. 개발키는 `build/debug.keystore`에 생성한다.
Gradle도 `libs/WMDroid-sdk.aar`을 사용한다. Gradle은 의존성을 다운로드할 수 있다.

배포 빌드는 기존 PKCS#8 키와 X.509 인증서를 모두 지정한다.

```sh
python3 tools/build_apk.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --sdk-aar libs/WMDroid-sdk.aar \
  --release-key /path/to/signing.pk8 --release-cert /path/to/signing.x509.pem
python3 tools/check_release_build.py --sdk /path/to/Android/Sdk --jdk /path/to/jdk \
  --cert /path/to/signing.x509.pem
```

결과: `build/WMDroid-release.apk`. `debuggable=false`, Java 디버그 정보 제거,
D8 release 모드를 적용한다. 검증에는 같은 소스·AAR로 만든 debug APK도 필요하다.
서명키는 소스나 APK에 포함하지 않는다.

## 리소스와 권한

설정 → 개인 → 리소스팩에서 ZIP을 가져오거나 해제한다.
Microsoft/OEM 폰트·사운드·배경·상세 아이콘은 배포 APK에 포함하지 않는다.
팩이 없으면 대체 이미지·기본 색을 사용하며, 검토해 유지한 단순 상태 아이콘과 기존에 수작업으로 다시 그린 Today 무선·잠금 아이콘 2개는 SDK 내장 경로로 그린다. SDK의 OFL Gulim은 별도 OFL 고지를 따른다.
원본 에셋이 필요한 팩은 사용자가 보유한 미디어에서 별도 추출기로 만든다.
팩을 적용해도 모든 화면·동작의 원본 일치를 보장하지 않는다.

일정·연락처·알림·잠금 등은 Android 버전과 사용자가 허용한 권한에 따라 동작한다.
WinCE GDI와 Android 글꼴 렌더링 차이, 미이식 동작이 남아 있다.

## 라이선스

런처: GPL-3.0-or-later. SDK: Apache-2.0.
적용 범위는 [LICENSES.md](LICENSES.md), 고지는 [NOTICE](NOTICE),
의존성과 미확인 범위는 [DEPENDENCIES.md](DEPENDENCIES.md)를 참조한다.
추출한 리소스의 권리는 원저작자에게 있다.
내보내기 검사 통과는 출처·corresponding source 검토 완료를 뜻하지 않는다.
