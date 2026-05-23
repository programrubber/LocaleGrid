# LocaleGrid 실제 구현 계획

## 목표

`PLAN.md`의 MVP를 실제 동작 가능한 PyCharm 플러그인으로 완성한다.

핵심 목표는 `locales/{locale}/{category}.json` 구조의 JSON 파일을 열었을 때, `JSON` 원본 탭 옆 `다국어 에디터` 탭에서 다국어 값을 비교, 수정, 검증, 저장할 수 있게 만드는 것이다.

## 현재 상태

이미 구성된 기반:

- 기존 `secui-version-checker` 코드는 제거됨
- 플러그인 ID와 이름은 `LocaleGrid`로 변경됨
- `FileEditorProvider` 기반 커스텀 에디터 구조 추가됨
- `locales/{locale}/{category}.json` 경로 인식 로직 추가됨
- dot path flatten/unflatten 기본 로직 추가됨
- locale별 key union 병합 로직 추가됨
- Swing 기반 그리드 UI 초안 추가됨
- 프로젝트 설정 UI 초안 추가됨

현재 보완이 필요한 부분:

- PLAN의 확정 사항은 Kotlin이지만 현재 구현은 Java임
- 저장 전 변경 요약이 아직 line diff 수준이 아님
- rename 추적이 명확하지 않음
- readonly/unsupported value 표시가 단순함
- 구역 표시용 key UI가 아직 충분히 구분되지 않음
- 단위 테스트가 없음
- 샌드박스 프로젝트 검증 데이터가 없음

## 결정 필요 사항

### 1. 구현 언어

PLAN에는 Kotlin으로 구현한다고 되어 있지만 현재 코드는 Java로 시작되어 있다.

권장 결정:

- MVP 안정화까지는 현재 Java 구조를 유지한다.
- 기능이 고정된 뒤 필요하면 Kotlin으로 점진 이전한다.

이유:

- 이미 에디터, 모델, 로드/저장 구조가 Java로 만들어져 있다.
- 지금 Kotlin 전환을 먼저 하면 기능 검증보다 구조 변경 비용이 커진다.
- JetBrains Platform API는 Java/Kotlin 모두 사용 가능하다.

### 2. 다국어 표시 방식

한국어, 영어, 일본어, 베트남어를 모두 한 줄에 컬럼으로 표시하면 다음 문제가 생긴다.

- 한 행이 지나치게 가로로 길어진다.
- 언어별 문장 길이가 달라 셀 높이와 가독성이 흔들린다.
- CJK, 라틴, 베트남어 악센트가 섞이면 한 줄 비교가 피로해진다.

권장 결정:

- MVP의 기본 화면은 `key + 번역 묶음` 중심으로 표시한다.
- `번역 묶음` 컬럼에는 한 셀 안에 `ko:`, `en:`, `jp:`, `vi:`처럼 locale별 값을 줄바꿈으로 요약 표시한다.
- 언어별 개별 컬럼도 제공하되, 테이블 위 체크박스로 `번역 묶음` 컬럼과 각 언어 컬럼을 show/hide 할 수 있게 한다.
- 기본 표시 컬럼은 `key + 언어별 컬럼`이다.
- `번역 묶음` 컬럼은 마지막 컬럼으로 배치하고 기본값은 숨김이다.
- 상태는 key 왼쪽의 별도 고정폭 `상태` 컬럼에 배지로 표시한다.
- 문제 없는 행은 상태 배지를 표시하지 않는다.
- 자동 탐지 locale 표시 순서는 `ko`, `en`, `jp`, `vi`를 우선하고, 나머지는 이름순으로 뒤에 붙인다.
- 그리드 셀에서는 긴 텍스트를 직접 편집하지 않고, 선택 행 상세 패널에서 locale별 값을 바로 수정한다.
- 이후 필요하면 표시할 locale을 선택하거나 접는 옵션을 추가한다.

즉, 기본 UI는 다음 구조를 권장한다.

```text
상단: toolbar, search, filter, column visibility checkboxes

중앙 그리드:
상태   key                  ko          en       jp          vi          번역 묶음(hidden)
WARN   login.title          로그인      Login    ログイン    Đăng nhập  ko: 로그인 ...
       login.button.submit  로그인하기  Sign in  ログインする Đăng nhập  ko: 로그인하기 ...

하단 또는 우측 상세 패널:
ko  로그인하기
en  Sign in
jp  ログインする
vi  Đăng nhập
```

이 방식은 PLAN의 key union 그리드 구조를 유지하면서도, 다국어 장문을 가로 컬럼에 모두 욱여넣는 문제를 피한다.
또한 사용자는 필요에 따라 `번역 묶음`만 보거나, 특정 언어별 컬럼만 켜서 비교할 수 있다.
실제 편집은 상세 패널에서 안정적으로 수행한다.

## 구현 단계

## 1단계: 모델 정리

목표:

- UI 동작보다 먼저 저장과 검증에 필요한 내부 모델을 확정한다.

작업:

- `TranslationTable`에 기준 파일, category, locale 순서, row 순서 정보를 명확히 저장
- `LocaleGridRow`에 상태값 추가
  - `normal`
  - `missing`
  - `modified`
  - `deleted`
  - `warning`
  - `error`
- rename 추적 모델 추가
  - `originalKey`
  - `currentKey`
  - `renamed`
- locale별 파일 존재 여부 모델 추가
- 구역 표시용 key 여부를 row 레벨에 명확히 저장

완료 기준:

- 행 하나만 보고도 추가, 수정, rename, 삭제, warning, error 여부를 판단할 수 있다.

## 2단계: JSON 처리 로직 고도화

목표:

- 저장 시 원본 구조를 최대한 보존하고, dot path 충돌을 안정적으로 막는다.

작업:

- flatten 시 key 순서 보존 강화
- unflatten 시 leaf/object 충돌 검증 강화
- comment key는 dot path 변환 대상에서 제외
- unsupported value 타입 표시용 metadata 추가
- 저장 전 모든 locale JSON을 메모리에서 먼저 생성
- JSON 직렬화 실패 시 실제 파일 쓰기 차단

완료 기준:

- 저장 전에 모든 대상 JSON 문자열이 생성되고 검증된다.
- error가 있으면 어떤 파일도 쓰지 않는다.

## 3단계: 검증 체계 정리

목표:

- 저장 차단 error와 허용 warning을 명확히 분리한다.

error:

- 빈 key
- 잘못된 dot path
- 중복 일반 번역 key
- leaf/object dot path 충돌
- JSON 파싱 실패
- JSON 직렬화 실패

warning:

- 빈 value
- 누락 locale 파일
- unsupported readonly value
- 일부 locale에만 존재하는 key

작업:

- `Diagnostic`에 `locale`, `file`, `rowKey`, `column` 정보 추가
- Validate 버튼 결과를 상태 영역과 팝업에서 모두 확인 가능하게 정리
- Save 실행 시 error가 있으면 저장 차단
- warning만 있으면 사용자 확인 후 저장 가능

완료 기준:

- 사용자가 왜 저장이 막혔는지 row와 locale 단위로 확인할 수 있다.

## 4단계: 그리드 UI 개선

목표:

- 다국어가 섞여도 읽기 쉬운 편집 화면을 만든다.

작업:

- 기본 그리드는 `상태`, `key`, 언어별 컬럼, `번역 묶음`으로 구성
- `번역 묶음` 셀은 locale별 값을 줄바꿈 preview로 표시
- 테이블 위에 컬럼 show/hide 체크박스 제공
  - `번역 묶음`
  - `ko`
  - `en`
  - `jp`
  - `vi`
- `번역 묶음`은 마지막 컬럼이며 기본 hide
- 언어별 컬럼은 기본 show
- key 왼쪽의 별도 `상태` 컬럼에 상태 배지 표시
  - 배지는 고정폭 텍스트로 표시
  - 예: `WARN`, `ERR`, `MOD`, `DEL`, `READ`
  - 정상 행은 배지를 표시하지 않음
- locale 표시/숨김 또는 preview locale 수 제한 옵션 추가
- 기준 locale과 대상 locale 선택 UI는 상세 패널 편집 보조 기능으로 추가
- 선택 행 상세 패널 추가
  - 모든 locale value를 세로로 표시
  - 문자열 value는 편집 가능
  - readonly value는 타입과 원본값 표시
- key 컬럼 고정 또는 넓은 기본 폭 적용
- 긴 value는 `번역 묶음`에서 줄 단위 축약, 상세 패널에서 전체 편집
- 색상 규칙 정리
  - error row
  - warning row
  - deleted row
  - comment row
  - modified cell

완료 기준:

- ko/en/jp/vi가 모두 있어도 사용자가 필요한 컬럼만 켜서 볼 수 있다.
- 선택 행에서 모든 locale 값을 한 번에 확인하고 수정할 수 있다.

## 5단계: key 편집 기능 완성

목표:

- Add, Rename, Delete 흐름을 PLAN에 맞게 완성한다.

작업:

- Add Row 팝업
  - 새 dot path key 입력
  - 선택 행 아래 삽입
  - 선택 행이 없으면 끝에 삽입
  - 구역 표시용 key 추가 차단
- Rename Key 팝업
  - 기존 key 표시
  - 새 key 입력
  - 영향받는 locale 파일 목록 표시
  - 충돌 검증 결과 표시
  - rename 추적 저장
- Delete Row
  - 즉시 삭제가 아니라 삭제 후보 표시
  - Save 전 삭제 대상 목록 표시
- Undo 수준은 MVP에서 제외

완료 기준:

- Save 전 변경 요약에서 추가, 수정, rename, 삭제 수가 구분된다.

## 6단계: 저장 흐름 완성

목표:

- 저장 전 검증, 요약, 확인, 파일 쓰기 순서를 안정화한다.

저장 순서:

1. 현재 셀 편집 종료
2. Validate 실행
3. error가 있으면 저장 차단
4. warning 목록 생성
5. 변경 요약 생성
6. 누락 locale 파일 생성 여부 확인
7. 모든 JSON을 메모리에서 생성
8. 대상 파일에 쓰기
9. VFS refresh
10. 테이블 reload

변경 요약 항목:

- 추가 key 수
- 수정 key 수
- rename key 수
- 삭제 key 수
- 생성될 locale 파일 목록
- 저장될 locale 파일 목록
- warning 수
- 저장 차단 error 여부

완료 기준:

- 저장 전에 사용자에게 실제 변경 범위가 명확히 보인다.

## 7단계: 프로젝트 설정 완성

목표:

- 프로젝트별 locale grid 설정을 안정적으로 저장한다.

작업:

- locales root 설정
- manual locales 설정
- comment key 목록 설정
- JSON indent 설정
- 설정 변경 후 열린 에디터 refresh 안내

완료 기준:

- 수동 locale 순서가 그리드 컬럼 순서와 상세 패널 순서에 반영된다.

## 8단계: 테스트 추가

목표:

- 핵심 로직은 UI 없이 단위 테스트로 검증한다.

단위 테스트:

- JSON flatten
- JSON unflatten
- dot path 문법 검증
- dot path 충돌 검증
- key union 병합
- 기준 파일 key 순서 보존
- locale 자동 탐지
- category 추론
- comment key 판별
- add/rename/delete 반영
- 저장용 JSON 생성

수동 테스트:

- `locales/ko/login.json` 열기
- `JSON` / `다국어 에디터` 탭 표시 확인
- ko/en/jp/vi 파일 병합 확인
- 긴 다국어 value 표시 확인
- 상세 패널 편집 확인
- Add Row 확인
- Rename Key 확인
- Delete Row 확인
- Save 요약 확인
- 누락 locale 파일 생성 확인

완료 기준:

- `.\gradlew.bat build`가 통과한다.
- `runIde` 샌드박스에서 수동 체크리스트를 통과한다.

## 우선순위

1. 모델과 저장 안정성
2. 검증 정확도
3. 다국어 표시 UI 개선
4. key 편집 UX 완성
5. 테스트 추가
6. README와 PLAN 반영

## 권장 구현 순서

1. Java 유지 결정 후 현재 코드 안정화
2. `TranslationTable`, `LocaleGridRow`, `LocaleValue` 모델 보강
3. `Diagnostic` 구조 확장
4. 저장 전 메모리 검증 강화
5. 그리드 UI를 `상태 배지 컬럼 + key + 언어별 컬럼 + 번역 묶음 마지막 컬럼 기본 hide + 상세 패널` 구조로 개선
6. Add/Rename/Delete 저장 반영 완성
7. 단위 테스트 추가
8. 샌드박스 프로젝트 수동 검증

## 최종 MVP 완료 기준

- JSON 파일을 열면 `JSON` / `다국어 에디터` 탭이 표시된다.
- 같은 category의 locale JSON 파일들이 하나의 테이블로 병합된다.
- 다국어 value를 한 화면에서 비교하고 수정할 수 있다.
- 긴 다국어 문장이 한 줄에 과밀하게 표시되지 않는다.
- Add, Rename, Delete, Save, Refresh, Validate가 동작한다.
- 빈 value는 warning으로 표시되고 저장은 가능하다.
- 중복 key와 dot path 충돌은 error로 표시되고 저장이 차단된다.
- 누락 locale 파일은 저장 시 사용자 확인 후 생성된다.
- 저장 전 변경 요약이 표시된다.
- 핵심 JSON 처리 로직 단위 테스트가 존재한다.
