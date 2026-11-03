# 링크 | 매장 주문·결제 MVP

링크는 비콘으로 매장을 찾고, 메뉴 선택부터 주문 상태 확인까지 진행하는 접근성 중심의 React Native 앱입니다. Figma **09 프로토타입**의 주문 성공 경로와 **08 V5**의 보완 화면을 바탕으로 구현했습니다. 화면별 대응은 [Figma 흐름 문서](docs/figma-flow.md)에서 확인할 수 있습니다.

## Android에서 데모 체험

APK는 Android 7.0(API 24) 이상에서 동작하며 `arm64-v8a`와 `x86_64`를 지원합니다. 개발용 서명과 앱 번들을 포함해 설치 후 Metro, 서버, 실물 비콘 없이 데모를 진행할 수 있습니다.

1. Windows에서 [APK를 빌드](#windows에서-릴리스-apk-빌드)하거나 [GitHub Releases](https://github.com/minjun45/link-mvp/releases)에 첨부된 APK를 내려받아 Android 기기에 설치합니다.
2. **링크 RN**을 열고 첫 사용법을 확인하거나 건너뜁니다. 탐색 화면에서 **데모로 체험하기**를 누릅니다.
3. 매장을 확인하고 메뉴·옵션·수량을 고릅니다. 상품을 담으면 메뉴 목록으로 돌아오고, 하단의 고정 장바구니 버튼으로 주문을 확인할 수 있습니다.
4. **테스트 결제 완료**를 누른 뒤 주문 완료 화면에서 **주문 상태 확인**을 선택합니다. 데모는 주문 후 약 25초에 준비 완료로 바뀝니다.
5. 같은 매장에서 다시 주문하면 이전 주문을 재사용하는 경로를 확인할 수 있습니다. 결제 화면에서는 취소·실패·시간 초과도 재현할 수 있습니다.

기기 내 데모는 결제와 제조 상태를 시뮬레이션합니다. 장바구니, 주문 내역, 사용법 완료 여부와 설정은 기기에 저장됩니다. 접근성 설정에서 큰 글씨, 고대비, 다크 모드와 음성 안내를 확인할 수 있습니다.

## 개발 환경

- Node.js 22.11 이상, npm, JDK 21
- Android Studio 또는 Android SDK. Android 플랫폼 37, Build Tools 37.0.0, NDK 27.1.12297006, CMake 3.22.1을 사용합니다.
- React Native 0.88.1, React 19.3.0, TypeScript, Hermes, Gradle 9.4.1

저장소 루트에서 의존성을 설치하고 Metro를 실행합니다.

```sh
npm ci
npm start
```

별도 터미널에서 Android 에뮬레이터를 켜거나 USB 디버깅을 활성화한 기기를 연결하고, 저장소 루트에서 실행합니다.

```sh
npm run android
```

현재 검증 대상은 Android입니다. `ios/`에는 React Native 템플릿이 있으며, BLE·TTS 등 이 앱의 네이티브 기능은 Android에 구현되어 있습니다.

## Windows에서 릴리스 APK 빌드

처음 포터블 도구를 준비할 때는 Android Studio의 SDK Manager에서 SDK 라이선스를 수락하고 `ANDROID_HOME`을 해당 SDK 경로로 지정합니다. 저장소 루트에서 `npm ci`를 실행한 후 다음 명령을 사용합니다.

```powershell
python tools/prepare-toolchain.py
.\Build-Android.ps1
```

도구가 이미 준비된 환경에서는 `.\Build-Android.ps1`만 실행하면 됩니다. 스크립트는 한글 경로에서 발생하는 네이티브 빌드 문제를 피하려고 임시 경로에서 빌드하고, 결과를 `artifacts/link-react-native-v5.apk`에 복사합니다. 팀 QA용 APK는 GitHub Release의 **Assets**에 첨부합니다. APK와 로컬 SDK는 Git에서 제외됩니다.

## 외부 주문 서버 연결

외부 주문·결제 API를 검증하려면 **설정 → 개발·테스트 연결**에 서버 기본 주소를 입력합니다. 릴리스 빌드는 HTTPS를 사용합니다. 디버그 빌드에서는 Android 에뮬레이터에서 PC의 서버에 접근할 때 `http://10.0.2.2:3000`을 사용할 수 있습니다. 기기 내 데모와 서버 주문 내역은 별도로 관리됩니다.

## 프로젝트 구조

| 경로 | 역할 |
| --- | --- |
| [`App.tsx`](App.tsx), [`src/ui/`](src/ui/) | 화면, 테마, 접근성 설정 및 공통 UI |
| [`src/core/`](src/core/) | 주문 흐름, 옵션·수량·가격 검증 |
| [`src/data/`](src/data/), [`src/services/`](src/services/) | 데모 카탈로그, 로컬 저장, 결제·주문 API |
| [`src/native/`](src/native/), [`android/`](android/) | Android BLE, 진동, TTS, 음성 검색과 결제 창 연결 |
| [`__tests__/`](__tests__/) | 화면, 흐름, 결제, 승인 처리 테스트 |

## 검증

```sh
npm run typecheck
npm test
```

현재 TypeScript 검사, Jest 테스트 59개와 Android 릴리스 빌드를 통과했습니다. Android 실기기 QA 항목에는 BLE 매장 탐색, TalkBack 읽기 순서, 글꼴 배율별 레이아웃, 외부 테스트 결제 앱 전환이 남아 있습니다.
