# PyCharm 다국어 플러그인 MVP 요구사항

[[PyCharm_Plugin_Development]]의 첫 번째 산출물은 완성형 플러그인이 아니라, 기존 프로젝트의 JSON 다국어 리소스를 PyCharm 안에서 표 형태로 확인하고 수정할 수 있는 최소 기능이다. [[다국어_툴]]과 [[Morpheus_IDE]]는 그리드 편집기와 저장 시 프로젝트 반영 흐름의 참고 모델이다.

## 목표

- `locales/{locale}/{category}.json` 구조의 다국어 JSON을 한 화면에서 비교하고 편집한다.
- Markdown Preview처럼 JSON 파일을 열었을 때 다국어 그리드 보기 버튼 또는 탭을 제공한다.
- 개발자가 JSON 원본을 직접 오가며 수정하는 비용을 줄인다.
- 추가, 수정, rename, 삭제, 검증, 저장까지 IDE 안에서 처리한다.

## 확정한 MVP 결정

- 구현 언어는 Kotlin으로 한다.
- UI는 Tool Window가 아니라 Custom Editor 방식으로 시작한다.
- 첫 번째 지원 포맷은 JSON이다.
- 기본 리소스 구조는 `locales/{locale}/{category}.json`이다.
- category는 현재 열린 JSON 파일명을 기준으로 결정한다.
- locale 목록은 기본 자동 탐지이며, 프로젝트 설정에서 수동 지정할 수 있다.
- 중첩 JSON은 트리 UI가 아니라 dot path 방식의 플랫 그리드로 표시한다.
- 그리드 행은 모든 locale 파일의 key union으로 만든다.
- 정렬 기능은 MVP에서 제외하고 원본 key 순서를 유지한다.
- 검색은 텍스트 입력, 상태 필터는 체크박스로 제공한다.
- key 추가, rename, value 편집, 삭제를 모두 지원한다.
- key rename은 셀 직접 편집이 아니라 Rename 팝업으로만 수행한다.
- 구역 표시용 key는 사용자가 패턴을 설정할 수 있고, MVP에서는 readonly로 표시/보존한다.
- 빈 value는 허용하고, key 셀 배경 표시와 저장 시 warning으로만 알린다.
- 중복 번역 key와 dot path 충돌은 error로 취급하고 저장을 차단한다.
- 저장 전 line diff 대신 변경 요약 팝업을 제공한다.
- 플러그인 자체 rollback/backup은 MVP에서 제외하고 SVN 복구를 전제로 한다.

## 제외 범위

- 자동 번역 API 연동
- Excel Export/Import
- 빌드 스크립트 자동 생성 및 Injection
- 여러 리소스 포맷 동시 지원
- 복잡한 diff/merge UI
- UI 자동화 테스트
- 팀 서버나 원격 동기화
- 전체 JetBrains IDE 호환성 보장

## 리소스 구조

기본 경로 패턴:

```text
locales/{locale}/{category}.json
```

예시:

```text
locales/ko/login.json
locales/en/login.json
locales/jp/login.json
locales/vi/login.json
```

사용자가 `locales/ko/login.json`을 열면 category는 `login`으로 해석한다. 플러그인은 같은 category의 locale별 파일을 함께 로드한다.

## locale 탐지

기본값은 자동 탐지다. 플러그인은 `locales/` 바로 아래의 디렉터리명을 locale 후보로 본다.

```text
locales/
- ko/
- en/
- jp/
- vi/
```

사용자가 프로젝트 설정에서 locale 목록을 직접 지정하면 수동 설정을 우선한다. 수동 설정은 표시 여부와 컬럼 순서를 제어한다.

## key 병합 규칙

그리드의 행은 현재 category에 존재하는 모든 locale 파일의 key union으로 만든다.

예를 들어:

```text
ko/login.json: login
jp/login.json: alertLogin
en/login.json: 없음
vi/login.json: 없음
```

그리드는 다음처럼 표시한다.

```text
key         ko       en   jp          vi
login       로그인         빈 값       빈 값
alertLogin  빈 값          アラート     빈 값
```

특정 locale에 key가 없으면 해당 셀은 빈 value로 본다. 빈 value는 warning이며 저장을 막지 않는다.

행 순서는 기준 파일의 원본 key 순서를 먼저 따른다. 기준 파일에 없는 key는 다른 locale 파일에서 발견된 순서대로 뒤에 붙인다. 기준 파일은 사용자가 현재 연 파일이다.

## dot path 처리

중첩 JSON은 그리드 표시 시 dot path로 펼친다.

```json
{
  "login": {
    "title": "로그인",
    "button": "로그인하기"
  }
}
```

그리드 표시:

```text
key           ko
login.title   로그인
login.button  로그인하기
```

저장할 때는 dot path를 다시 중첩 JSON 구조로 복원한다. 기존 key 순서는 최대한 보존하고, 새 key는 사용자가 추가한 위치에 반영한다. formatting은 2-space indent 기준의 표준 JSON으로 다시 쓴다.

dot path 문법은 구조만 엄격히 검증하고 segment 문자 종류는 넓게 허용한다.

허용 예시:

```text
login.title
login.button.submit
menu.header.name
login-page.title
login_title.text
error.404.title
```

금지 예시:

```text
빈 문자열
.login
login.
login..title
공백만 있는 segment
```

새 key 추가 또는 rename 시 기존 dot path 구조와 충돌하면 error로 처리한다. 기존 문자열 leaf를 object로 자동 변환하거나, 기존 object를 문자열 leaf로 덮어쓰지 않는다.

```text
기존 key: login
추가 key: login.title
결과: error
```

```text
기존 key: login.title
추가 key: login
결과: error
```

## value 타입

MVP에서 셀 편집을 지원하는 value 타입은 문자열로 제한한다.

- string: 편집 가능
- number: readonly 또는 unsupported 표시
- boolean: readonly 또는 unsupported 표시
- array: readonly 또는 unsupported 표시
- object leaf: readonly 또는 unsupported 표시

중첩 object는 dot path로 펼치되, 최종 leaf value가 문자열일 때만 편집 가능하다.

## 구역 표시용 key

기존 JSON에 `__comment__` 또는 `__commant__`처럼 구역을 나누기 위한 특수 key가 있을 수 있다. 이 key들은 일반 번역 key와 구분해서 다룬다.

- 구역 표시용 key 패턴은 사용자가 프로젝트 설정에서 지정한다.
- 기본값은 `__comment__`, `__commant__` 같은 명시적 key 목록으로 시작한다.
- 그리드에서는 구역 표시 행처럼 보이게 한다.
- MVP에서는 readonly로 표시한다.
- Add Row로 새 구역 표시용 key를 추가하지 않는다.
- 빈 value warning 대상에서 제외한다.
- dot path 변환 대상이 아니라 원래 key 형태를 유지한다.
- 저장 시 기존 위치와 값을 최대한 보존한다.

## 그리드 UI

컬럼:

- 첫 번째 열: dot path key
- 이후 열: locale별 value

기능:

- 문자열 value 셀 편집
- Add Row
- Rename Key
- Delete Row
- Save
- Refresh
- Validate
- Search input
- 상태 필터 체크박스

상태 표시:

- 빈 value가 있는 행은 key 셀 배경을 변경한다.
- 중복 key나 dot path 충돌은 error 색으로 표시한다.
- 누락 locale 파일은 컬럼 헤더 또는 상태 표시로 구분한다.
- 구역 표시용 key는 일반 번역 key와 다른 행 스타일로 표시한다.

## 검색/필터

기본 입력은 검색 전용으로 사용한다. key 또는 locale별 value에 검색어가 포함된 행만 표시한다.

상태 필터는 체크박스로 제공한다.

- 빈 value 있는 행만 보기
- 수정된 행만 보기
- 삭제 후보 행만 보기
- error/warning 행만 보기

MVP에서는 정렬 기능을 제공하지 않는다. 행 순서는 원본 JSON의 흐름을 따른다.

## key 편집

MVP는 key 추가, rename, value 편집, 삭제를 지원한다.

추가:

- Add Row 팝업에서는 새 dot path key만 입력받는다.
- locale별 value는 행 추가 후 그리드에서 입력한다.
- 선택한 행이 있으면 선택 행 아래에 추가한다.
- 선택한 행이 없으면 파일 끝에 추가한다.
- Add Row는 일반 번역 key만 대상으로 한다.

Rename:

- key 셀 직접 편집은 허용하지 않는다.
- Rename Key 액션과 팝업을 통해서만 수행한다.
- 팝업에는 기존 key, 새 key 입력칸, 영향받는 locale 파일 목록, 충돌 검증 결과를 보여준다.
- 새 key가 비어 있거나 이미 존재하면 불가하다.
- 새 key가 기존 dot path 구조와 충돌하면 불가하다.
- 새 key가 구역 표시용 key 패턴과 충돌하면 불가하다.
- 일부 locale 파일에 기존 key가 없으면 해당 파일은 무시한다.

삭제:

- Delete Row는 선택한 key를 삭제 후보로 표시한다.
- Save 시 삭제 대상 key 목록을 보여주고 확인받는다.
- key가 존재하는 locale 파일에서만 삭제한다.
- key가 없는 locale 파일은 오류로 보지 않고 무시한다.

## 빈 value 처리

빈 value는 허용한다. 개발 중에는 일부 locale 번역이 아직 준비되지 않을 수 있으므로, 빈 값이 있다는 이유만으로 저장을 막지 않는다.

- 빈 value가 하나라도 있는 행은 key 셀 배경을 변경해 표시한다.
- Validate 실행 시 빈 value가 있는 key를 warning으로 보여준다.
- Save 실행 시 빈 value warning을 보여주되, 사용자가 계속 진행하면 저장한다.
- 빈 value는 error가 아니라 warning으로 취급한다.

## 중복 key 처리

일반 번역 key의 중복은 error로 취급하고 저장을 막는다.

- 중복 번역 key가 있으면 key 셀 배경을 오류 색으로 표시한다.
- Validate 실행 시 중복 key를 error로 보여준다.
- Save 실행 시 중복 key가 남아 있으면 저장하지 않는다.
- 구역 표시용 key는 사용자가 설정한 규칙에 따라 일반 번역 key 검증에서 제외하거나 별도 처리한다.

## 누락 locale 파일 처리

locale 목록에는 포함되어 있지만 현재 category 파일이 없는 경우에도 그리드 컬럼은 표시한다.

```text
locales/ko/login.json
locales/en/login.json
locales/jp/login.json
locales/vi/login.json  없음
```

이 경우 `vi` 컬럼은 missing file 상태로 표시한다. 사용자가 값을 입력하고 저장하면, 플러그인은 `locales/vi/login.json` 파일을 생성할지 확인한다. 사용자가 승인한 경우에만 새 JSON 파일을 생성한다.

## 저장

저장 정책:

- 저장 전 Validate를 수행한다.
- 저장 전 변경 요약 팝업을 표시한다.
- 중복 key, dot path 충돌, JSON 직렬화 실패가 있으면 저장하지 않는다.
- 빈 value warning과 누락 locale 파일 생성은 사용자 확인 후 저장할 수 있다.
- 기존 key 순서는 최대한 보존한다.
- formatting은 2-space indent 기준의 표준 JSON으로 다시 쓴다.
- 플러그인 자체 rollback/backup은 제공하지 않는다.
- 파일 복구는 SVN diff/revert에 맡긴다.

변경 요약 항목:

- 추가 key 수
- 수정 key 수
- rename key 수
- 삭제 key 수
- 생성될 locale 파일 목록
- 빈 value warning 수
- 저장 차단 error 여부

쓰기 전 최소 검증:

- 모든 대상 JSON을 메모리에서 먼저 생성한다.
- JSON 직렬화가 모두 성공하는지 확인한다.
- 중복 key와 dot path 충돌 error가 없는지 확인한다.

## 프로젝트 설정

MVP의 설정은 프로젝트 단위로 저장한다.

초기 설정 항목:

- locales root 경로: 기본값 `locales`
- locale 목록/순서: 기본값 자동 탐지
- 구역 표시용 key 목록 또는 패턴
- JSON formatting: 기본값 2-space indent

## 데이터 모델 초안

```text
TranslationEntry
- key: string  // dot path
- category: string
- values: Map<LocaleCode, JsonValue>
- sourcePaths: Map<LocaleCode, string>
- status: normal | missing | modified | conflict
```

```text
TranslationTable
- locales: List<LocaleCode>
- localeSource: auto | manual
- category: string
- openedPath: string
- entries: List<TranslationEntry>
- format: json
```

## 테스트 범위

MVP에서는 핵심 로직 단위 테스트를 작성하고, UI 자동화 테스트는 제외한다. Custom Editor와 그리드 동작은 샌드박스 프로젝트에서 수동 체크리스트로 검증한다.

단위 테스트 대상:

- JSON flatten/unflatten
- locale별 key union 병합
- dot path 문법 검증
- dot path 충돌 검증
- locale 자동 탐지
- category 추론
- 구역 표시용 key 판별
- add/rename/delete 반영
- 저장용 JSON 생성

수동 테스트 대상:

- JSON 파일을 열었을 때 Custom Editor Preview가 표시되는지
- 같은 category의 locale 파일들이 그리드로 묶이는지
- locale별로 서로 다른 key가 union으로 표시되는지
- 빈 value 행의 key 셀 배경이 바뀌는지
- Add Row, Rename Key, Delete Row가 의도대로 동작하는지
- Save 전 변경 요약 팝업이 표시되는지
- 누락 locale 파일 생성 확인이 동작하는지
