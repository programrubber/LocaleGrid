# LocaleGrid

LocaleGrid는 PyCharm 기반 IDE 플러그인으로, 프로젝트 안의 locale JSON 리소스를 그리드 형태로 비교하고 수정할 수 있도록 돕습니다.
기본 대상 구조는 `locales/{locale}/{category}.json`이며, `locales/{locale}/{category}_{locale}.json` 패턴도 지원합니다. 같은 category에 속한 여러 locale 파일을 하나의 표로 묶어 보여줍니다.

기존 `secui-version-checker` 프로젝트를 기반으로 가져왔지만, 현재 목표는 `PLAN.md`에 정의된 locale JSON 편집 MVP 구현입니다.

## LocaleGrid 개요

LocaleGrid는 개발자가 여러 언어의 JSON 번역 파일을 직접 열어 비교하고 수정하는 비용을 줄이기 위한 플러그인입니다.

예를 들어 `locales/ko/login.json` 파일을 열면, 플러그인은 `login`을 category로 판단하고 다음 파일들을 함께 찾습니다.

```text
locales/ko/login.json
locales/en/login.json
locales/ja/login.json
locales/vi/login.json
```

`locales/ko/common_ko.json`처럼 파일명이 `{category}_{locale}.json`이고 폴더 locale과 suffix가 일치하면, 플러그인은 `common`을 category로 판단하고 다음 파일들을 함께 찾습니다.

```text
locales/ko/common_ko.json
locales/en/common_en.json
locales/ja/common_ja.json
locales/vi/common_vi.json
```

이후 각 JSON 파일의 key를 dot path 형태로 펼친 뒤, locale별 value를 하나의 그리드에서 표시합니다.

```text
key            ko       en       ja       vi
login.title    ...      ...      ...      ...
login.button   ...      ...      ...      ...
```

중첩 JSON은 다음처럼 dot path로 표시합니다.

```json
{
  "login": {
    "title": "로그인",
    "button": "로그인하기"
  }
}
```

```text
login.title
login.button
```

## 주요 기능

- JSON 파일을 열었을 때 `JSON` 원본 탭과 `다국어 에디터` 커스텀 에디터 탭 표시
- `locales/{locale}/{category}.json`, `locales/{locale}/{category}_{locale}.json` 구조 자동 인식
- locale 디렉터리 자동 감지
- 프로젝트 설정에서 locale 목록과 순서 수동 지정
- 같은 category의 locale 파일들을 key union으로 병합
- 중첩 JSON을 dot path key로 펼쳐 표시
- 문자열 value 직접 편집
- key 추가, rename, 삭제 후보 표시
- 검색어와 매칭된 텍스트를 셀 안에서 강조 표시
- 빈 value warning 표시
- dot path 충돌 및 중복 key error 검증
- 누락 locale 파일 감지
- 저장 시 누락 locale 파일 생성 여부 확인
- 저장 전 변경 요약 표시
- 2-space indent 기준 JSON 저장
- `__section__` 같은 예외키를 root-level entry로 보존하고 필요 시 테이블에서 추가/편집

## LocaleGrid 확인 방식

LocaleGrid는 별도 메뉴 액션이 아니라 JSON 파일의 커스텀 에디터 탭으로 동작합니다.

1. 프로젝트에서 `locales/{locale}/{category}.json` 또는 `locales/{locale}/{category}_{locale}.json` 형태의 JSON 파일을 엽니다.
2. IDE 에디터 상단 탭에서 `다국어 에디터` 탭을 선택합니다.
3. 현재 파일의 category와 같은 이름을 가진 다른 locale 파일을 자동으로 로드합니다.
4. 각 locale 파일의 key를 병합해 그리드로 표시합니다.

기본 locale root는 `locales`입니다.
설정은 `Settings > Tools > LocaleGrid`에서 변경할 수 있습니다.

설정 항목:

- `locale 루트 경로`: 기본값 `locales`
- `locale 표시 순서`: 쉼표로 구분한 locale 목록. 비어 있으면 자동 감지
- `예외키`: 쉼표로 구분한 root-level 예외키 목록. 기본값 `__section__`
- `JSON 들여쓰기`: 저장 시 JSON indent. 기본값 `2`

예외키는 `Settings > Tools > LocaleGrid` 또는 다국어 에디터 상단의 `예외키 설정` 버튼에서 프로젝트별로 설정합니다.
예외키는 중복될 수 있으며 각 Locale 파일별 위치를 기준으로 저장합니다. 구분 또는 설명을 위한 entry에 사용합니다.

## LocaleGrid 확인 프로세스

1. 현재 연 JSON 파일이 `locales/{locale}/{category}.json` 또는 `locales/{locale}/{category}_{locale}.json` 구조인지 확인합니다.
2. category 이름을 현재 JSON 파일명에서 추출합니다. `{category}_{locale}.json` 패턴은 파일명 suffix와 폴더 locale이 일치할 때만 적용합니다.
3. locale 목록을 설정값 또는 `locales/` 하위 디렉터리에서 감지합니다.
4. 같은 category의 locale별 JSON 파일을 로드합니다.
5. JSON object를 dot path key/value 목록으로 펼칩니다.
6. 모든 locale 파일의 key union을 생성합니다.
7. 문자열 value는 편집 가능 상태로 표시합니다.
8. number, boolean, array, object leaf 등 MVP에서 지원하지 않는 value는 readonly로 표시합니다.
9. 빈 value는 warning으로 표시합니다.
10. 중복 key와 dot path 충돌은 error로 표시합니다.
11. 저장 시 메모리에서 JSON을 먼저 재생성하고 검증합니다.
12. error가 없으면 변경 요약과 누락 locale 파일 생성 여부를 확인한 뒤 저장합니다.

## LocaleGrid 팝업

LocaleGrid MVP에서 사용하는 팝업은 다음 흐름에 맞춰 동작합니다.

- `행 추가`: 새 dot path key 입력
- `키 이름 변경`: 선택한 key의 새 이름 입력 및 충돌 검증
- `행 삭제`: 선택한 key를 삭제 후보로 표시
- `저장`: 변경 요약 표시 및 저장
- 누락 locale 파일이 있고 입력된 값이 있으면 파일 생성 여부 확인
- 저장 차단 error가 있으면 error 메시지 표시
- 빈 value warning은 표시하되 사용자가 계속 진행하면 저장 가능

저장 변경 요약 항목:

- 추가 key 수
- 수정 key 수
- 삭제 key 수
- 생성할 locale 파일 목록
- 저장 대상 파일 목록
- warning 수
- 저장 차단 error 여부

## 개발 모드 실행

1. 우측 Gradle 메뉴 선택
2. Tasks > intellij > runIde 실행

또는 터미널에서 실행합니다.

```powershell
.\gradlew.bat runIde
```

## 빌드 실행

### 수동 빌드
1. `build.gradle.kts`의 `version` 값을 변경하고 커밋
2. 우측 Gradle 메뉴 선택
3. Tasks > intellij > buildPlugin 실행
4. 빌드 결과물은 `build/distributions` 아래에 zip 파일로 생성

또는 터미널에서 실행합니다.

```powershell
.\gradlew.bat build
```

### 자동 마이너 버전 상향 및 릴리즈 빌드 (추천)
마이너 버전을 자동으로 1 올리고 빌드를 수행한 뒤, 최종 배포용 zip 파일을 루트 `release` 디렉토리로 이동시키고 이전 릴리즈를 정리해주는 자동화 스크립트를 제공합니다.

터미널에서 아래 스크립트를 실행하면 전체 릴리즈 패키징 과정이 자동으로 완료됩니다.

```powershell
.\bump-minor-release.ps1
```

## PyCharm 적용 방법

1. Settings > Plugins 진입
2. 기어 아이콘 클릭
3. Install Plugin from Disk 선택
4. `build/distributions` 아래의 zip 파일 선택
5. IDE 재시작

## 프로젝트 구조

```text
src/main/java/com/localegrid/core
  JSON flatten/unflatten, 검증, 로드/저장 로직

src/main/java/com/localegrid/editor
  FileEditorProvider 및 Swing 그리드 UI

src/main/java/com/localegrid/model
  TranslationTable, row, value, diagnostic 모델

src/main/java/com/localegrid/settings
  프로젝트 단위 설정
```

## TODO List

[O] 1. 기존 secui-version-checker 코드 제거 및 LocaleGrid 패키지 구조로 재구성

[O] 2. `locales/{locale}/{category}.json` 파일 인식 구조 추가

[O] 3. JSON 커스텀 에디터 탭 등록

[O] 4. dot path flatten/unflatten 기본 로직 추가

[O] 5. locale별 key union 병합 로직 추가

[O] 6. 문자열 value 편집 그리드 UI 추가

[O] 7. key 추가, rename, 삭제 후보 기능 추가

[O] 8. 빈 value warning 및 dot path 충돌 error 검증 추가

[O] 9. 프로젝트 설정 화면 추가

[] 10. 저장 전 line diff 수준의 변경 요약 고도화

[] 11. readonly value 표시 개선

[x] 12. 예외키 보존 및 설정 UI 개선

[] 13. 단위 테스트 추가

[] 14. 샌드박스 프로젝트에서 수동 동작 검증

## 참고 문서

상세 MVP 범위와 구현 기준은 [PLAN.md](PLAN.md)를 참고합니다.
