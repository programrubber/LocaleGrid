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
- 검색어와 매칭된 텍스트를 셀 안에서 강조하고 `현재 / 전체` 건수와 이전·다음 결과 탐색 제공 (검색창에서 키보드 ↑/↓ 및 Enter/Shift+Enter로 일치 대상 조절)
- 검색창의 필터 버튼을 켠 경우에만 검색 결과 Row만 표시
- 상세 value 연속 입력 시 debounce를 적용해 검증과 테이블 갱신 부하 완화
- 빈 value warning 표시
- ko/en/ja/vi 고정 규칙과 CLDR 보완 규칙 기반 예상 문자 체계 위반 진단 표시
- dot path 충돌 및 중복 key error 검증
- 누락 locale 파일 감지
- 저장 시 누락 locale 파일 생성 여부 확인
- 저장 전 변경 요약 표시
- 2-space indent 기준 JSON 저장
- `__section__` 같은 예외키를 root-level entry로 보존하고 필요 시 테이블에서 추가/편집
- 사내 호스팅 LLM(Qwen 등) 및 OpenAI 호환 모델 연동 다국어 번역 제안 (하단 상세 패널의 `[AI 번역 제안]` 버튼으로 입력된 언어 문맥을 종합한 번역 미리보기 표시, 추천 칩을 클릭하면 바로 반영)

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
- `문자 체계 검사`: 지원 locale 값 검사 사용 여부. `검사 사용` 기본값은 켬
- `문자 위반 처리`: 기본값 `경고 (저장 가능)`, 선택값 `에러 (저장 차단)`
- `사내 AI 번역 제안 (LLM 연동)`:
  - `기능 활성화`: 하단 상세 패널에서 보라색 AI 번역 제안 버튼 표시 여부. 참조 문장과 편집 가능한 빈 언어 항목이 모두 있을 때만 활성화되며, 모든 언어가 입력되면 비활성화됩니다.
  - 번역 제안은 앞쪽 별 아이콘이 있는 작은 보라색 칩으로 표시됩니다. 문구 길이에 맞춰 너비를 정하며, 긴 문구는 말줄임하고 툴팁으로 전체를 보여줍니다. 칩 클릭 또는 포커스 상태에서 Enter/Space로 적용합니다. 닫기 버튼은 적용하지 않고 제안만 닫습니다.
  - 하단의 카테고리·행 수·편집·오류·경고 상태 정보는 항상 유지하며, AI 진행·결과·실패 안내는 `AI 번역 제안` 버튼 바로 오른쪽에 표시합니다. 공간이 부족하면 AI 안내가 말줄임되며 툴팁으로 확인할 수 있습니다.
  - `엔드포인트 URL`: OpenAI 호환 `/v1/chat/completions` API URL (예: `http://localhost:8000/v1/chat/completions`)
  - `모델 식별자`: 배포된 LLM 모델명 (예: `qwen3.6-27b`, `deepseek-v3`, `llama-3.3`, `gpt-4o-mini` 등)
  - `API Key`: 사내 프록시/게이트웨이 인증 토큰 (필요 시)
  - `타임아웃(초)`: 응답 대기 시간 (기본값: 30초)
  - `연결 테스트`: 실시간 응답 지연 시간(ms) 측정 및 정상 작동 여부 확인

`locale 루트 경로`, `locale 표시 순서`, `예외키`는 테이블 구성을 바꾸는 구조 설정입니다. 열린 LocaleGrid 에디터에 미저장 변경이 있으면 구조 설정 적용을 차단합니다. 적용에 성공하면 프로젝트의 열린 LocaleGrid 에디터를 모두 새 설정으로 재로드하며, 재로드에 실패한 에디터는 이전 테이블을 제거하고 로드 실패 상태를 표시합니다. JSON 들여쓰기와 문자 체계 검사 같은 비구조 설정은 테이블을 유지한 채 진단을 다시 계산합니다.

예외키는 `Settings > Tools > LocaleGrid` 또는 다국어 에디터 상단의 `예외키` 버튼에서 프로젝트별로 설정합니다.
예외키는 중복될 수 있으며 각 Locale 파일별 위치를 기준으로 저장합니다. 구분 또는 설명을 위한 entry에 사용합니다.

## 번역 문자 검사

LocaleGrid는 문자열 value에 다른 문자 체계가 섞였는지 확인하는 검사를 제공합니다. ko/en/ja/vi에는 확정 내장 규칙을 우선 적용하고, 그 외 올바른 BCP 47 locale에는 CLDR likely-subtags가 제공하는 예상 native Script로 보완 규칙을 만듭니다.

| locale | 허용 문자 |
| --- | --- |
| 한국어 `ko` | 한글 + 라틴 문자(Latin) + 공통 문자(Common) + 상속/결합 문자(Inherited) |
| 영어 `en` | 라틴 문자(Latin) + 공통 문자(Common) + 상속/결합 문자(Inherited) |
| 일본어 `ja` | 히라가나 + 가타카나 + 한자 + 라틴 문자(Latin) + 공통 문자(Common) + 상속/결합 문자(Inherited) |
| 베트남어 `vi` | 라틴 문자(Latin) + 공통 문자(Common) + 상속/결합 문자(Inherited) |

- locale 태그는 대소문자와 `_`/`-`를 정규화해 BCP 47 형식으로 해석합니다. 지역 변형에는 고정 규칙을 우선 적용하고, `ja-Latn`, `sr-Latn`처럼 명시된 Script subtag는 존중합니다.
- ko/en/ja/vi 외 locale은 명시 Script가 없을 때 CLDR likely-subtags에서 예상 native Script를 구하며, 결정된 Script와 Latin/Common/Inherited를 허용해 검사합니다.
- 모든 Unicode 숫자, Common 범주의 공백·문장부호·기호, Inherited 결합 문자를 허용합니다.
- 런타임 Unicode 버전보다 새로운 보조 평면 이모지를 위해 U+1F000–U+1FAFF 범위만 제한적 호환 fallback으로 허용합니다.
- Java 정규표현식으로 잠재 위반을 빠르게 찾고, Java 17이 `UNKNOWN`으로 보는 코드포인트만 IDE 번들 ICU의 `UScript`/`UCharacter`로 허용 Script와 숫자 여부를 다시 확인합니다. 따라서 Toto/Nagm/Kawi/Vith 같은 신규 Script와 신규 Arabic/Latin/Han 문자·숫자를 런타임 Unicode 버전 차이로 오탐하지 않습니다.
- 허용 범위를 벗어난 문자는 선택한 처리 수준에 따라 해당 locale 셀의 `경고` 또는 `에러`로 표시합니다.
- 기본 설정에서는 문자 체계 위반을 `경고 (저장 가능)`로 표시하며, 사용자가 확인한 뒤 계속 저장할 수 있습니다.
- 프로젝트 설정에서 `에러 (저장 차단)`로 변경하면 위반 문자가 남아 있는 동안 저장할 수 없습니다.
- `문자 체계 검사`를 끄면 이 검사를 수행하지 않습니다.
- CLDR에서 예상 Script를 구할 수 없거나 locale 태그가 malformed이면 검사를 건너뜁니다.

CLDR는 locale을 예상 Script에 매핑하는 용도로만 사용하며 value의 자연어를 판별하지 않습니다. 모든 고정·자동 생성 규칙은 라틴 문자(Latin)를 허용하므로, 베트남어·프랑스어처럼 라틴 기반인 문장은 `ko`/`ja` 값에서도 통과할 수 있습니다. 일본어와 중국어의 한자도 문자만으로는 구분할 수 없습니다.

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
10. 설정이 켜져 있으면 ko/en/ja/vi 고정 규칙 또는 CLDR에서 예상 Script를 구한 보완 규칙으로 문자열 value를 검사하고, 선택한 문자 위반 처리 수준으로 표시합니다.
11. 중복 key와 dot path 충돌은 error로 표시합니다.
12. 저장 시 메모리에서 JSON을 먼저 재생성하고 검증합니다.
13. error가 없으면 변경 요약과 누락 locale 파일 생성 여부를 확인한 뒤 저장합니다.

## LocaleGrid 팝업

LocaleGrid MVP에서 사용하는 팝업은 다음 흐름에 맞춰 동작합니다.

- `행 추가`: 새 dot path key 입력
- `키 이름 변경`: 선택한 key의 새 이름 입력 및 충돌 검증
- `행 삭제`: 선택한 key를 삭제 후보로 표시
- `저장`: 변경 요약 표시 및 저장
- 누락 locale 파일이 있고 입력된 값이 있으면 파일 생성 여부 확인
- 저장 차단 error가 있으면 error 메시지 표시
- 빈 value 및 기본 설정의 예상 문자 체계 warning은 표시하되 사용자가 계속 진행하면 저장 가능
- 예상 문자 체계 위반을 error로 설정한 경우 저장 차단

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

[O] 8-1. ko/en/ja/vi 고정 규칙과 CLDR likely-subtags fallback 기반 예상 문자 체계 검증 및 warning/error 설정 추가

[O] 9. 프로젝트 설정 화면 추가

[] 10. 저장 전 line diff 수준의 변경 요약 고도화

[] 11. readonly value 표시 개선

[x] 12. 예외키 보존 및 설정 UI 개선

[] 13. 단위 테스트 추가

[] 14. 샌드박스 프로젝트에서 수동 동작 검증

## 참고 문서

- 작성일별 보고서 목록은 [doc/문서_목록.md](doc/문서_목록.md)를 참고합니다.
- 상세 MVP 범위와 구현 기준은 [PLAN.md](PLAN.md)를 참고합니다.
