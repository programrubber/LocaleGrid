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

현재 구현 완료 및 보완된 부분:

- **구현 언어**: MVP 안정성 확보를 위해 기존 Java 구조를 유지하며 최적화 완료
- **저장 전 변경 요약**: `SaveResult`를 통해 추가, 수정, 삭제, 생성된 파일 수 등의 명확한 변경 요약을 상태 바와 정보 메시지로 제공
- **Key 편집 및 Rename 추적**: `LocaleGridRow`에 `added` 필드 및 `originalKey`를 도입하여 key 이름 변경 시 `편집` 상태로 관리하고 중복 검증 연동 완료
- **한글화 및 UI 개선**: 상태 배지를 한글(`추가`, `경고`, `편집`, `삭제`, `에러`)로 직관적으로 변경하고, 동일한 기준으로 필터 토글 버튼 연동
- **예외키 보존**: root-level `__section__` entry를 hidden marker로 보존하고, 새로 추가한 예외키 row는 상태 배지 없이 편집 가능하도록 구현
- **대화상자 레이아웃 개선**: `WideConfirmDialog`를 도입하여 에디터 종료 및 취소 시 예쁘고 넓은 경고창 제공
- **단위 테스트**: `FlattenedJsonTest`에 중복 key 검증 및 dot path 구조 분석에 대한 단위 테스트 추가 완료
- **설정 UI 고도화**: 사용자 친화성과 가독성을 극대화하기 위해 플러그인 설정(Settings) 화면을 전면 개편 완료
  - **기본/고급 설정 영역 분리**: 설정 항목들을 '기본 설정'과 '고급 설정' 영역으로 명확히 구획하고, 고급 설정은 펼치고 접을 수 있는 IntelliJ 스타일의 `HideableTitledPanel`로 묶어 복잡성 완화
  - **상세 힌트 설명 및 개행**: 각 입력 필드 아래에 폰트 크기와 색상을 조정한 보조 힌트 라벨을 배치하고, 긴 텍스트에는 HTML 형식의 개행(`<br>`)을 적용하여 폼 가독성 극대화
  - **플레이스홀더(Placeholder) 제공**: `locale 표시 순서` 필드에 `JBTextField`의 Empty Text API를 적용해 placeholder(`ko,en,jp,vi`)를 노출하고 힌트 텍스트에도 괄호 예시 추가
  - **명칭 표준화**: 기존 '구역 표시 key' 명칭을 **예외 키**로 용어를 일원화 및 설명 문구 교체
  - **JSON 들여쓰기 컴포넌트 교체**: 기존의 숫자 Spinner를 들여쓰기 2칸 혹은 4칸을 고를 수 있는 **JComboBox(SelectBox)** 형태의 콤보박스로 변경 및 연동 완료
- **기어 아이콘 버튼 외관 개선**: 다국어 에디터의 기어(설정) 버튼이 테두리와 마우스 리액션이 없어 비활성화된 것처럼 보였던 현상을 개선. 기존 `MoveActionButton` 디자인 사양을 계승한 `ToolbarIconButton`을 구현하고 설정 기어 버튼 및 위/아래 이동 버튼에 통합 적용하여 둥근 테두리와 호버/클릭 피드백을 제공함. 아울러 기어 아이콘 자체를 선명한 흰색(`java.awt.Color.WHITE`)으로 틴트 적용하여 다크 테마(Darcula)에서의 시인성을 높이고 흐려 보이는 비주얼을 완벽히 해결함.
- **상단 툴바 레이아웃 개편 및 예외키 버튼 재배치**: 다국어 에디터 상단 툴바를 3분할 구조로 전면 개편하였습니다. 필터 영역을 좌측(BorderLayout.WEST)에 정렬하고 라벨 앞의 파이프 기호(|)를 제거하였으며, 검색 영역을 정중앙(BorderLayout.CENTER)에 수평 배치하였습니다. 그리고 기존 하단 행에 있었던 "예외키 설정" 버튼명을 "예외키"로 간결화하고 상단 툴바 우측 끝(BorderLayout.EAST, 설정 기어 버튼 바로 왼쪽)으로 배치하여 미적 밸런스와 사용성을 고도로 충족시켰습니다.
- **검색창 UI 개선**: 기존의 텍스트 라벨 "검색"과 결합된 형태에서 라벨을 완전히 배제하고, 입력창 내부에 돋보기 검색 아이콘 및 입력 지우기(X) 버튼이 깔끔하게 내장된 `JBTextField`로 전환하였습니다. 또한 둥근 모서리(RoundRect) 속성을 활성화하여 부드러운 느낌을 부여하고, 가로 폭을 기존 320px에서 420px로 늘려 검색 영역을 더 확장하였으며, placeholder 텍스트를 "검색"으로 지정하여 직관적이고 세련된 미니멀 검색 영역을 완성했습니다.
- **드래그 핸들러 디자인 개선**: 행 순서 변경용 드래그 핸들(6도트 아이콘)을 입체적이고 현대적인 느낌으로 업그레이드하였습니다. 도트 크기를 4px로 조절하고, 개별 도트마다 3D 입체 엠보싱 효과(Drop shadow 그림자 및 Highlight 오버레이)를 적용하여 뛰어난 시각적 완성도를 구현했습니다. 또한 다크 테마 시인성을 위해 활성화 도트 색상의 명도를 높였습니다.
- **용어 통일 및 0.2.0 릴리즈**: 기존에 사용되던 모호한 용어인 '번역 묶음'을 직관적인 행동형 명칭인 **'일괄보기'**로 테이블 컬럼 헤더 및 상단 툴바 체크박스 모두 일괄 갱신했습니다. 또한 플러그인 버전을 `0.2.0`으로 공식 상향 조정한 뒤 빌드를 수행하여 배포용 릴리즈 zip 파일을 성공적으로 완성했습니다.

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

- MVP의 기본 화면은 `key + 일괄보기` 중심으로 표시한다.
- `일괄보기` 컬럼에는 한 셀 안에 `ko:`, `en:`, `jp:`, `vi:`처럼 locale별 값을 줄바꿈으로 요약 표시한다.
- 언어별 개별 컬럼도 제공하되, 테이블 위 체크박스로 `일괄보기` 컬럼과 각 언어 컬럼을 show/hide 할 수 있게 한다.
- 기본 표시 컬럼은 `key + 언어별 컬럼`이다.
- `일괄보기` 컬럼은 마지막 컬럼으로 배치하고 기본값은 숨김이다.
- 상태는 key 왼쪽의 별도 고정폭 `상태` 컬럼에 배지로 표시한다.
- 문제 없는 행은 상태 배지를 표시하지 않는다.
- 자동 탐지 locale 표시 순서는 `ko`, `en`, `jp`, `vi`를 우선하고, 나머지는 이름순으로 뒤에 붙인다.
- 그리드 셀에서는 긴 텍스트를 직접 편집하지 않고, 선택 행 상세 패널에서 locale별 값을 바로 수정한다.
- 이후 필요하면 표시할 locale을 선택하거나 접는 옵션을 추가한다.

즉, 기본 UI는 다음 구조를 권장한다.

```text
상단: toolbar, search, filter, column visibility checkboxes

중앙 그리드:
상태   key                  ko          en       jp          vi          일괄보기(hidden)
경고   login.title          로그인      Login    ログイン    Đăng nhập  ko: 로그인 ...
       login.button.submit  로그인하기  Sign in  ログイン하는 Đăng nhập  ko: 로그인하기 ...

하단 또는 우측 상세 패널:
ko  로그인하기
en  Sign in
jp  ログインする
vi  Đăng nhập
```

이 방식은 PLAN의 key union 그리드 구조를 유지하면서도, 다국어 장문을 가로 컬럼에 모두 욱여넣는 문제를 피한다.
또한 사용자는 필요에 따라 `일괄보기`만 보거나, 특정 언어별 컬럼만 켜서 비교할 수 있다.
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
- 예외키 row 여부와 hidden marker 위치를 row/table 레벨에 명확히 저장

완료 기준:

- 행 하나만 보고도 추가, 수정, rename, 삭제, warning, error 여부를 판단할 수 있다.

## 2단계: JSON 처리 로직 고도화

목표:

- 저장 시 원본 구조를 최대한 보존하고, dot path 충돌을 안정적으로 막는다.

작업:

- flatten 시 key 순서 보존 강화
- unflatten 시 leaf/object 충돌 검증 강화
- root-level 예외키는 dot path 변환 대상에서 제외하고 hidden marker로 보존
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

- 기본 그리드는 `상태`, `key`, 언어별 컬럼, `일괄보기`로 구성
- `일괄보기` 셀은 locale별 값을 줄바꿈 preview로 표시
- 테이블 위에 컬럼 show/hide 체크박스 제공
  - `일괄보기`
  - `ko`
  - `en`
  - `jp`
  - `vi`
- `일괄보기`은 마지막 컬럼이며 기본 hide
- 언어별 컬럼은 기본 show
- key 왼쪽의 별도 `상태` 컬럼에 상태 배지 표시
  - 배지는 고정폭 텍스트로 표시
  - 예: `추가`, `경고`, `편집`, `삭제`, `에러`
  - 정상 행은 배지를 표시하지 않음
- locale 표시/숨김 또는 preview locale 수 제한 옵션 추가
- 기준 locale과 대상 locale 선택 UI는 상세 패널 편집 보조 기능으로 추가
- 선택 행 상세 패널 추가
  - 모든 locale value를 세로로 표시
  - 문자열 value는 편집 가능
  - readonly value는 타입과 원본값 표시
- key 컬럼 고정 또는 넓은 기본 폭 적용
- 긴 value는 `일괄보기`에서 줄 단위 축약, 상세 패널에서 전체 편집
- 색상 규칙 정리
  - error row
  - warning row
  - deleted row
  - exception key row
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
  - 예외키 입력 시 visible 예외키 row로 처리
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

## 7단계: 프로젝트 설정 완성 및 UI 개선

목표:

- 프로젝트별 locale grid 설정을 안정적으로 저장하고, 사용자 친화적이고 직관적인 설정 UI로 고도화한다.

작업:

- **설정 화면 레이아웃 고도화**:
  - 설정 화면을 '기본 설정'과 '고급 설정' 영역으로 명확히 분리하여 그룹화.
  - '기본 설정': locales root 경로 설정, locale 표시 순서 설정과 각 필드 하단의 상세 안내(힌트) 텍스트 추가.
  - '고급 설정': 접기/펼치기가 가능한 IntelliJ 스타일 접이식 패널(HideableTitledPanel)로 구현.
- **예외 키 설정 개선**:
  - 기존 '구역 표시 key'의 라벨명을 '예외 키'로 변경하고, 설명 문구를 "번역 key가 아니라 예외 키로 보존할 key입니다. 쉼표로 구분합니다."로 수정.
- **JSON 들여쓰기 설정 개선**:
  - 기존의 숫자 Spinner 대신, 2와 4 중 선택할 수 있는 콤보박스(SelectBox) 형태의 JComboBox UI 컴포넌트로 변경.
  - 하단 설명 문구: "적용 시 저장되는 JSON 들여쓰기 칸 수입니다. 기본값은 2입니다."
- 설정 변경 후 열린 에디터 refresh 안내

완료 기준:

- 설정 화면에서 2와 4 중 들여쓰기를 선택할 수 있고, 예외 키의 설명과 라벨이 업데이트되며, 변경 사항이 프로젝트 XML 설정에 정확하게 로드/세이브 및 반영된다.


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
- 예외키 판별
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
5. 그리드 UI를 `상태 배지 컬럼 + key + 언어별 컬럼 + 일괄보기 마지막 컬럼 기본 hide + 상세 패널` 구조로 개선
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

