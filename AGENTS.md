## 구현 문서

- `PLAN.md`를 기준으로 구현한다.
- 사용자-facing 문서는 `README.md`의 용어와 흐름을 따른다.
- 구현 계획을 수정할 때는 `IMPLEMENTATION_PLAN.md`와 `IMPLEMENTATION_PLAN.html`을 함께 수정한다.

## 응답 지침

- 영어로 요청받지 않는 한 한국어로 답한다.
- 작업을 수행한 뒤에는 무엇을 했는지 최소 1줄로 요약한다.
- 과도한 설명은 피하고 필요한 내용만 전달한다.

## 프로젝트 개요

- 프로젝트명: `LocaleGrid`
- 목적: PyCharm/IntelliJ 기반 IDE에서 `locales/{locale}/{category}.json` 번역 JSON을 그리드로 비교/편집하는 플러그인
- 주요 구현 위치:
  - `src/main/java/com/localegrid/core`: JSON 처리, 검증, 로드/저장
  - `src/main/java/com/localegrid/editor`: 커스텀 에디터 및 그리드 UI
  - `src/main/java/com/localegrid/model`: 테이블/행/값/진단 모델
  - `src/main/java/com/localegrid/settings`: 프로젝트 설정

## 빌드 및 실행

- 개발 실행:

```powershell
.\gradlew.bat runIde
```

- 현재 환경에서 정상 동작한 개발 실행:

```powershell
$env:JAVA_HOME='C:\Users\rafal\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat runIde --console=plain
```

- 빌드:

```powershell
.\gradlew.bat build
```

- 플러그인 zip 산출물 위치:

```text
build/distributions
```

## 필요 조건

- JDK 17이 필요하다.
- 로컬 `JAVA_HOME`이 JDK 17을 가리키지 않으면 Gradle 빌드가 실패할 수 있다.
- 저장소의 `jdk/jdk-17/jbr.zip`을 사용할 경우 압축을 푼 실제 JDK/JBR 루트 경로를 Gradle에 지정해야 한다.

## 구현 주의사항

- 기본 대상 구조는 `locales/{locale}/{category}.json`이다.
- JSON 파일을 열었을 때 기본 에디터 옆 `Locale Grid` 커스텀 에디터 탭으로 동작해야 한다.
- MVP에서는 문자열 value만 편집 가능하게 처리한다.
- number, boolean, array, object leaf는 readonly 또는 unsupported로 표시한다.
- 빈 value는 warning이며 저장 차단 사유가 아니다.
- 중복 key와 dot path 충돌은 error이며 저장을 차단한다.
- 저장 전에는 메모리에서 JSON을 재생성하고 검증한다.
- JSON 저장 포맷은 기본 2-space indent를 사용한다.
