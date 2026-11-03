# 링크 React Native · Android MVP

`테크포임팩트 5팀 링크 — 매장 주문·결제 화면 설계.fig`의 **09 프로토타입 · 주문 성공 경로**를 기본으로 구현했습니다. **08 V5 · 피드백 반영**의 호환되는 보완 흐름을 포함합니다. 화면과 상태 관리는 React Native/TypeScript이며 Android의 BLE·진동·TTS·음성 검색·결제 창만 Kotlin TurboModule로 연결합니다. 기존 `MVP/android` Kotlin 앱과는 별도 앱입니다.

## 설치해서 체험

빌드 결과는 `artifacts/link-react-native-v5.apk`입니다. Android 7(API 24) 이상, **arm64-v8a / x86_64**를 지원합니다. 개발용 서명으로 만든 독립 실행 APK이며 Metro나 PC 서버 없이 데모 주문을 체험할 수 있습니다. 배포용 서명은 별도로 설정해야 합니다.

1. APK를 Android에 설치해 `링크 RN`을 실행합니다.
2. 처음 4단계 사용법을 확인하거나 건너뜁니다.
3. 실제 비콘이 없다면 탐색·권한 화면의 **데모로 체험하기**를 누릅니다. 실물 탐색 결과를 가짜 매장으로 바꾸지 않습니다.
4. 매장 확인 → 메뉴 → 옵션 → 담기 → 메뉴의 고정 장바구니 버튼 → 결제를 진행합니다.
5. 테스트 결제 완료 → **주문 완료 화면** → 주문 상태 확인을 누릅니다. 데모 제조 상태는 약 25초 후 준비 완료로 바뀝니다.
6. 같은 매장에서 새 주문을 시작하면 지난 주문 재주문 경로가 표시됩니다.

실제 청구·매장 주문 전송·제조 완료 푸시는 연결되어 있지 않습니다. 기본 기기 내 데모는 결제 취소·실패·시간 초과도 재현합니다. 결제와 주문 내역, 장바구니, 설정, 사용법 완료 여부는 AsyncStorage에 저장합니다.

## 개발 실행

Node 22.11 이상(현재 검증은 Node 24), JDK 21, Android SDK가 필요합니다.

```powershell
cd MVP/react-native
npm ci
npm run start
# 별도 터미널, Android 에뮬레이터 또는 USB 디버깅 기기 연결
npm run android
```

공식 React Native 0.88.1 / React 19.3.0 / Hermes / New Architecture를 사용합니다. 템플릿 기준 compileSdk 37, targetSdk 36, Build Tools 37.0.0, NDK 27.1.12297006, CMake 3.22.1, Gradle 9.4.1입니다.

이 작업 환경의 포터블 도구로 독립 APK를 다시 빌드하려면 다음을 실행합니다.

```powershell
# 기존 MVP/android/.tools에 JDK·기본 SDK가 준비된 환경
python tools/prepare-toolchain.py
.\Build-Android.ps1
# 같은 임시 빌드 작업 공간에서 Lint
.\Build-Android.ps1 -Task lintRelease
```

`Build-Android.ps1`은 Windows의 한글 경로/네이티브 빌드 문제를 피하려고 소스를 `%TEMP%/link-react-native-v5`에 복사합니다. 원본 소스는 이 폴더에 유지되며 APK만 `artifacts`로 가져옵니다. 일반 Android Studio 환경에서는 `android` 프로젝트를 열고 SDK 위치를 설정해도 됩니다. 기본 debug 빌드는 Metro, release 빌드는 JS/Hermes 번들을 포함합니다.

## 기존 주문 서버 연결

설정 → 개발·테스트 연결에 **HTTPS 서버 기본 주소**를 입력합니다. 연결 변경 시 화면의 장바구니와 최근 주문 표시를 초기화하며 진행 중인 결제가 있으면 변경을 차단합니다. 기기 내 원장과 서버 원장은 별도입니다.

기존 `MVP/server.mjs`의 매장·메뉴·견적·결제·주문 API를 사용합니다. debug 빌드에서는 `http://10.0.2.2:3000` 등 로컬 개발 주소도 허용합니다. 실제 휴대폰의 `localhost`는 PC가 아니므로 HTTPS 개발 서버 또는 `adb reverse`가 필요합니다. release는 HTTPS만 허용합니다.

서버의 `PAYMENT_PROVIDER=mock`은 테스트 결제입니다. `kakao-sandbox`는 서버의 Kakao 테스트 키와 외부 HTTPS 콜백 설정이 있어야 결제 창으로 연결됩니다. Kakao 승인 결과는 **서버에서 조회해 확인**하고 앱 복귀 자체를 결제 성공으로 취급하지 않습니다. 키를 앱에 넣지 않습니다. 서버 환경 설정은 상위 MVP 문서를 참조하세요.

## 구조와 검증

- `App.tsx`, `src/ui`: 화면, 큰 글씨·고대비·다크 모드, 고정 CTA/장바구니, 도움말/다시 듣기
- `src/core/flow.ts`: Figma 화면 ID를 사용하는 순수 상태 전이
- `src/core/domain.ts`: 옵션·수량·가격 검증
- `src/services`: 저장, 원장, API, 비동기 흐름 제어
- `src/native`, `android/.../LinkDeviceModule.kt`: Android 기능 연결
- `docs/figma-flow.md`: Figma 프레임과 구현 대응, 의도적인 데모 처리

```powershell
npm run typecheck
npm test
# 기존 서버 변경도 함께 검증
cd ..
npm test
```

자동 테스트는 화면 렌더링, 하단 고정 영역, 핵심 전환, 결제 중복 방지, 오류와 복구를 검증합니다. 실제 TalkBack 조작·BLE 신호·Kakao 앱 전환·실기기 키보드/폰트 배율은 Android 실기기에서 추가 확인해야 합니다. iOS 템플릿은 포함하지만 이번 구현·빌드 대상은 Android이며 iOS용 BLE/TTS 네이티브 모듈은 구현하지 않았습니다.
